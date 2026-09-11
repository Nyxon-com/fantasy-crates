// made by haze
package it.haze.hazecrates.command;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.command.CommandSupport.TargetCrateAmount;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.gui.preview.PreviewInventory;
import it.haze.hazecrates.listener.CrateListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrateCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE_GIVE = "/crate give <giocatore> <crate> [quantita]";
    private static final String USAGE_TAKE = "/crate take <giocatore> <crate> [quantita]";
    private static final String USAGE_SET = "/crate set <giocatore> <crate> <quantita>";
    private static final String USAGE_LOOTBOX = "/crate lootbox <giocatore> <crate> [quantita]";
    private static final String USAGE_ITEM = "/crate item <crate> [quantita]";
    private static final String USAGE_PLACE = "/crate place <crate>";
    private static final String USAGE_PREVIEW = "/crate preview <crate>";
    private static final String USAGE_OPEN = "/crate open [giocatore] <crate>";
    private static final String USAGE_OPEN_SELF = "/open [giocatore] <crate>";

    private final HazeCrates plugin;
    private final ChiaveCommand keys;

    public CrateCommand(HazeCrates plugin) {
        this.plugin = plugin;
        this.keys = new ChiaveCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("open")) {
            return open(sender, args, USAGE_OPEN_SELF);
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String[] rest = restOf(args);

        return switch (action) {
            case "give" -> requireKeyAdmin(sender) && keys.give(sender, rest, USAGE_GIVE);
            case "take" -> requireKeyAdmin(sender) && keys.take(sender, rest, USAGE_TAKE);
            case "set" -> requireKeyAdmin(sender) && keys.set(sender, rest, USAGE_SET);
            case "lootbox" -> requireKeyAdmin(sender) && giveLootbox(sender, rest);
            case "item" -> handleItem(sender, rest);
            case "preview" -> preview(sender, rest);
            case "open" -> open(sender, rest, USAGE_OPEN);
            case "place" -> requireAdmin(sender) && place(sender, rest);
            case "break" -> requireAdmin(sender) && breakCrate(sender, rest);
            case "stats" -> requireAdmin(sender) && stats(sender, rest);
            case "reload" -> requireAdmin(sender) && reload(sender, rest);
            default -> {
                CommandSupport.usage(plugin, sender, "/crate help");
                yield true;
            }
        };
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.messages().parse("<dark_gray>─── <gold>HazeCrates</gold> <dark_gray>───</dark_gray>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_GIVE + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_TAKE + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_SET + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_LOOTBOX + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_ITEM + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_PLACE + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/crate break</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>" + USAGE_PREVIEW + "</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/crate open [giocatore] <crate></yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/open [giocatore] <crate></yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/crate stats [giocatore]</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/crate reload</yellow>"));
        sender.sendMessage(plugin.messages().parse("<yellow>/chiave</yellow> <gray>– saldo chiavi</gray>"));
    }

    private boolean preview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandSupport.usage(plugin, sender, USAGE_PREVIEW);
            return true;
        }
        CrateDefinition crate = CommandSupport.requireCrate(plugin, sender, args, USAGE_PREVIEW);
        if (crate != null) PreviewInventory.open(player, crate, plugin);
        return true;
    }

    private boolean open(CommandSender sender, String[] args, String usage) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                CommandSupport.usage(plugin, sender, usage);
                return true;
            }
            CrateDefinition crate = plugin.crates().find(args[0]);
            if (crate == null) {
                plugin.messages().send(player, "unknown-crate", Map.of("crate", args[0]));
                return true;
            }
            Location at = openLocation(player, crate);
            new CrateListener(plugin).tryOpen(player, crate, at, false);
            return true;
        }
        if (args.length == 2) {
            if (!sender.hasPermission("hazecrates.admin")) {
                CommandSupport.fail(plugin, sender, "no-permission", Map.of());
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                CommandSupport.fail(plugin, sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
            CrateDefinition crate = plugin.crates().find(args[1]);
            if (crate == null) {
                CommandSupport.fail(plugin, sender, "unknown-crate", Map.of("crate", args[1]));
                return true;
            }
            Location at = openLocation(target, crate);
            new CrateListener(plugin).tryOpen(target, crate, at, false);
            return true;
        }
        CommandSupport.usage(plugin, sender, usage);
        return true;
    }

    private static Location openLocation(Player player, CrateDefinition crate) {
        if (crate.keyType() == it.haze.hazecrates.crate.KeyType.LOOTBOX) {
            return player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.8));
        }
        Block target = player.getTargetBlockExact(6);
        if (target != null) {
            return target.getLocation().add(0.5, 1.0, 0.5);
        }
        return player.getLocation();
    }

    private boolean place(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandSupport.usage(plugin, sender, USAGE_PLACE);
            return true;
        }
        CrateDefinition crate = CommandSupport.requireCrate(plugin, sender, args, USAGE_PLACE);
        if (crate == null) return true;
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            plugin.messages().send(player, "invalid-target");
            return true;
        }
        plugin.placements().place(target.getLocation(), crate);
        if (plugin.externalPluginsReady()) {
            plugin.display().stopAt(target.getLocation());
            plugin.display().startAt(it.haze.hazecrates.crate.CratePlacementService.key(target.getLocation()),
                    target.getLocation(), crate);
        }
        plugin.messages().send(player, "created");
        return true;
    }

    private boolean breakCrate(CommandSender sender, String[] args) {
        if (args.length != 0) {
            CommandSupport.usage(plugin, sender, "/crate break");
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        Block target = player.getTargetBlockExact(6);
        if (target == null || !new CrateListener(plugin).breakCrateAt(target, player)) {
            plugin.messages().send(player, "invalid-target");
        }
        return true;
    }

    private boolean handleItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hazecrates.item") && !sender.hasPermission("hazecrates.admin")) {
            CommandSupport.fail(plugin, sender, "no-permission", Map.of());
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandSupport.usage(plugin, sender, USAGE_ITEM);
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            CommandSupport.usage(plugin, sender, USAGE_ITEM);
            return true;
        }
        CrateDefinition crate = plugin.crates().find(args[0]);
        if (crate == null) {
            plugin.messages().send(player, "unknown-crate", Map.of("crate", args[0]));
            return true;
        }
        Integer amount = CommandSupport.optionalAmount(plugin, sender, args, 1);
        if (amount == null) return true;
        amount = Math.min(64, amount);
        var item = new CrateListener(plugin).buildCrateItem(crate);
        item.setAmount(amount);
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        plugin.messages().send(player, "item-given", Map.of("crate", crate.displayName()));
        return true;
    }

    private boolean giveLootbox(CommandSender sender, String[] args) {
        TargetCrateAmount parsed = CommandSupport.parsePlayerCrateAmount(plugin, sender, args, USAGE_LOOTBOX, false);
        if (parsed == null) return true;
        plugin.keys().givePhysical(parsed.target(), parsed.crate(), parsed.amount());
        plugin.messages().send(parsed.target(), "item-given", Map.of("crate", parsed.crate().displayName()));
        if (sender instanceof Player admin && !admin.equals(parsed.target())) {
            plugin.messages().send(admin, "item-given", Map.of("crate", parsed.crate().displayName()));
        }
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        if (args.length > 1) {
            CommandSupport.usage(plugin, sender, "/crate stats [giocatore]");
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        Player target = player;
        if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                CommandSupport.fail(plugin, sender, "player-not-found", Map.of("player", args[0]));
                return true;
            }
        }
        Player shown = target;
        player.sendMessage(plugin.messages().parse("<gold><bold>Aperture di " + shown.getName() + "</bold></gold>"));
        plugin.crates().all().forEach(crate ->
                plugin.stats().opened(shown.getUniqueId(), crate.id())
                        .thenAccept(n -> Bukkit.getScheduler().runTask(plugin,
                                () -> player.sendMessage(plugin.messages().parse(
                                        "<gray>• </gray>" + crate.displayName() + "<gray>: </gray><yellow>" + n + "</yellow>")))));
        return true;
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) {
            CommandSupport.usage(plugin, sender, "/crate reload");
            return true;
        }
        plugin.reloadPlugin();
        if (sender instanceof Player player) plugin.messages().send(player, "reload");
        else sender.sendMessage("HazeCrates ricaricato.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("open")) {
            if (args.length == 1) {
                List<String> names = new java.util.ArrayList<>(CommandSupport.playerNames());
                names.addAll(plugin.crates().tabIds());
                return CommandSupport.filter(args[0], names);
            }
            if (args.length == 2) return CommandSupport.filter(args[1], plugin.crates().tabIds());
            return List.of();
        }
        if (args.length == 1) {
            return CommandSupport.filter(args[0], List.of(
                    "give", "take", "set", "lootbox", "item",
                    "place", "break", "preview", "open", "stats", "reload", "help"
            ));
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("give") || action.equals("take") || action.equals("set") || action.equals("lootbox")) {
            if (args.length == 2) return CommandSupport.filter(args[1], CommandSupport.playerNames());
            if (args.length == 3) return CommandSupport.filter(args[2], plugin.crates().tabIds());
            if (args.length == 4) return CommandSupport.filter(args[3], CommandSupport.amounts());
            return List.of();
        }
        if (action.equals("item") || action.equals("place") || action.equals("preview")) {
            if (args.length == 2) return CommandSupport.filter(args[1], plugin.crates().tabIds());
            if (action.equals("item") && args.length == 3) return CommandSupport.filter(args[2], CommandSupport.amounts());
            return List.of();
        }
        if (action.equals("open")) {
            if (args.length == 2) {
                List<String> names = new java.util.ArrayList<>(CommandSupport.playerNames());
                names.addAll(plugin.crates().tabIds());
                return CommandSupport.filter(args[1], names);
            }
            if (args.length == 3) return CommandSupport.filter(args[2], plugin.crates().tabIds());
            return List.of();
        }
        if (action.equals("stats") && args.length == 2) {
            return CommandSupport.filter(args[1], CommandSupport.playerNames());
        }
        return List.of();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("hazecrates.admin")) return true;
        CommandSupport.fail(plugin, sender, "no-permission", Map.of());
        return false;
    }

    private boolean requireKeyAdmin(CommandSender sender) {
        if (sender.hasPermission("hazecrates.admin") || sender.hasPermission("hazecrates.key")) return true;
        CommandSupport.fail(plugin, sender, "no-permission", Map.of());
        return false;
    }

    private static String[] restOf(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }
}
