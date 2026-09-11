// made by haze
package it.haze.hazecrates.command;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandSupport {

    public record TargetCrateAmount(Player target, CrateDefinition crate, int amount) {}

    private CommandSupport() {}

    public static TargetCrateAmount parsePlayerCrateAmount(
            HazeCrates plugin, CommandSender sender, String[] args, String usage, boolean amountRequired) {
        if (args.length < 2 || (amountRequired && args.length < 3) || args.length > 3) {
            usage(plugin, sender, usage);
            return null;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            fail(plugin, sender, "player-not-found", Map.of("player", args[0]));
            return null;
        }

        CrateDefinition crate = plugin.crates().find(args[1]);
        if (crate == null) {
            fail(plugin, sender, "unknown-crate", Map.of("crate", args[1]));
            return null;
        }

        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                fail(plugin, sender, "invalid-amount", Map.of("amount", args[2]));
                return null;
            }
            if (amount < 1) {
                fail(plugin, sender, "invalid-amount", Map.of("amount", args[2]));
                return null;
            }
        }
        return new TargetCrateAmount(target, crate, amount);
    }

    public static CrateDefinition requireCrate(HazeCrates plugin, CommandSender sender, String[] args, String usage) {
        if (args.length != 1) {
            usage(plugin, sender, usage);
            return null;
        }
        CrateDefinition crate = plugin.crates().find(args[0]);
        if (crate == null) {
            fail(plugin, sender, "unknown-crate", Map.of("crate", args[0]));
        }
        return crate;
    }

    public static Integer optionalAmount(HazeCrates plugin, CommandSender sender, String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int amount = Integer.parseInt(args[index]);
            if (amount < 1) {
                fail(plugin, sender, "invalid-amount", Map.of("amount", args[index]));
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            fail(plugin, sender, "invalid-amount", Map.of("amount", args[index]));
            return null;
        }
    }

    public static void usage(HazeCrates plugin, CommandSender sender, String usage) {
        sender.sendMessage(plugin.messages().parse("<red>Uso: " + usage + "</red>"));
    }

    public static void fail(HazeCrates plugin, CommandSender sender, String key, Map<String, String> values) {
        if (sender instanceof Player player) {
            plugin.messages().send(player, key, values);
        } else {
            sender.sendMessage(plugin.messages().raw(key, values).replaceAll("<[^>]+>", ""));
        }
    }

    public static List<String> filter(String input, List<String> values) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }

    public static List<String> playerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public static List<String> amounts() {
        return List.of("1", "5", "10", "16", "32", "64");
    }
}
