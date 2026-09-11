// made by haze
package it.haze.hazecrates.command;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.command.CommandSupport.TargetCrateAmount;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.KeyType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChiaveCommand implements CommandExecutor, TabCompleter {

    public static final String USAGE_GIVE = "/chiave give <giocatore> <crate> [quantita]";
    public static final String USAGE_TAKE = "/chiave take <giocatore> <crate> [quantita]";
    public static final String USAGE_SET = "/chiave set <giocatore> <crate> <quantita>";

    private final HazeCrates plugin;

    public ChiaveCommand(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("give") || action.equals("take") || action.equals("set")) {
                if (!sender.hasPermission("hazecrates.admin") && !sender.hasPermission("hazecrates.key")) {
                    CommandSupport.fail(plugin, sender, "no-permission", Map.of());
                    return true;
                }
                String[] rest = restOf(args);
                return switch (action) {
                    case "give" -> give(sender, rest, USAGE_GIVE);
                    case "take" -> take(sender, rest, USAGE_TAKE);
                    default -> set(sender, rest, USAGE_SET);
                };
            }
        }

        if (!(sender instanceof Player player)) {
            CommandSupport.usage(plugin, sender, USAGE_GIVE);
            return true;
        }

        if (args.length == 0) {
            list(player);
            return true;
        }
        if (args.length != 1) {
            CommandSupport.usage(plugin, sender, "/chiave [crate]");
            return true;
        }

        CrateDefinition crate = plugin.crates().find(args[0]);
        if (crate == null) {
            plugin.messages().send(player, "unknown-crate", Map.of("crate", args[0]));
            return true;
        }
        show(player, crate);
        return true;
    }

    public boolean give(CommandSender sender, String[] args, String usage) {
        TargetCrateAmount parsed = CommandSupport.parsePlayerCrateAmount(plugin, sender, args, usage, false);
        if (parsed == null) return true;
        deliver(sender, parsed.target(), parsed.crate(), parsed.amount());
        return true;
    }

    public boolean take(CommandSender sender, String[] args, String usage) {
        TargetCrateAmount parsed = CommandSupport.parsePlayerCrateAmount(plugin, sender, args, usage, false);
        if (parsed == null) return true;
        int removedItems = plugin.keys().takePhysical(parsed.target(), parsed.crate().id(), parsed.amount());
        plugin.keys().takeVirtual(parsed.target().getUniqueId(), parsed.crate().id(), Math.max(0, parsed.amount() - removedItems));
        plugin.keys().play(parsed.target(), parsed.crate().id(), "taken");
        notify(sender, parsed.target(), parsed.crate(), parsed.amount(), "key-taken", "key-taken-admin", "miste");
        return true;
    }

    public boolean set(CommandSender sender, String[] args, String usage) {
        TargetCrateAmount parsed = CommandSupport.parsePlayerCrateAmount(plugin, sender, args, usage, true);
        if (parsed == null) return true;
        plugin.keys().setVirtual(parsed.target().getUniqueId(), parsed.crate().id(), parsed.amount());
        plugin.keys().play(parsed.target(), parsed.crate().id(), "given");
        notify(sender, parsed.target(), parsed.crate(), parsed.amount(), "key-set", "key-set-admin", "virtuale");
        return true;
    }

    public void deliver(CommandSender sender, Player target, CrateDefinition crate, int amount) {
        boolean virtual = crate.keyType() == KeyType.VIRTUAL;
        if (virtual) {
            plugin.keys().addVirtual(target.getUniqueId(), crate.id(), amount);
            plugin.keys().play(target, crate.id(), "given");
        } else {
            plugin.keys().givePhysical(target, crate, amount);
        }
        String type = virtual ? "virtuale" : "fisica";
        plugin.messages().send(target, virtual ? "key-given-virtual" : "key-given-physical", placeholders(target, crate, amount, type));
        if (sender instanceof Player admin && !admin.equals(target)) {
            plugin.messages().send(admin, "key-given-admin", placeholders(target, crate, amount, type));
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("Date " + amount + " chiavi " + type + " " + crate.id() + " a " + target.getName());
        }
    }

    private void notify(CommandSender sender, Player target, CrateDefinition crate, int amount,
                        String targetKey, String adminKey, String type) {
        Map<String, String> ph = placeholders(target, crate, amount, type);
        plugin.messages().send(target, targetKey, ph);
        if (sender instanceof Player admin && !admin.equals(target)) {
            plugin.messages().send(admin, adminKey, ph);
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(crate.id() + " x" + amount + " -> " + target.getName());
        }
    }

    private static Map<String, String> placeholders(Player target, CrateDefinition crate, int amount, String type) {
        return Map.of(
                "crate", crate.displayName(),
                "amount", String.valueOf(amount),
                "player", target.getName(),
                "type", type
        );
    }

    private void list(Player player) {
        plugin.keys().allVirtual(player.getUniqueId()).thenAccept(virtuals ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(player, "keys-header");
                    boolean any = false;
                    for (CrateDefinition crate : plugin.crates().all()) {
                        int physical = plugin.keys().countPhysical(player, crate.id());
                        int virtual = virtuals.getOrDefault(crate.id(), 0);
                        if (physical <= 0 && virtual <= 0) continue;
                        any = true;
                        plugin.messages().send(player, "keys-line", Map.of(
                                "crate", crate.displayName(),
                                "id", crate.id(),
                                "physical", String.valueOf(physical),
                                "virtual", String.valueOf(virtual)
                        ));
                    }
                    if (!any) {
                        plugin.messages().send(player, "keys-empty", Map.of("player", player.getName()));
                    }
                    plugin.messages().send(player, "keys-hint");
                }));
    }

    private void show(Player player, CrateDefinition crate) {
        plugin.keys().virtual(player.getUniqueId(), crate.id()).thenAccept(virtual ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int physical = plugin.keys().countPhysical(player, crate.id());
                    plugin.messages().send(player, "keys-line", Map.of(
                            "crate", crate.displayName(),
                            "id", crate.id(),
                            "physical", String.valueOf(physical),
                            "virtual", String.valueOf(virtual)
                    ));
                }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(plugin.crates().tabIds());
            if (sender.hasPermission("hazecrates.admin") || sender.hasPermission("hazecrates.key")) {
                opts.addAll(0, List.of("give", "take", "set"));
            }
            return CommandSupport.filter(args[0], opts);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            return List.of();
        }
        if (args.length == 2) return CommandSupport.filter(args[1], CommandSupport.playerNames());
        if (args.length == 3) return CommandSupport.filter(args[2], plugin.crates().tabIds());
        if (args.length == 4) return CommandSupport.filter(args[3], CommandSupport.amounts());
        return List.of();
    }

    private static String[] restOf(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }
}
