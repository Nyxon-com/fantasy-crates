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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Icona visiva per l'animazione. Il premio vero resta l'item completo.
 * Qui restano modello, texture, colore e nome: niente lore o NBT MMOItems.
 */
public final class DisplayIcon {

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
        if (cached == null) {
            cached = build(source);
            CACHE.put(source, cached);
        }
        return cached.clone();
    }

    private static ItemStack build(ItemStack source) {
        ItemStack copy = new ItemStack(source.getType(), 1);
        ItemMeta from = source.getItemMeta();
        ItemMeta meta = copy.getItemMeta();
        if (from == null || meta == null) {
            return copy;
        }
        Component name = from.displayName();
        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false).hoverEvent(null).clickEvent(null));
        } else {
            meta.displayName(plainName(source, from));
        }
        meta.lore(null);
        copyLook(from, meta);
        copy.setItemMeta(meta);
        return copy;
    }

    private static void copyLook(ItemMeta from, ItemMeta to) {
        copyItemModel(from, to);
        copyCustomModel(from, to);
        if (from instanceof SkullMeta skull && to instanceof SkullMeta target) {
            if (skull.getOwnerProfile() != null) {
                target.setOwnerProfile(skull.getOwnerProfile());
            } else if (skull.getPlayerProfile() != null) {
                target.setPlayerProfile(skull.getPlayerProfile());
            }
        }
        if (from instanceof LeatherArmorMeta leather && to instanceof LeatherArmorMeta target) {
            target.setColor(leather.getColor());
        }
        if (from.hasEnchantmentGlintOverride() && from.getEnchantmentGlintOverride() != null) {
            to.setEnchantmentGlintOverride(from.getEnchantmentGlintOverride());
        } else if (!from.getEnchants().isEmpty()) {
            to.setEnchantmentGlintOverride(true);
        }
        to.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        for (ItemFlag flag : from.getItemFlags()) {
            to.addItemFlags(flag);
        }
    }

    private static void copyItemModel(ItemMeta from, ItemMeta to) {
        try {
            Method has = ItemMeta.class.getMethod("hasItemModel");
            if (!Boolean.TRUE.equals(has.invoke(from))) {
                return;
            }
            Method getter = ItemMeta.class.getMethod("getItemModel");
            Object model = getter.invoke(from);
            if (model == null) {
                return;
            }
            Method setter = ItemMeta.class.getMethod("setItemModel", getter.getReturnType());
            setter.invoke(to, model);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void copyCustomModel(ItemMeta from, ItemMeta to) {
        try {
            Method getter = ItemMeta.class.getMethod("getCustomModelDataComponent");
            Object component = getter.invoke(from);
            if (component != null && !emptyCustomModel(component)) {
                Method setter = ItemMeta.class.getMethod("setCustomModelDataComponent", getter.getReturnType());
                setter.invoke(to, component);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (from.hasCustomModelData()) {
            to.setCustomModelData(from.getCustomModelData());
        }
    }

    private static boolean emptyCustomModel(Object component) {
        try {
            for (String name : new String[]{"getFloats", "getFlags", "getStrings", "getColors"}) {
                Object value = component.getClass().getMethod(name).invoke(component);
                if (value instanceof java.util.Collection<?> list && !list.isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
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
}
