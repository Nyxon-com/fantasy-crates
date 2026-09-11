// made by haze
package it.haze.hazecrates.command;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.gui.CrateEditorGui;
import it.haze.hazecrates.gui.CrateEditorSession;
import it.haze.hazecrates.gui.CrateListGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class HcCommand implements CommandExecutor, TabCompleter {

    private final HazeCrates plugin;

    public HcCommand(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandSupport.usage(plugin, sender, "/hc editor [crate]");
            return true;
        }
        if (!player.hasPermission("hazecrates.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("editor")) {
            if (args.length > 2) {
                CommandSupport.usage(plugin, sender, "/hc editor [crate]");
                return true;
            }
            if (args.length == 2) {
                CrateDefinition crate = plugin.crates().find(args[1]);
                if (crate == null) {
                    plugin.messages().send(player, "unknown-crate", Map.of("crate", args[1]));
                    return true;
                }
                CrateEditorSession session = plugin.guiManager().getOrCreate(player);
                session.loadFrom(crate);
                session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
                CrateEditorGui.open(player, plugin, session);
            } else {
                CrateEditorSession session = plugin.guiManager().getOrCreate(player);
                session.currentPage(CrateEditorSession.Page.CRATE_LIST);
                CrateListGui.open(player, plugin);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length != 1) {
                CommandSupport.usage(plugin, sender, "/hc reload");
                return true;
            }
            plugin.reloadPlugin();
            plugin.messages().send(player, "reload");
            return true;
        }

        CommandSupport.usage(plugin, sender, "/hc editor [crate] | /hc reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filter(args[0], List.of("editor", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            return CommandSupport.filter(args[1], plugin.crates().tabIds());
        }
        return List.of();
    }
}
