// made by nyxon
package net.nyxon.crates.item;

import net.nyxon.crates.NyxonCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class NexoItemsLoadedListener implements Listener {

    private final NyxonCrates plugin;

    public NexoItemsLoadedListener(NyxonCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNexoItemsLoaded(com.nexomc.nexo.api.events.NexoItemsLoadedEvent event) {
        plugin.onNexoItemsLoaded();
    }
}
