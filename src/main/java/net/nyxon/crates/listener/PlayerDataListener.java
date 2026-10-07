// made by nyxon
package net.nyxon.crates.listener;

import net.nyxon.crates.NyxonCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {

    private final NyxonCrates plugin;

    public PlayerDataListener(NyxonCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.playerData().load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.playerData().unload(event.getPlayer().getUniqueId());
    }
}
