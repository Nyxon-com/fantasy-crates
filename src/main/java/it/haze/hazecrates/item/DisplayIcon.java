package it.haze.hazecrates.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Icona da mandare al client durante l'apertura.
 * Il premio vero resta l'item completo, dato a fine animazione.
 * Il nome è solo testo: i component MMOItems portano dietro l'item intero
 * e, rinviati a ogni frame, riempiono la coda di rete finché il ping sale.
 */
public final class DisplayIcon {

    private static final int MAX_BYTES = 1024;
    private static final Map<ItemStack, ItemStack> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, Component> NAMES = Collections.synchronizedMap(new IdentityHashMap<>());

    private DisplayIcon() {}

    public static void clear() {
        CACHE.clear();
        NAMES.clear();
    }

    public static Component visibleName(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return Component.text("?");
        }
        return plainName(source);
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
        ItemMeta from = source.getItemMeta();
        Component name = plainName(source, from);
        ItemStack copy = withLook(source.getType(), from, name);
        if (payload(copy) > MAX_BYTES) {
            copy = named(source.getType(), name);
        }
        return copy;
    }

    private static Component plainName(ItemStack source) {
        return plainName(source, null);
    }

    private static Component plainName(ItemStack source, ItemMeta known) {
        Component cached = NAMES.get(source);
        if (cached != null) {
            return cached;
        }
        ItemMeta from = known != null ? known : source.getItemMeta();
        String text = readPlain(source, from);
        Component name = Component.text(text).decoration(TextDecoration.ITALIC, false);
        NAMES.put(source, name);
        return name;
    }

    private static String readPlain(ItemStack source, ItemMeta from) {
        if (from != null && from.hasDisplayName()) {
            Component display = from.displayName();
            if (display != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(display).trim();
                if (!plain.isEmpty()) {
                    return plain.length() > 48 ? plain.substring(0, 48) : plain;
                }
            }
        }
        return source.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static ItemStack named(Material type, Component name) {
        ItemStack copy = new ItemStack(type, 1);
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.displayName(name);
        meta.lore(null);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        copy.setItemMeta(meta);
        return copy;
    }

    private static ItemStack withLook(Material type, ItemMeta from, Component name) {
        ItemStack copy = named(type, name);
        if (from == null) {
            return copy;
        }
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
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
        copy.setItemMeta(meta);
        return copy;
    }

    private static int payload(ItemStack item) {
        try {
            byte[] data = item.serializeAsBytes();
            return data == null ? 0 : data.length;
        } catch (Throwable ignored) {
            return 0;
        }
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
