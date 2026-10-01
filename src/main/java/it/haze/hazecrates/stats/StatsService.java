// made by haze
package it.haze.hazecrates.stats;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
    private final Cache<String, CompletableFuture<List<LeaderboardEntry>>> lbCache;

    public StatsService(DatabaseService db, HazeCrates plugin) {
        this.db     = db;
        this.plugin = plugin;
        this.lbCache = Caffeine.newBuilder()
                .maximumSize(128)
                .expireAfterWrite(Duration.ofSeconds(
                        Math.max(5, plugin.getConfig().getLong("leaderboard-cache-seconds", 60))))
                .build();
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
            checkMilestones(uuid, player, crate, total);
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

    private void checkMilestones(UUID uuid, Player player, CrateDefinition crate, int total) {
        var store = plugin.playerData();
        for (MilestoneDefinition m : crate.milestones()) {
            if (total < m.openingsRequired()) continue;
            boolean fire;
            if (m.resetAfterClaim()) {
                fire = total % m.openingsRequired() == 0;
            } else {
                fire = store.tryClaim(uuid, crate.id(), m.id());
            }
            if (fire) {
                Runnable grant = () -> plugin.rewards().grantMilestone(player, crate, m);
                if (Bukkit.isPrimaryThread()) grant.run();
                else Bukkit.getScheduler().runTask(plugin, grant);
            }
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String crate) {
        if (!db.isAvailable()) return CompletableFuture.completedFuture(List.of());
        return lbCache.get(crate, key -> {
            CompletableFuture<List<LeaderboardEntry>> result = new CompletableFuture<>();
            db.query(conn -> {
                List<LeaderboardEntry> rows = new ArrayList<>();
                try (PreparedStatement s = conn.prepareStatement(
                        "SELECT uuid, total_opened FROM fc_player_stats WHERE crate_id=? ORDER BY total_opened DESC LIMIT 100")) {
                    s.setString(1, key);
                    ResultSet r = s.executeQuery();
                    while (r.next()) rows.add(new LeaderboardEntry(r.getString(1), r.getInt(2)));
                    return rows;
                } catch (Exception e) { throw new CompletionException(e); }
            }, List.<LeaderboardEntry>of()).whenComplete((rows, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    lbCache.invalidate(key);
                    return;
                }
                Runnable resolve = () -> {
                    List<LeaderboardEntry> names = new ArrayList<>(rows.size());
                    for (LeaderboardEntry row : rows) {
                        names.add(new LeaderboardEntry(resolveName(row.name()), row.amount()));
                    }
                    result.complete(List.copyOf(names));
                };
                if (Bukkit.isPrimaryThread()) resolve.run();
                else Bukkit.getScheduler().runTask(plugin, resolve);
            });
            return result;
        });
    }

    private static String resolveName(String rawUuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(rawUuid)).getName();
            return name != null ? name : rawUuid.substring(0, Math.min(8, rawUuid.length()));
        } catch (IllegalArgumentException e) {
            return rawUuid;
        }
    }

}
