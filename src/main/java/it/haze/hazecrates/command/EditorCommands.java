// made by haze
package it.haze.hazecrates.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.gui.CrateEditorGui;
import it.haze.hazecrates.gui.CrateEditorSession;
import it.haze.hazecrates.gui.CrateListGui;
import org.bukkit.entity.Player;

@CommandAlias("hc")
@CommandPermission("hazecrates.admin")
@Description("Editor crate")
public final class EditorCommands extends BaseCommand {

    private final HazeCrates plugin;

    public EditorCommands(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @Default
    @Subcommand("editor")
    @CommandCompletion("@crates")
    @Syntax("[crate]")
    public void onEditor(Player player, @Optional CrateDefinition crate) {
        CrateEditorSession session = plugin.guiManager().getOrCreate(player);
        if (crate == null) {
            session.currentPage(CrateEditorSession.Page.CRATE_LIST);
            CrateListGui.open(player, plugin);
            return;
        }
        session.loadFrom(crate);
        session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
        CrateEditorGui.open(player, plugin, session);
    }

    @Subcommand("reload")
    public void onReload(Player player) {
        plugin.reloadPlugin();
        plugin.messages().send(player, "reload");
    }
}
