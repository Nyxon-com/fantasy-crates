// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class GuiManager {

    public record PendingInput(Consumer<String> callback, String defaultValue, BukkitTask timeout) {}

    private final HazeCrates plugin;
    private final Map<UUID, CrateEditorSession> sessions = new HashMap<>();
    private final Map<UUID, PendingInput> pendingInput = new HashMap<>();

    public GuiManager(HazeCrates plugin) { this.plugin = plugin; }

    public CrateEditorSession getOrCreate(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), k -> new CrateEditorSession(player));
    }

    public CrateEditorSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void setPendingInput(Player player, PendingInput input) {
        PendingInput previous = pendingInput.put(player.getUniqueId(), input);
        if (previous != null && previous.timeout() != null) previous.timeout().cancel();
    }

    public PendingInput takePendingInput(Player player) {
        PendingInput input = pendingInput.remove(player.getUniqueId());
        if (input != null && input.timeout() != null) input.timeout().cancel();
        return input;
    }

    public boolean hasPendingInput(Player player) {
        return pendingInput.containsKey(player.getUniqueId());
    }

    public void removePendingInput(Player player) {
        PendingInput input = pendingInput.remove(player.getUniqueId());
        if (input != null && input.timeout() != null) input.timeout().cancel();
    }
}
