// made by haze
package it.haze.hazecrates.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
            String converted = it.haze.hazecrates.config.MessageService.legacyToMini(name);
            try {
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(converted));
            } catch (Exception e) {
                meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
            }
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> {
                        String converted = it.haze.hazecrates.config.MessageService.legacyToMini(line);
                        try {
                            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(converted);
                        } catch (Exception e) {
                            return LegacyComponentSerializer.legacyAmpersand().deserialize(line);
                        }
                    })
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
