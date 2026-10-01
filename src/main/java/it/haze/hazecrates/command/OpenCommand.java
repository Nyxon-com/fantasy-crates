// made by haze
package it.haze.hazecrates.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.listener.CrateListener;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("open")
@Description("Apri una crate se hai la chiave o il forziere")
public final class OpenCommand extends BaseCommand {

    private final CrateListener crates;

    public OpenCommand(HazeCrates plugin) {
        this.crates = new CrateListener(plugin);
    }

    @Default
    @CommandCompletion("@crates")
    @Syntax("<crate>")
    public void onOpen(Player player, CrateDefinition crate) {
        crates.tryOpen(player, crate, CrateCommands.openLocation(player, crate), false);
    }

    @Default
    @CommandPermission("hazecrates.admin")
    @CommandCompletion("@players @crates")
    @Syntax("<giocatore> <crate>")
    public void onOpenOther(CommandSender sender, OnlinePlayer target, CrateDefinition crate) {
        Player receiver = target.getPlayer();
        crates.tryOpen(receiver, crate, CrateCommands.openLocation(receiver, crate), false, false);
    }
}
