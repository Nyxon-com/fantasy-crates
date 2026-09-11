// made by haze
package it.haze.hazecrates.gui.preview;

import it.haze.hazecrates.crate.CrateDefinition;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class PreviewHolder implements InventoryHolder {

    private final CrateDefinition crate;
    private final int page;
    private final int totalPages;
    private final Map<Integer, String> actions = new HashMap<>();
    private final Map<Integer, String> sounds  = new HashMap<>();

    public PreviewHolder(CrateDefinition crate) {
        this(crate, 1, 1);
    }

    public PreviewHolder(CrateDefinition crate, int page, int totalPages) {
        this.crate = crate;
        this.page = Math.max(1, page);
        this.totalPages = Math.max(1, totalPages);
    }

    public CrateDefinition crate() { return crate; }
    public int page()              { return page; }
    public int totalPages()        { return totalPages; }

    public void registerAction(int slot, String action) {
        if (action != null && !action.isBlank()) {
            actions.put(slot, action);
        }
    }

    public void registerSound(int slot, String sound) {
        if (sound != null && !sound.isBlank()) {
            sounds.put(slot, sound);
        }
    }

    public String actionAt(int slot) {
        return actions.get(slot);
    }

    public String soundAt(int slot) {
        return sounds.get(slot);
    }

    @Override
    public Inventory getInventory() { return null; }
}
