// made by haze
package it.haze.hazecrates.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.CratePlacementService;
import it.haze.hazecrates.crate.KeyType;
import it.haze.hazecrates.gui.preview.PreviewInventory;
import it.haze.hazecrates.listener.CrateListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

@CommandAlias("crate|crates")
@Description("Gestione delle crate")
public final class CrateCommands extends BaseCommand {

    private static final List<String> HELP_LINES = List.of(
            "/crate give <giocatore> <crate> [quantita]",
            "/crate take <giocatore> <crate> [quantita]",
            "/crate set <giocatore> <crate> <quantita>",
            "/crate lootbox <giocatore> <crate> [quantita]",
            "/crate item <crate> [quantita]",
            "/crate place <crate>",
            "/crate break",
            "/crate preview [giocatore] <crate>",
            "/crate open [giocatore] <crate>",
            "/crate openall [giocatore] <crate>",
            "/open [giocatore] <crate>",
            "/crate stats [giocatore]",
            "/crate reload"
    );

    private final HazeCrates plugin;
    private final KeyActions keys;
    private final CrateListener crates;

    public CrateCommands(HazeCrates plugin, KeyActions keys) {
        this.plugin = plugin;
        this.keys = keys;
        this.crates = new CrateListener(plugin);
    }

    @Default
    @Subcommand("help")
    @CatchUnknown
    public void onHelp(CommandSender sender) {
        sender.sendMessage(plugin.messages().parse("<dark_gray>─── <gold>HazeCrates</gold> <dark_gray>───</dark_gray>"));
        for (String line : HELP_LINES) {
            sender.sendMessage(plugin.messages().parse("<yellow>" + line + "</yellow>"));
        }
        sender.sendMessage(plugin.messages().parse("<yellow>/chiave</yellow> <gray>– saldo chiavi</gray>"));
    }

    @Subcommand("give")
    @Conditions("keyadmin")
    @CommandCompletion("@players @crates @amounts")
    @Syntax("<giocatore> <crate> [quantita]")
    public void onGive(CommandSender sender, OnlinePlayer target, CrateDefinition crate, @Default("1") int amount) {
        keys.give(sender, target.getPlayer(), crate, amount);
    }

    @Subcommand("take")
    @Conditions("keyadmin")
    @CommandCompletion("@players @crates @amounts")
    @Syntax("<giocatore> <crate> [quantita]")
    public void onTake(CommandSender sender, OnlinePlayer target, CrateDefinition crate, @Default("1") int amount) {
        keys.take(sender, target.getPlayer(), crate, amount);
    }

    @Subcommand("set")
    @Conditions("keyadmin")
    @CommandCompletion("@players @crates @amounts")
    @Syntax("<giocatore> <crate> <quantita>")
    public void onSet(CommandSender sender, OnlinePlayer target, CrateDefinition crate, int amount) {
        keys.set(sender, target.getPlayer(), crate, amount);
    }

    @Subcommand("lootbox")
    @Conditions("keyadmin")
    @CommandCompletion("@players @crates @amounts")
    @Syntax("<giocatore> <crate> [quantita]")
    public void onLootbox(CommandSender sender, OnlinePlayer target, CrateDefinition crate, @Default("1") int amount) {
        keys.giveLootbox(sender, target.getPlayer(), crate, amount);
    }

    @Subcommand("item")
    @Conditions("itemadmin")
    @CommandCompletion("@crates @amounts")
    @Syntax("<crate> [quantita]")
    public void onItem(Player player, CrateDefinition crate, @Default("1") int amount) {
        ItemStack item = crates.buildCrateItem(crate);
        item.setAmount(Math.min(64, Math.max(1, amount)));
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        plugin.messages().send(player, "item-given", Map.of("crate", crate.displayName()));
    }

    @Subcommand("place")
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@crates")
    @Syntax("<crate>")
    public void onPlace(Player player, CrateDefinition crate) {
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            plugin.messages().send(player, "invalid-target");
            return;
        }
        plugin.placements().place(target.getLocation(), crate);
        if (plugin.externalPluginsReady()) {
            plugin.display().stopAt(target.getLocation());
            plugin.display().startAt(CratePlacementService.key(target.getLocation()), target.getLocation(), crate);
        }
        plugin.messages().send(player, "created");
    }

    @Subcommand("break")
    @CommandPermission("hazecrates.admin")
    public void onBreak(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !crates.breakCrateAt(target, player)) {
            plugin.messages().send(player, "invalid-target");
        }
    }

    @Subcommand("preview")
    @CommandCompletion("@crates")
    @Syntax("<crate>")
    public void onPreview(Player player, CrateDefinition crate) {
        PreviewInventory.open(player, crate, plugin);
    }

    @Subcommand("preview")
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@players @crates")
    @Syntax("<giocatore> <crate>")
    public void onPreviewOther(CommandSender sender, OnlinePlayer target, CrateDefinition crate) {
        PreviewInventory.open(target.getPlayer(), crate, plugin);
    }

    @Subcommand("open")
    @CommandCompletion("@crates")
    @Syntax("<crate>")
    public void onOpen(Player player, CrateDefinition crate) {
        crates.tryOpen(player, crate, openLocation(player, crate), false);
    }

    @Subcommand("open")
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@players @crates")
    @Syntax("<giocatore> <crate>")
    public void onOpenOther(CommandSender sender, OnlinePlayer target, CrateDefinition crate) {
        Player receiver = target.getPlayer();
        crates.tryOpen(receiver, crate, openLocation(receiver, crate), false);
    }

    @Subcommand("openall|bulk|mass")
    @CommandCompletion("@crates")
    @Syntax("<crate>")
    public void onOpenAll(Player player, CrateDefinition crate) {
        crates.tryOpenAll(player, crate);
    }

    @Subcommand("openall|bulk|mass")
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@players @crates")
    @Syntax("<giocatore> <crate>")
    public void onOpenAllOther(CommandSender sender, OnlinePlayer target, CrateDefinition crate) {
        crates.tryOpenAll(target.getPlayer(), crate);
    }

    @Subcommand("stats")
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@players")
    @Syntax("[giocatore]")
    public void onStats(Player player, @Optional OnlinePlayer target) {
        Player shown = target == null ? player : target.getPlayer();
        player.sendMessage(plugin.messages().parse(
                "<gold><bold>Aperture di " + shown.getName() + "</bold></gold>"));
        plugin.crates().all().forEach(crate ->
                plugin.stats().opened(shown.getUniqueId(), crate.id())
                        .thenAccept(n -> Bukkit.getScheduler().runTask(plugin,
                                () -> player.sendMessage(plugin.messages().parse(
                                        "<gray>• </gray>" + crate.displayName()
                                                + "<gray>: </gray><yellow>" + n + "</yellow>")))));
    }

    @Subcommand("reload")
    @CommandPermission("hazecrates.admin")
    public void onReload(CommandSender sender) {
        plugin.reloadPlugin();
        if (sender instanceof Player player) {
            plugin.messages().send(player, "reload");
        } else {
            sender.sendMessage("HazeCrates ricaricato.");
        }
    }

    static Location openLocation(Player player, CrateDefinition crate) {
        if (crate.keyType() == KeyType.LOOTBOX) {
            return player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.8));
        }
        Block target = player.getTargetBlockExact(6);
        if (target != null) {
            return target.getLocation().add(0.5, 1.0, 0.5);
        }
        return player.getLocation();
    }
}
