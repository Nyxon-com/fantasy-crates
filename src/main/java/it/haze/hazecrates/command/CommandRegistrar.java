// made by haze
package it.haze.hazecrates.command;

import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registra i comandi su ACF: contesti, completamenti e permessi in un posto solo. */
public final class CommandRegistrar {

    private static final List<String> AMOUNTS = List.of("1", "5", "10", "16", "32", "64");

    private final HazeCrates plugin;
    private PaperCommandManager manager;

    public CommandRegistrar(HazeCrates plugin) {
        this.plugin = plugin;
    }

    public void register() {
        manager = new PaperCommandManager(plugin);
        manager.usePerIssuerLocale(false);
        manager.getLocales().setDefaultLocale(Locale.ITALIAN);

        registerContexts();
        registerCompletions();
        registerConditions();

        KeyActions keys = new KeyActions(plugin);
        manager.registerCommand(new CrateCommands(plugin, keys));
        manager.registerCommand(new OpenCommand(plugin));
        manager.registerCommand(new KeyCommands(keys));
        manager.registerCommand(new EditorCommands(plugin));
    }

    public void unregister() {
        if (manager != null) {
            manager.unregisterCommands();
        }
    }

    private void registerContexts() {
        manager.getCommandContexts().registerContext(CrateDefinition.class, context -> {
            String input = context.popFirstArg();
            CrateDefinition crate = plugin.crates().find(input);
            if (crate == null) {
                throw new InvalidCommandArgument(
                        CommandSupport.plain(plugin, "unknown-crate", Map.of("crate", input)), false);
            }
            return crate;
        });
    }

    private void registerCompletions() {
        manager.getCommandCompletions().registerAsyncCompletion("crates", context -> plugin.crates().tabIds());
        manager.getCommandCompletions().registerStaticCompletion("amounts", AMOUNTS);
    }

    private void registerConditions() {
        manager.getCommandConditions().addCondition("keyadmin", context -> {
            CommandSender sender = context.getIssuer().getIssuer();
            if (!sender.hasPermission("hazecrates.admin") && !sender.hasPermission("hazecrates.key")) {
                throw new ConditionFailedException(CommandSupport.plain(plugin, "no-permission", Map.of()));
            }
        });
        manager.getCommandConditions().addCondition("itemadmin", context -> {
            CommandSender sender = context.getIssuer().getIssuer();
            if (!sender.hasPermission("hazecrates.admin") && !sender.hasPermission("hazecrates.item")) {
                throw new ConditionFailedException(CommandSupport.plain(plugin, "no-permission", Map.of()));
            }
        });
    }
}
