// made by haze
package it.haze.hazecrates.item;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

public final class ExternalItemReloadListener implements Listener {

    private final HazeCrates plugin;

    public ExternalItemReloadListener(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMmoItemsReload(net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent event) {
        plugin.scheduleExternalItemRefresh("MMOItems reloaded", 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isMmoItemsReload(event.getMessage())) {
            plugin.scheduleExternalItemRefresh("MMOItems reload command", 25L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (isMmoItemsReload(event.getCommand())) {
            plugin.scheduleExternalItemRefresh("MMOItems reload command", 25L);
        }
    }

    private static boolean isMmoItemsReload(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String cmd = raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        cmd = cmd.toLowerCase(Locale.ROOT);
        return cmd.equals("mi reload")
                || cmd.startsWith("mi reload ")
                || cmd.equals("mmoitems reload")
                || cmd.startsWith("mmoitems reload ");
    }
}
