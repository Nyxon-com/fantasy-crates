// made by haze
package it.haze.hazecrates.reward;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.MilestoneDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Grant stile ExcellentCrates: clone del template in cache.
 * Evita `mmoitems give` a ogni open (rigenera NBT/stats = lag).
 */
public final class RewardService {

    private final HazeCrates plugin;
    private final Random random = new Random();

    public RewardService(HazeCrates plugin) {
        this.plugin = plugin;
    }

    public Optional<RewardDefinition> choose(Player player, CrateDefinition crate) {
        int total = 0;
        for (RewardDefinition r : crate.rewards()) {
            if (r.weight() <= 0) continue;
            if (!r.permission().isBlank() && !player.hasPermission(r.permission())) continue;
            total += r.weight();
        }
        if (total <= 0) return Optional.empty();
        int roll = random.nextInt(total);
        RewardDefinition last = null;
        for (RewardDefinition r : crate.rewards()) {
            if (r.weight() <= 0) continue;
            if (!r.permission().isBlank() && !player.hasPermission(r.permission())) continue;
            last = r;
            roll -= r.weight();
            if (roll < 0) return Optional.of(r);
        }
        return Optional.ofNullable(last);
    }

    public boolean hasAccessible(Player player, CrateDefinition crate) {
        for (RewardDefinition r : crate.rewards()) {
            if (r.weight() <= 0) continue;
            if (r.permission().isBlank() || player.hasPermission(r.permission())) return true;
        }
        return false;
    }

    public void grant(Player player, CrateDefinition crate, RewardDefinition reward) {
        grant(player, crate, reward, true, true);
    }

    public void grant(Player player, CrateDefinition crate, RewardDefinition reward, boolean notifyPlayer) {
        grant(player, crate, reward, notifyPlayer, notifyPlayer);
    }

    public void grant(Player player, CrateDefinition crate, RewardDefinition reward,
                      boolean notifyPlayer, boolean allowBroadcast) {
        if (giveFromTemplate(player, reward)) {
            // ok: item da cache, niente console mi give
        } else {
            boolean ran = false;
            for (String cmd : reward.commands()) {
                if (cmd == null || cmd.isBlank()) continue;
                ran = true;
                run(player, cmd);
            }
            if (!ran) {
                ItemStack prize = reward.prize();
                if (prize != null) {
                    give(player, prize);
                }
            }
        }

        if (notifyPlayer) {
            plugin.messages().send(player, "reward", Map.of("reward", reward.plainName()));
        }

        if (allowBroadcast && !crate.broadcast().isBlank() && reward.broadcast()) {
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

    /**
     * Se i commands sono solo give di provider custom verso questo reward,
     * consegna il template già risolto (ExcellentCrates-like).
     */
    private boolean giveFromTemplate(Player player, RewardDefinition reward) {
        ItemStack prize = reward.prize();
        if (prize == null) {
            return false;
        }
        List<String> commands = reward.commands();
        if (commands == null || commands.isEmpty()) {
            give(player, prize);
            return true;
        }
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            if (!isProviderGiveCommand(raw)) {
                return false;
            }
        }
        give(player, prize);
        return true;
    }

    private static boolean isProviderGiveCommand(String raw) {
        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String lower = cmd.toLowerCase(Locale.ROOT);
        return lower.startsWith("mmoitems give ")
                || lower.startsWith("mi give ")
                || lower.startsWith("nexo give ")
                || lower.startsWith("iagive ")
                || lower.startsWith("ia give ")
                || lower.startsWith("give ");
    }

    public void grantMilestone(Player player, CrateDefinition crate, MilestoneDefinition m) {
        for (String cmd : m.commands()) {
            run(player, cmd);
        }

        plugin.messages().send(player, "milestone",
                Map.of("milestone", m.id(), "crate", crate.id()));

        if (m.broadcast()) {
            Bukkit.broadcast(plugin.messages().prefix().append(plugin.messages().parse(
                    "<gold><yellow>" + player.getName()
                    + "</yellow> <gold>ha completato <yellow>" + m.id()
                    + "</yellow> <gold>in </gold>" + crate.displayName() + "<gold>!</gold>")));
        }
    }

    private boolean run(Player player, String raw) {
        if (raw == null || raw.isBlank()) return false;
        String cmd = expand(raw.trim(), player.getName());
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String[] parts = cmd.split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return false;

        String root = parts[0].toLowerCase(Locale.ROOT);
        if (root.equals("give") || root.equals("minecraft:give")) {
            return handleGive(player, parts, 1);
        }
        if ((root.equals("mi") || root.equals("mmoitems")) && parts.length >= 2
                && parts[1].equalsIgnoreCase("give")) {
            return handleGive(player, parts, 2);
        }
        if (root.equals("nexo") && parts.length >= 2 && parts[1].equalsIgnoreCase("give")) {
            return handleGive(player, parts, 2);
        }
        if (root.equals("iagive") || (root.equals("ia") && parts.length >= 2
                && parts[1].equalsIgnoreCase("give"))) {
            return handleGive(player, parts, root.equals("iagive") ? 1 : 2);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        return false;
    }

    private boolean handleGive(Player player, String[] parts, int start) {
        List<String> args = new ArrayList<>();
        for (int i = start; i < parts.length; i++) args.add(parts[i]);
        if (args.isEmpty()) return false;

        String playerName = player.getName();
        String itemId;
        int amount = 1;

        int playerAt = indexOfIgnoreCase(args, playerName);
        if (playerAt == 0 && args.size() < 2) return false;
        if (playerAt == 0) {
            itemId = joinItem(args, 1);
            amount = takeTrailingAmount(args, 2);
        } else if (playerAt > 0) {
            itemId = joinItem(args.subList(0, playerAt), 0);
            amount = takeTrailingAmount(args, playerAt + 1);
        } else if (args.size() >= 2 && isNumber(args.get(1))) {
            itemId = args.get(0);
            amount = Integer.parseInt(args.get(1));
        } else {
            itemId = args.get(0);
            amount = takeTrailingAmount(args, 1);
        }

        if (itemId == null || itemId.isBlank()) return false;
        ItemStack item = plugin.externalItems().resolveById(itemId, amount);
        if (item == null) {
            plugin.getLogger().warning("[HazeCrates] Unknown give item '" + itemId + "' for " + playerName + ".");
            return false;
        }
        give(player, item);
        return true;
    }

    private static String expand(String cmd, String playerName) {
        return cmd
                .replace("%player%", playerName)
                .replace("%PLAYER%", playerName)
                .replace("{player}", playerName)
                .replace("{PLAYER}", playerName);
    }

    private static void give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static int indexOfIgnoreCase(List<String> args, String playerName) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equalsIgnoreCase(playerName)) return i;
        }
        return -1;
    }

    private static String joinItem(List<String> args, int from) {
        if (from >= args.size()) return "";
        String first = args.get(from);
        if (from + 1 < args.size() && !isNumber(args.get(from + 1))
                && args.get(from).indexOf(':') < 0) {
            String second = args.get(from + 1);
            if (!second.startsWith("-") && second.equals(second.toUpperCase(Locale.ROOT))
                    && second.contains("_")) {
                return first + ":" + second;
            }
        }
        return first;
    }

    private static int takeTrailingAmount(List<String> args, int from) {
        if (from < args.size() && isNumber(args.get(args.size() - 1))) {
            return Math.max(1, Integer.parseInt(args.get(args.size() - 1)));
        }
        if (from < args.size() && isNumber(args.get(from))) {
            return Math.max(1, Integer.parseInt(args.get(from)));
        }
        return 1;
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
