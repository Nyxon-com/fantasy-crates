// made by haze
package it.haze.hazecrates.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import it.haze.hazecrates.crate.CrateDefinition;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("chiave|chiavi|keys")
@Description("Saldo chiavi")
public final class KeyCommands extends BaseCommand {

    private final KeyActions keys;

    public KeyCommands(KeyActions keys) {
        this.keys = keys;
    }

    @Default
    @CommandCompletion("@crates")
    @Syntax("[crate]")
    public void onBalance(Player player, @Optional CrateDefinition crate) {
        if (crate == null) {
            keys.list(player);
        } else {
            keys.show(player, crate);
        }
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
}
