package it.haze.hazecrates.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Copia visiva per le animazioni. Il premio vero resta l'item completo.
 * Senza lore, attributi e NBT di MMOItems il pacchetto non blocca il client.
 * La copia è calcolata una volta: rileggere il NBT a ogni frame blocca il tick.
 */
public final class DisplayIcon {

    private static final Map<ItemStack, ItemStack> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    private DisplayIcon() {}

    public static void clear() {
        CACHE.clear();
    }

    public static ItemStack light(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return new ItemStack(Material.PAPER);
        }
        ItemStack cached = CACHE.get(source);
        if (cached != null) {
            return cached.clone();
        }
        ItemStack copy = build(source);
        CACHE.put(source, copy);
        return copy.clone();
    }

    private static ItemStack build(ItemStack source) {
        ItemStack copy = new ItemStack(source.getType(), 1);
        ItemMeta from = source.getItemMeta();
        if (from == null) {
            return copy;
        }
        ItemMeta meta = copy.getItemMeta();
        if (from.hasDisplayName() && from.displayName() != null) {
            meta.displayName(from.displayName());
        }
        if (from.hasCustomModelData()) {
            meta.setCustomModelData(from.getCustomModelData());
        }
        copyItemModel(from, meta);
        if (from instanceof SkullMeta skull && meta instanceof SkullMeta target && skull.getPlayerProfile() != null) {
            target.setPlayerProfile(skull.getPlayerProfile());
        }
        if (from instanceof LeatherArmorMeta leather && meta instanceof LeatherArmorMeta target) {
            target.setColor(leather.getColor());
        }
        if (from.hasEnchantmentGlintOverride() && from.getEnchantmentGlintOverride() != null) {
            meta.setEnchantmentGlintOverride(from.getEnchantmentGlintOverride());
        } else if (!from.getEnchants().isEmpty()) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        copy.setItemMeta(meta);
        return copy;
    }

    private static void copyItemModel(ItemMeta from, ItemMeta to) {
        try {
            var has = ItemMeta.class.getMethod("hasItemModel");
            if (!Boolean.TRUE.equals(has.invoke(from))) {
                return;
            }
            Object key = ItemMeta.class.getMethod("getItemModel").invoke(from);
            if (key == null) {
                return;
            }
            ItemMeta.class.getMethod("setItemModel", key.getClass()).invoke(to, key);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
