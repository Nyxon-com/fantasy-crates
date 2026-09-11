// made by haze
package it.haze.hazecrates.animation.opening.gui.support;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private GuiOpeningTheme() {}

    public static Component title(String miniMessage) {
        return MINI.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public static Component openingTitle() {
        return title("<gold>Apertura</gold>");
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
        return pane(FILL);
    }

    public static ItemStack pointer() {
        return pane(ACCENT);
    }

    public static ItemStack winPointer() {
        return pane(HIGHLIGHT);
    }

    public static void fillAll(Inventory inventory) {
        ItemStack fill = fill();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fill);
        }
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
