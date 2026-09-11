// made by haze
package it.haze.hazecrates.gui.preview;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PreviewTheme {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private PreviewTheme() {}

    public static void paintVault(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack frame = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack empty = socket();
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inv.setItem(slot, border ? frame.clone() : empty.clone());
        }
    }

    public static ItemStack socket() {
        return pane(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack pane(Material material, String miniName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(miniName));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static Component title(String miniMessage) {
        return MINI.deserialize(miniMessage == null || miniMessage.isBlank() ? "<dark_gray>" : miniMessage)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String vaultTitle(String plainName) {
        return plainName == null || plainName.isBlank() ? "Crate" : plainName;
    }

    public static boolean genericTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String lower = title.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("anteprima") || lower.contains("preview") || title.contains("»")
                || lower.contains("reliquiario");
    }

    public static void silence(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.hasDisplayName() && meta.displayName() != null) {
            meta.displayName(meta.displayName().decoration(TextDecoration.ITALIC, false));
        }
        if (meta.hasLore() && meta.lore() != null) {
            meta.lore(meta.lore().stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
    }
}
