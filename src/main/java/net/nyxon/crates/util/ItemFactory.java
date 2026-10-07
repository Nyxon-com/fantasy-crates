// made by nyxon
package net.nyxon.crates.util;

import net.nyxon.crates.config.MiniMessageService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemFactory {

    private ItemFactory() { }

    public static ItemStack create(Material material, int amount, String name,
                                   List<String> lore, boolean glow) {
        return create(material, amount, name, lore, glow, 0);
    }

    public static ItemStack create(Material material, int amount, String name,
                                   List<String> lore, boolean glow, int customModelData) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (name != null) {
            meta.displayName(MiniMessageService.shared().parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> MiniMessageService.shared().parse(line))
                    .toList());
        }
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        item.setItemMeta(meta);
        return item;
    }
}
