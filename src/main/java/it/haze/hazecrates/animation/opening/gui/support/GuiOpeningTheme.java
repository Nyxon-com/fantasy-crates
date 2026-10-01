// made by haze
package it.haze.hazecrates.animation.opening.gui.support;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import it.haze.hazecrates.config.MiniMessageService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiOpeningTheme {

    public static final Material FILL = Material.BLACK_STAINED_GLASS_PANE;
    public static final Material BORDER = Material.BLACK_STAINED_GLASS_PANE;
    public static final Material ACCENT = Material.YELLOW_STAINED_GLASS_PANE;
    public static final Material HIGHLIGHT = Material.LIME_STAINED_GLASS_PANE;

    private static final Component OPENING_TITLE = title("<gold>Apertura</gold>");
    private static final ItemStack FILL_ITEM = pane(FILL);
    private static final ItemStack POINTER_ITEM = pane(ACCENT);
    private static final ItemStack WIN_POINTER_ITEM = pane(HIGHLIGHT);

    private GuiOpeningTheme() {}

    public static Component title(String miniMessage) {
        return MiniMessageService.shared().parse(miniMessage);
    }

    public static Component openingTitle() {
        return OPENING_TITLE;
    }

    public static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack fill() {
        return FILL_ITEM.clone();
    }

    public static ItemStack pointer() {
        return POINTER_ITEM.clone();
    }

    public static ItemStack winPointer() {
        return WIN_POINTER_ITEM.clone();
    }

    public static void fillAll(Inventory inventory) {
        ItemStack fill = FILL_ITEM;
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fill);
        }
    }

    public static void show(Inventory inventory, int slot, ItemStack item) {
        ItemStack current = inventory.getItem(slot);
        if (current != null && current.isSimilar(item)) {
            return;
        }
        inventory.setItem(slot, item);
    }

    public static ItemStack glow(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
