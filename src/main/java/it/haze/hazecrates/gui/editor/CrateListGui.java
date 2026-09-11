// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static it.haze.hazecrates.gui.GuiItems.*;

public final class CrateListGui {

    public static final String TAG = "Crate Manager";

    private static final int[] INNER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private CrateListGui() {}

    public static void open(Player player, HazeCrates plugin) {
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.messages().parse("<gold>Crate Manager</gold>"));

        fillBorder(inv);

        List<CrateDefinition> sorted = new ArrayList<>(plugin.crates().all());
        sorted.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));

        for (int i = 0; i < sorted.size() && i < INNER.length; i++) {
            inv.setItem(INNER[i], icon(sorted.get(i)));
        }

        inv.setItem(49, add("Crea nuova crate"));
        fillAll(inv);
        player.openInventory(inv);
    }

    private static ItemStack icon(CrateDefinition crate) {
        org.bukkit.Material mat;
        try {
            mat = org.bukkit.Material.valueOf(
                    crate.display().blockSpec().id().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mat = org.bukkit.Material.CHEST;
        }

        int totalWeight = crate.rewards().stream().mapToInt(r -> r.weight()).sum();

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy(crate.displayName()));
        meta.lore(List.of(
                legacy("&8┌ &7ID: &f" + crate.id()),
                legacy("&8├ &7Key type: &f" + crate.keyType().name()),
                legacy("&8├ &7Animation: &f" + crate.animation()),
                legacy("&8├ &7Rewards: &f" + crate.rewards().size()
                        + " &8(total weight: &7" + totalWeight + "&8)"),
                legacy("&8├ &7Milestones: &f" + crate.milestones().size()),
                legacy("&8└ &7Block: &f" + crate.display().blockSpec().serialize()),
                legacy(""),
                legacy("&e▶ &fLeft-click to edit")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static int innerIndexOf(int rawSlot) {
        for (int i = 0; i < INNER.length; i++) if (INNER[i] == rawSlot) return i;
        return -1;
    }

    public static int[] innerSlots() { return INNER; }
}
