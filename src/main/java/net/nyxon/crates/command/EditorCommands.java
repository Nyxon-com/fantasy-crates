// made by nyxon
package net.nyxon.crates.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.gui.CrateEditorGui;
import net.nyxon.crates.gui.CrateEditorSession;
import net.nyxon.crates.gui.CrateListGui;
import org.bukkit.entity.Player;

@CommandAlias("nc|nyxoncrates")
@CommandPermission("nyxoncrates.admin")
@Description("Editor crate")
public final class EditorCommands extends BaseCommand {

    private final NyxonCrates plugin;

    public EditorCommands(NyxonCrates plugin) {
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
