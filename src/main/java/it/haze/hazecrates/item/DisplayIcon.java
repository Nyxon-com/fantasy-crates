package it.haze.hazecrates.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Icona mostrata in crate e animazione: è l'item vero, non una copia di cuoio.
 */
public final class DisplayIcon {

    private static final Map<ItemStack, Component> NAMES = Collections.synchronizedMap(new IdentityHashMap<>());

    private DisplayIcon() {}

    public static void clear() {
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
        return source.clone();
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
