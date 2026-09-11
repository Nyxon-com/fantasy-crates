// made by haze
package it.haze.hazecrates.stats;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.MilestoneDefinition;
import it.haze.hazecrates.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class StatsService {

    private final DatabaseService db;
    private final HazeCrates plugin;
    private final Map<String, Integer> openedCache = new ConcurrentHashMap<>();

    private final Map<String, Integer> memOpenings = new ConcurrentHashMap<>();

    private final java.util.Set<String> memClaimed = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, CacheVal<List<LeaderboardEntry>>> lbCache = new ConcurrentHashMap<>();
    private final long lbTtl;

    public StatsService(DatabaseService db, HazeCrates plugin) {
        this.db     = db;
        this.plugin = plugin;
        this.lbTtl  = Duration.ofSeconds(
                Math.max(5, plugin.getConfig().getLong("leaderboard-cache-seconds", 60))).toNanos();
    }

    private String k(UUID uuid, String crate) { return uuid + ":" + crate; }

    public CompletableFuture<Integer> opened(UUID uuid, String crate) {
        Integer c = openedCache.get(k(uuid, crate));
        if (c != null) return CompletableFuture.completedFuture(c);
        if (!db.isAvailable()) {
            int mem = memOpenings.getOrDefault(k(uuid, crate), 0);
            openedCache.put(k(uuid, crate), mem);
            return CompletableFuture.completedFuture(mem);
        }
        return db.query(conn -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT total_opened FROM fc_player_stats WHERE uuid=? AND crate_id=?")) {
                s.setString(1, uuid.toString()); s.setString(2, crate);
                ResultSet r = s.executeQuery();
                int v = r.next() ? r.getInt(1) : 0;
                openedCache.put(k(uuid, crate), v);
                return v;
            } catch (Exception e) { throw new CompletionException(e); }
        }, 0);
    }

    public CompletableFuture<Integer> recordOpening(Player player, CrateDefinition crate) {

        int memTotal = memOpenings.merge(k(player.getUniqueId(), crate.id()), 1, Integer::sum);

        if (!db.isAvailable()) {

            checkMilestonesSync(player, crate, memTotal);
            return CompletableFuture.completedFuture(memTotal);
        }

        return db.query(conn -> {
            try {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT OR IGNORE INTO fc_player_stats(uuid,crate_id,total_opened) VALUES(?,?,0)")) {
                    ins.setString(1, player.getUniqueId().toString()); ins.setString(2, crate.id());
                    ins.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE fc_player_stats SET total_opened=total_opened+1, last_opened_at=? WHERE uuid=? AND crate_id=?")) {
                    upd.setString(1, java.time.Instant.now().toString());
                    upd.setString(2, player.getUniqueId().toString());
                    upd.setString(3, crate.id());
                    upd.executeUpdate();
                }
                int total = openedCache.merge(k(player.getUniqueId(), crate.id()), 1, Integer::sum);
                checkMilestones(conn, player, crate, total);
                return total;
            } catch (Exception e) { throw new CompletionException(e); }
        }, 0);
    }

    private void checkMilestonesSync(Player player, CrateDefinition crate, int total) {
        for (MilestoneDefinition m : crate.milestones()) {
            if (total < m.openingsRequired()) continue;
            boolean fire;
            if (m.resetAfterClaim()) {
                fire = total % m.openingsRequired() == 0;
            } else {

                String claimKey = player.getUniqueId() + ":" + crate.id() + ":" + m.id();
                fire = memClaimed.add(claimKey);
            }
            if (fire) Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.rewards().grantMilestone(player, crate, m));
        }
    }

    private void checkMilestones(java.sql.Connection conn, Player player,
                                  CrateDefinition crate, int total) {
        for (MilestoneDefinition m : crate.milestones()) {
            if (total < m.openingsRequired()) continue;
            boolean fire = m.resetAfterClaim()
                    ? (total % m.openingsRequired() == 0)
                    : markClaimed(conn, player.getUniqueId(), crate.id(), m.id());
            if (fire) Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.rewards().grantMilestone(player, crate, m));
        }
    }

    private boolean markClaimed(java.sql.Connection conn, UUID uuid, String crate, String ms) {
        try {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT claimed FROM fc_reward_progress WHERE uuid=? AND crate_id=? AND milestone_id=?")) {
                chk.setString(1, uuid.toString()); chk.setString(2, crate); chk.setString(3, ms);
                ResultSet r = chk.executeQuery();
                if (r.next() && r.getInt(1) == 1) return false;
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR REPLACE INTO fc_reward_progress(uuid,crate_id,milestone_id,current_count,claimed) VALUES(?,?,?,0,1)")) {
                ins.setString(1, uuid.toString()); ins.setString(2, crate); ins.setString(3, ms);
                ins.executeUpdate();
            }
            return true;
        } catch (Exception e) { throw new CompletionException(e); }
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String crate) {
        CacheVal<List<LeaderboardEntry>> cv = lbCache.get(crate);
        if (cv != null && !cv.expired()) return CompletableFuture.completedFuture(cv.val);
        return db.query(conn -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT uuid, total_opened FROM fc_player_stats WHERE crate_id=? ORDER BY total_opened DESC LIMIT 100")) {
                s.setString(1, crate);
                ResultSet r = s.executeQuery();
                while (r.next()) {
                    String raw = r.getString(1);
                    String name = resolveName(raw);
                    list.add(new LeaderboardEntry(name, r.getInt(2)));
                }
                lbCache.put(crate, new CacheVal<>(list, System.nanoTime()));
                return list;
            } catch (Exception e) { throw new CompletionException(e); }
        }, List.of());
    }

    private static String resolveName(String rawUuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(rawUuid)).getName();
            return name != null ? name : rawUuid.substring(0, Math.min(8, rawUuid.length()));
        } catch (IllegalArgumentException e) {
            return rawUuid;
        }
    }

    private final class CacheVal<T> {
        final T val; final long at;
        CacheVal(T v, long a) { val = v; at = a; }
        boolean expired() { return System.nanoTime() - at > lbTtl; }
    }
}
