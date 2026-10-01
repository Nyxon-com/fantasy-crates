// made by haze
package it.haze.hazecrates.database;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.util.AsyncWorkService;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Cache in RAM per chiavi virtuali / aperture / milestone.
 * Load al join, mutazioni sync in memoria, flush async a intervalli.
 */
public final class PlayerDataStore {

    private final HazeCrates plugin;
    private final DatabaseService db;
    private final AsyncWorkService async;

    private final ConcurrentMap<String, Integer> keys = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> opened = new ConcurrentHashMap<>();
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    private final Set<String> dirtyKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyOpened = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyClaims = ConcurrentHashMap.newKeySet();

    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> unloading = new ConcurrentHashMap<>();
    private final AtomicBoolean flushing = new AtomicBoolean();

    private ScheduledFuture<?> flushTask;

    public PlayerDataStore(HazeCrates plugin, DatabaseService db, AsyncWorkService async) {
        this.plugin = plugin;
        this.db = db;
        this.async = async;
    }

    public void start() {
        long periodSec = Math.max(2L, plugin.getConfig().getLong("database.flush-seconds", 5L));
        flushTask = async.scheduleWithFixedDelay(this::flushDirty, Duration.ofSeconds(periodSec));
        for (var player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
        try {
            CompletableFuture.allOf(unloading.values().toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ignored) {
            // Failed player writes restored their dirty entries for this final flush.
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (flushing.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(Duration.ofMillis(10));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!async.awaitIdle(Duration.ofSeconds(30))) {
            plugin.getLogger().warning("[HazeCrates] Async database work is still pending during shutdown.");
        }
        flushDirty();
    }

    public CompletableFuture<Void> load(UUID uuid) {
        if (loaded.contains(uuid)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> pendingUnload = unloading.get(uuid);
        if (pendingUnload != null && !pendingUnload.isDone()) {
            return pendingUnload.handle((v, err) -> null).thenCompose(v -> load(uuid));
        }
        CompletableFuture<Void> future = loading.computeIfAbsent(uuid, id -> db.ready().thenCompose(ready -> {
            if (!db.isAvailable()) {
                loaded.add(id);
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
            }, null);
        }));
        future.whenComplete((v, err) -> {
            loading.remove(uuid, future);
            if (err != null) {
                plugin.getLogger().log(Level.WARNING,
                        "[HazeCrates] Load dati player fallito: " + uuid, err);
            }
        });
        return future;
    }

    public void unload(UUID uuid) {
        loaded.remove(uuid);
        loading.remove(uuid);
        CompletableFuture<Void> write = flushPlayer(uuid);
        unloading.put(uuid, write);
        write.whenComplete((v, error) -> {
            unloading.remove(uuid, write);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "[HazeCrates] Flush player fallito: " + uuid, error);
            } else if (!loaded.contains(uuid)) {
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
                String k = mk(uuid, r.getString(1));
                if (!dirtyKeys.contains(k)) {
                    keys.put(k, r.getInt(2));
                }
            }
        }
    }

    private void loadOpened(Connection conn, UUID uuid) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(
                "SELECT crate_id, total_opened FROM fc_player_stats WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            ResultSet r = s.executeQuery();
            while (r.next()) {
                String k = mk(uuid, r.getString(1));
                if (!dirtyOpened.contains(k)) {
                    opened.put(k, r.getInt(2));
                }
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

    private CompletableFuture<Void> flushPlayer(UUID uuid) {
        if (!db.isAvailable()) return CompletableFuture.completedFuture(null);
        String prefix = uuid + ":";
        Map<String, Integer> keySnap = new HashMap<>();
        Map<String, Integer> openSnap = new HashMap<>();
        Set<String> claimFlush = new HashSet<>();
        for (String k : dirtyKeys) {
            if (k.startsWith(prefix)) keySnap.put(k, keys.getOrDefault(k, 0));
        }
        for (String k : dirtyOpened) {
            if (k.startsWith(prefix)) openSnap.put(k, opened.getOrDefault(k, 0));
        }
        for (String k : dirtyClaims) if (k.startsWith(prefix)) claimFlush.add(k);
        if (keySnap.isEmpty() && openSnap.isEmpty() && claimFlush.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        dirtyKeys.removeAll(keySnap.keySet());
        dirtyOpened.removeAll(openSnap.keySet());
        dirtyClaims.removeAll(claimFlush);
        return db.transaction(conn -> {
                try {
                    writeKeys(conn, keySnap);
                    writeOpened(conn, openSnap);
                    writeClaims(conn, claimFlush);
                    return (Void) null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((v, error) -> {
                if (error != null) {
                    dirtyKeys.addAll(keySnap.keySet());
                    dirtyOpened.addAll(openSnap.keySet());
                    dirtyClaims.addAll(claimFlush);
                }
            });
    }

    private void flushDirty() {
        if (!flushing.compareAndSet(false, true)) return;
        try {
            flushDirtyNow();
        } finally {
            flushing.set(false);
        }
    }

    private void flushDirtyNow() {
        if (!db.isAvailable()) return;
        Map<String, Integer> keySnap = new HashMap<>();
        Map<String, Integer> openSnap = new HashMap<>();
        for (String k : dirtyKeys) keySnap.put(k, keys.getOrDefault(k, 0));
        for (String k : dirtyOpened) openSnap.put(k, opened.getOrDefault(k, 0));
        Set<String> claimFlush = Set.copyOf(dirtyClaims);
        if (keySnap.isEmpty() && openSnap.isEmpty() && claimFlush.isEmpty()) return;
        dirtyKeys.removeAll(keySnap.keySet());
        dirtyOpened.removeAll(openSnap.keySet());
        dirtyClaims.removeAll(claimFlush);
        try {
            db.transaction(conn -> {
                try {
                    writeKeys(conn, keySnap);
                    writeOpened(conn, openSnap);
                    writeClaims(conn, claimFlush);
                    return (Void) null;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).join();
        } catch (Exception e) {
            dirtyKeys.addAll(keySnap.keySet());
            dirtyOpened.addAll(openSnap.keySet());
            dirtyClaims.addAll(claimFlush);
            plugin.getLogger().log(Level.WARNING, "[HazeCrates] Flush DB fallito", e);
        }
    }

    private void writeKeys(Connection conn, Map<String, Integer> entries) throws Exception {
        if (entries.isEmpty()) return;
        boolean sqlite = db.isSqlite();
        String upsert = sqlite
                ? "INSERT INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,?) ON CONFLICT(uuid,crate_id) DO UPDATE SET amount=excluded.amount"
                : "INSERT INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount)";
        try (PreparedStatement s = conn.prepareStatement(upsert)) {
            for (Map.Entry<String, Integer> e : entries.entrySet()) {
                String[] p = split(e.getKey());
                if (p == null) continue;
                s.setString(1, p[0]);
                s.setString(2, p[1]);
                s.setInt(3, e.getValue());
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private void writeOpened(Connection conn, Map<String, Integer> entries) throws Exception {
        if (entries.isEmpty()) return;
        boolean sqlite = db.isSqlite();
        String upsert = sqlite
                ? "INSERT INTO fc_player_stats(uuid,crate_id,total_opened,last_opened_at) VALUES(?,?,?,?) ON CONFLICT(uuid,crate_id) DO UPDATE SET total_opened=excluded.total_opened, last_opened_at=excluded.last_opened_at"
                : "INSERT INTO fc_player_stats(uuid,crate_id,total_opened,last_opened_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE total_opened=VALUES(total_opened), last_opened_at=VALUES(last_opened_at)";
        String now = java.time.Instant.now().toString();
        try (PreparedStatement s = conn.prepareStatement(upsert)) {
            for (Map.Entry<String, Integer> e : entries.entrySet()) {
                String[] p = split(e.getKey());
                if (p == null) continue;
                s.setString(1, p[0]);
                s.setString(2, p[1]);
                s.setInt(3, e.getValue());
                s.setString(4, now);
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private void writeClaims(Connection conn, Set<String> entries) throws Exception {
        if (entries.isEmpty()) return;
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
