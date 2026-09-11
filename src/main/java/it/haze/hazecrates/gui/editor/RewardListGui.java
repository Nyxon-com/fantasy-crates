// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.RewardDefinition;
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

public final class RewardListGui {

    public static final String TAG = "Premi:";

    static final int[] INNER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    static final int SLOT_BACK = 45;
    static final int SLOT_ADD  = 49;

    private RewardListGui() {}

    public static void open(Player player, HazeCrates plugin, CrateEditorSession session) {
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.messages().parse("<gold>Premi: </gold>" + session.crateId()));

        fillBorder(inv);

        List<RewardDefinition> sorted = new ArrayList<>(session.rewards());
        sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));

        int totalWeight = sorted.stream().mapToInt(r -> r.weight()).sum();

        for (int i = 0; i < sorted.size() && i < INNER.length; i++) {
            RewardDefinition r = sorted.get(i);
            int origIdx = session.rewards().indexOf(r);
            inv.setItem(INNER[i], buildIcon(r, origIdx, totalWeight));
        }

        inv.setItem(SLOT_BACK, back());
        inv.setItem(SLOT_ADD, add("Aggiungi premio"));

        inv.setItem(50, of(org.bukkit.Material.BOOK,
                "&7Statistiche premi",
                "&8Premi totali: &f" + sorted.size(),
                "&8Peso totale:  &f" + totalWeight,
                "",
                "&7Click destro su un premio per &crimuoverlo"));

        fillAll(inv);
        player.openInventory(inv);
    }

    private static ItemStack buildIcon(RewardDefinition r, int origIdx, int totalWeight) {
        ItemStack item = r.icon().clone();
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = meta.lore() == null
                ? new ArrayList<>()
                : new ArrayList<>(meta.lore());

        lore.add(legacy(""));
        lore.add(legacy("&8┌ &7ID: &f" + r.id()));
        lore.add(legacy("&8├ &7Weight: &f" + r.weight()
                + " &8→ " + probabilityColour(r.weight(), totalWeight)));
        lore.add(legacy("&8├ &7Rarity: " + rarityLabel(r.weight(), totalWeight)));
        lore.add(legacy("&8├ &7Provider: &f" + providerLabel(r.itemSpec())));
        lore.add(legacy("&8├ &7Commands: &f" + r.commands().size()));
        if (!r.permission().isBlank())
            lore.add(legacy("&8├ &7Permission: &f" + r.permission()));
        if (r.broadcast())
            lore.add(legacy("&8├ &7Broadcast: &aYes"));
        lore.add(legacy("&8└ &8Index: &7#" + origIdx));
        lore.add(legacy(""));
        lore.add(legacy("&e▶ &fLeft-click to edit"));
        lore.add(legacy("&c▶ &fRight-click to remove"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String providerLabel(it.haze.hazecrates.item.ItemSpec spec) {
        if (spec == null) return "vanilla";
        return switch (spec.provider()) {
            case MMOITEMS   -> "&dMMOItems";
            case ITEMSADDER -> "&bItemsAdder";
            case NEXO       -> "&aNexo";
            case VANILLA    -> "&7Vanilla";
        };
    }

    public static int innerIndexOf(int rawSlot) {
        for (int i = 0; i < INNER.length; i++) if (INNER[i] == rawSlot) return i;
        return -1;
    }
}
