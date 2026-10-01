// made by haze
package it.haze.hazecrates.database;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Cache in RAM per chiavi virtuali / aperture / milestone.
 * Load al join, mutazioni sync in memoria, flush async a intervalli.
 */
public final class PlayerDataStore {

    private final HazeCrates plugin;
    private final DatabaseService db;

    private final ConcurrentMap<String, Integer> keys = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> opened = new ConcurrentHashMap<>();
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyOpened = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyClaims = ConcurrentHashMap.newKeySet();

    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();

    private BukkitTask flushTask;

    public PlayerDataStore(HazeCrates plugin, DatabaseService db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        long periodSec = Math.max(2L, plugin.getConfig().getLong("database.flush-seconds", 5L));
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::flushDirty, periodSec * 20L, periodSec * 20L);
        for (var player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushDirty();
    }

    public CompletableFuture<Void> load(UUID uuid) {
        if (loaded.contains(uuid)) {
            return CompletableFuture.completedFuture(null);
        }
        return loading.computeIfAbsent(uuid, id -> {
            if (!db.isAvailable()) {
                loaded.add(id);
                loading.remove(id);
                return CompletableFuture.completedFuture(null);
            }
            return db.query(conn -> {
                try {
                    loadKeys(conn, id);
                    loadOpened(conn, id);
                    loadClaims(conn, id);
                    loaded.add(id);
                    return (Void) null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, null).whenComplete((v, err) -> {
                loading.remove(id);
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING,
                            "[HazeCrates] Load dati player fallito: " + id, err);
                    loaded.add(id);
                }
            });
        });
    }

    public void unload(UUID uuid) {
        loaded.remove(uuid);
        loading.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                flushPlayer(uuid);
            } finally {
                String prefix = uuid + ":";
                keys.keySet().removeIf(k -> k.startsWith(prefix));
                opened.keySet().removeIf(k -> k.startsWith(prefix));
                claimed.removeIf(k -> k.startsWith(prefix));
                dirtyKeys.removeIf(k -> k.startsWith(prefix));
                dirtyOpened.removeIf(k -> k.startsWith(prefix));
                dirtyClaims.removeIf(k -> k.startsWith(prefix));
            }
        });
    }

    public boolean isLoaded(UUID uuid) {
        return loaded.contains(uuid);
    }

    public int getKeys(UUID uuid, String crate) {
        return keys.getOrDefault(mk(uuid, crate), 0);
    }

    public boolean consumeKey(UUID uuid, String crate) {
        String k = mk(uuid, crate);
        int[] ok = {0};
        keys.compute(k, (key, v) -> {
            int cur = v == null ? 0 : v;
            if (cur <= 0) return cur;
            ok[0] = 1;
            dirtyKeys.add(k);
            return cur - 1;
        });
        return ok[0] == 1;
    }

    public void setKeys(UUID uuid, String crate, int amount) {
        String k = mk(uuid, crate);
        keys.put(k, Math.max(0, amount));
        dirtyKeys.add(k);
    }

    public boolean takeKeys(UUID uuid, String crate, int amount) {
        if (amount <= 0) return true;
        String k = mk(uuid, crate);
        int[] ok = {0};
        keys.compute(k, (key, v) -> {
            int cur = v == null ? 0 : v;
            if (cur < amount) return cur;
            ok[0] = 1;
            dirtyKeys.add(k);
            return cur - amount;
        });
        return ok[0] == 1;
    }

    public void addKeys(UUID uuid, String crate, int amount) {
        String k = mk(uuid, crate);
        keys.merge(k, amount, Integer::sum);
        dirtyKeys.add(k);
    }

    public Map<String, Integer> allKeys(UUID uuid) {
        String prefix = uuid + ":";
        Map<String, Integer> out = new HashMap<>();
        keys.forEach((k, v) -> {
            if (k.startsWith(prefix) && v > 0) {
                out.put(k.substring(prefix.length()), v);
            }
        });
        return out;
    }

    public int getOpened(UUID uuid, String crate) {
        return opened.getOrDefault(mk(uuid, crate), 0);
    }

    public int incrementOpened(UUID uuid, String crate) {
        String k = mk(uuid, crate);
        int total = opened.merge(k, 1, Integer::sum);
        dirtyOpened.add(k);
        return total;
    }

    public boolean tryClaim(UUID uuid, String crate, String milestone) {
        String k = uuid + ":" + crate + ":" + milestone;
        if (!claimed.add(k)) return false;
        dirtyClaims.add(k);
        return true;
    }

    public boolean isClaimed(UUID uuid, String crate, String milestone) {
        return claimed.contains(uuid + ":" + crate + ":" + milestone);
    }

    private void loadKeys(Connection conn, UUID uuid) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(
                "SELECT crate_id, amount FROM fc_virtual_keys WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            ResultSet r = s.executeQuery();
            while (r.next()) {
                keys.put(mk(uuid, r.getString(1)), r.getInt(2));
            }
        }
    }

    private void loadOpened(Connection conn, UUID uuid) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(
                "SELECT crate_id, total_opened FROM fc_player_stats WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            ResultSet r = s.executeQuery();
            while (r.next()) {
                opened.put(mk(uuid, r.getString(1)), r.getInt(2));
            }
        }
    }

    private void loadClaims(Connection conn, UUID uuid) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(
                "SELECT crate_id, milestone_id FROM fc_reward_progress WHERE uuid=? AND claimed=1")) {
            s.setString(1, uuid.toString());
            ResultSet r = s.executeQuery();
            while (r.next()) {
                claimed.add(uuid + ":" + r.getString(1) + ":" + r.getString(2));
            }
        }
    }

    private void flushPlayer(UUID uuid) {
        if (!db.isAvailable()) return;
        String prefix = uuid + ":";
        Set<String> keyFlush = new HashSet<>();
        Set<String> openFlush = new HashSet<>();
        Set<String> claimFlush = new HashSet<>();
        for (String k : dirtyKeys) if (k.startsWith(prefix)) keyFlush.add(k);
        for (String k : dirtyOpened) if (k.startsWith(prefix)) openFlush.add(k);
        for (String k : dirtyClaims) if (k.startsWith(prefix)) claimFlush.add(k);
        if (keyFlush.isEmpty() && openFlush.isEmpty() && claimFlush.isEmpty()) return;
        dirtyKeys.removeAll(keyFlush);
        dirtyOpened.removeAll(openFlush);
        dirtyClaims.removeAll(claimFlush);
        try {
            db.query(conn -> {
                try {
                    writeKeys(conn, keyFlush);
                    writeOpened(conn, openFlush);
                    writeClaims(conn, claimFlush);
                    return (Void) null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, null).join();
        } catch (Exception e) {
            dirtyKeys.addAll(keyFlush);
            dirtyOpened.addAll(openFlush);
            dirtyClaims.addAll(claimFlush);
            plugin.getLogger().log(Level.WARNING, "[HazeCrates] Flush player fallito", e);
        }
    }

    private void flushDirty() {
        if (!db.isAvailable()) return;
        Set<String> keyFlush = Set.copyOf(dirtyKeys);
        Set<String> openFlush = Set.copyOf(dirtyOpened);
        Set<String> claimFlush = Set.copyOf(dirtyClaims);
        if (keyFlush.isEmpty() && openFlush.isEmpty() && claimFlush.isEmpty()) return;
        dirtyKeys.removeAll(keyFlush);
        dirtyOpened.removeAll(openFlush);
        dirtyClaims.removeAll(claimFlush);
        try {
            db.query(conn -> {
                try {
                    writeKeys(conn, keyFlush);
                    writeOpened(conn, openFlush);
                    writeClaims(conn, claimFlush);
                    return (Void) null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, null).join();
        } catch (Exception e) {
            dirtyKeys.addAll(keyFlush);
            dirtyOpened.addAll(openFlush);
            dirtyClaims.addAll(claimFlush);
            plugin.getLogger().log(Level.WARNING, "[HazeCrates] Flush DB fallito", e);
        }
    }

    private void writeKeys(Connection conn, Set<String> entries) throws Exception {
        boolean sqlite = db.isSqlite();
        String upsert = sqlite
                ? "INSERT INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,?) ON CONFLICT(uuid,crate_id) DO UPDATE SET amount=excluded.amount"
                : "INSERT INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount)";
        try (PreparedStatement s = conn.prepareStatement(upsert)) {
            for (String k : entries) {
                String[] p = split(k);
                if (p == null) continue;
                s.setString(1, p[0]);
                s.setString(2, p[1]);
                s.setInt(3, keys.getOrDefault(k, 0));
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private void writeOpened(Connection conn, Set<String> entries) throws Exception {
        boolean sqlite = db.isSqlite();
        String upsert = sqlite
                ? "INSERT INTO fc_player_stats(uuid,crate_id,total_opened,last_opened_at) VALUES(?,?,?,?) ON CONFLICT(uuid,crate_id) DO UPDATE SET total_opened=excluded.total_opened, last_opened_at=excluded.last_opened_at"
                : "INSERT INTO fc_player_stats(uuid,crate_id,total_opened,last_opened_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE total_opened=VALUES(total_opened), last_opened_at=VALUES(last_opened_at)";
        String now = java.time.Instant.now().toString();
        try (PreparedStatement s = conn.prepareStatement(upsert)) {
            for (String k : entries) {
                String[] p = split(k);
                if (p == null) continue;
                s.setString(1, p[0]);
                s.setString(2, p[1]);
                s.setInt(3, opened.getOrDefault(k, 0));
                s.setString(4, now);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private void writeClaims(Connection conn, Set<String> entries) throws Exception {
        boolean sqlite = db.isSqlite();
        String upsert = sqlite
                ? "INSERT INTO fc_reward_progress(uuid,crate_id,milestone_id,current_count,claimed) VALUES(?,?,?,0,1) ON CONFLICT(uuid,crate_id,milestone_id) DO UPDATE SET claimed=1"
                : "INSERT INTO fc_reward_progress(uuid,crate_id,milestone_id,current_count,claimed) VALUES(?,?,?,0,1) ON DUPLICATE KEY UPDATE claimed=1";
        try (PreparedStatement s = conn.prepareStatement(upsert)) {
            for (String k : entries) {
                String[] p = k.split(":", 3);
                if (p.length != 3) continue;
                s.setString(1, p[0]);
                s.setString(2, p[1]);
                s.setString(3, p[2]);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static String mk(UUID uuid, String crate) {
        return uuid + ":" + crate;
    }

    private static String[] split(String k) {
        int i = k.indexOf(':');
        if (i <= 0 || i >= k.length() - 1) return null;
        return new String[]{k.substring(0, i), k.substring(i + 1)};
    }
}
