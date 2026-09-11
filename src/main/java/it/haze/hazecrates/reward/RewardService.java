// made by haze
package it.haze.hazecrates.reward;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.MilestoneDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class RewardService {

    private final HazeCrates plugin;
    private final Random random = new Random();

    public RewardService(HazeCrates plugin) {
        this.plugin = plugin;
    }

    public Optional<RewardDefinition> choose(Player player, CrateDefinition crate) {
        List<RewardDefinition> pool = crate.rewards().stream()
                .filter(r -> r.permission().isBlank() || player.hasPermission(r.permission()))
                .toList();
        if (pool.isEmpty()) return Optional.empty();
        int total = pool.stream().mapToInt(RewardDefinition::weight).sum();
        if (total <= 0) return Optional.empty();
        int roll = random.nextInt(total);
        for (RewardDefinition r : pool) {
            roll -= r.weight();
            if (roll < 0) return Optional.of(r);
        }
        return Optional.of(pool.get(pool.size() - 1));
    }

    public void grant(Player player, CrateDefinition crate, RewardDefinition reward) {
        for (String cmd : reward.commands())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));

        plugin.messages().send(player, "reward", Map.of("reward", reward.plainName()));

        if (!crate.broadcast().isBlank() && reward.broadcast()) {
            int threshold = crate.broadcastThreshold();
            boolean allowed = threshold <= 0 || reward.weight() <= threshold;
            if (allowed) {
                var message = plugin.messages().prefix().append(plugin.messages().parse(
                        crate.broadcast(),
                        Map.of(
                                "player", player.getName(),
                                "reward", reward.plainName(),
                                "crate", crate.displayName()
                        )));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(message);
                }
            }
        }
    }

    public void grantMilestone(Player player, CrateDefinition crate, MilestoneDefinition m) {
        for (String cmd : m.commands())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));

        plugin.messages().send(player, "milestone",
                Map.of("milestone", m.id(), "crate", crate.id()));

        if (m.broadcast()) {
            Bukkit.broadcast(plugin.messages().prefix().append(plugin.messages().parse(
                    "<gold><yellow>" + player.getName()
                    + "</yellow> <gold>ha completato <yellow>" + m.id()
                    + "</yellow> <gold>in </gold>" + crate.displayName() + "<gold>!</gold>")));
        }
    }
}
