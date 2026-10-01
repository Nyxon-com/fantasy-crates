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
    private final Map<String, CacheVal<List<LeaderboardEntry>>> lbCache = new ConcurrentHashMap<>();
    private final long lbTtl;

    public StatsService(DatabaseService db, HazeCrates plugin) {
        this.db     = db;
        this.plugin = plugin;
        this.lbTtl  = Duration.ofSeconds(
                Math.max(5, plugin.getConfig().getLong("leaderboard-cache-seconds", 60))).toNanos();
    }

    public CompletableFuture<Integer> opened(UUID uuid, String crate) {
        var store = plugin.playerData();
        if (store.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(store.getOpened(uuid, crate));
        }
        return store.load(uuid).thenApply(v -> store.getOpened(uuid, crate));
    }

    public CompletableFuture<Integer> recordOpening(Player player, CrateDefinition crate) {
        var store = plugin.playerData();
        UUID uuid = player.getUniqueId();
        Runnable grant = () -> {
            int total = store.incrementOpened(uuid, crate.id());
            checkMilestones(player, crate, total);
        };
        if (store.isLoaded(uuid)) {
            grant.run();
            return CompletableFuture.completedFuture(store.getOpened(uuid, crate.id()));
        }
        return store.load(uuid).thenApply(v -> {
            grant.run();
            return store.getOpened(uuid, crate.id());
        });
    }

    private void checkMilestones(Player player, CrateDefinition crate, int total) {
        var store = plugin.playerData();
        for (MilestoneDefinition m : crate.milestones()) {
            if (total < m.openingsRequired()) continue;
            boolean fire;
            if (m.resetAfterClaim()) {
                fire = total % m.openingsRequired() == 0;
            } else {
                fire = store.tryClaim(player.getUniqueId(), crate.id(), m.id());
            }
            if (fire) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.rewards().grantMilestone(player, crate, m));
            }
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String crate) {
        CacheVal<List<LeaderboardEntry>> cv = lbCache.get(crate);
        if (cv != null && !cv.expired()) return CompletableFuture.completedFuture(cv.val);
        if (!db.isAvailable()) return CompletableFuture.completedFuture(List.of());
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
