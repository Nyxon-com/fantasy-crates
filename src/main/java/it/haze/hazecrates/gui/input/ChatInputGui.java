// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ChatInputGui {

    private static final long TIMEOUT_TICKS = 20L * 60;

    private ChatInputGui() {}

    public static void open(Player player, HazeCrates plugin,
                            String fieldLabel, String defaultValue,
                            List<String> suggestions,
                            Consumer<String> callback) {
        player.closeInventory();

        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.guiManager().hasPendingInput(player)) return;
            plugin.guiManager().removePendingInput(player);
            plugin.messages().send(player, "editor-input-timeout");
            reopenEditor(player, plugin);
        }, TIMEOUT_TICKS);

        plugin.guiManager().setPendingInput(player, new GuiManager.PendingInput(
                callback, defaultValue == null ? "" : defaultValue, timeout));

        String shown = defaultValue == null || defaultValue.isBlank() ? "(empty)" : defaultValue;
        String prompt = plugin.messages().raw("editor-input-prompt", Map.of(
                "field", fieldLabel, "current", shown));
        if (prompt.isBlank()) {
            prompt = "<gray>Editing <aqua>" + fieldLabel
                    + "</aqua> — current: <white>" + shown + "</white></gray>";
        }
        player.sendMessage(plugin.messages().prefix().append(plugin.messages().parse(prompt)));

        if (suggestions != null && !suggestions.isEmpty()) {
            String joined = String.join("<dark_gray>, </dark_gray><aqua>", suggestions);
            String sug = plugin.messages().raw("editor-input-suggestions", Map.of(
                    "suggestions", "<aqua>" + joined + "</aqua>"));
            if (sug.isBlank()) {
                sug = "<dark_gray>Suggestions: <aqua>" + String.join("</aqua><dark_gray>, </dark_gray><aqua>", suggestions)
                        + "</aqua></dark_gray>";
            }
            player.sendMessage(plugin.messages().prefix().append(plugin.messages().parse(sug)));
        }

        String hint = plugin.messages().raw("editor-input-hint");
        if (hint.isBlank()) {
            hint = "<dark_gray>Type the new value in chat · <yellow>cancel</yellow> to abort · <yellow>default</yellow> to keep</dark_gray>";
        }
        player.sendMessage(plugin.messages().prefix().append(plugin.messages().parse(hint)));
    }

    public static void open(Player player, HazeCrates plugin,
                            String fieldLabel, String defaultValue,
                            Consumer<String> callback) {
        open(player, plugin, fieldLabel, defaultValue, List.of(), callback);
    }

    public static void reopenEditor(Player player, HazeCrates plugin) {
        CrateEditorSession session = plugin.guiManager().session(player);
        if (session == null) return;
        switch (session.currentPage()) {
            case CRATE_LIST    -> CrateListGui.open(player, plugin);
            case CRATE_EDITOR  -> CrateEditorGui.open(player, plugin, session);
            case REWARD_LIST   -> RewardListGui.open(player, plugin, session);
            case REWARD_EDITOR -> RewardEditorGui.open(player, plugin, session);
        }
    }
}
