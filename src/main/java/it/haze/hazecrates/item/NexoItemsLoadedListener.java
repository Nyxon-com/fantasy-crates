// made by haze
package it.haze.hazecrates.item;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class NexoItemsLoadedListener implements Listener {

    private final HazeCrates plugin;

    public NexoItemsLoadedListener(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNexoItemsLoaded(com.nexomc.nexo.api.events.NexoItemsLoadedEvent event) {
        plugin.onNexoItemsLoaded();
    }
}
