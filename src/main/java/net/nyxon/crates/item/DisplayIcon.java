package net.nyxon.crates.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.Locale;

/**
 * Icona visiva per l'animazione. Il premio vero resta l'item completo.
 * Qui restano modello, texture, colore e nome: niente lore o NBT MMOItems.
 */
public final class DisplayIcon {

    private DisplayIcon() {}

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
        return build(source);
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
        if (from.hasItemModel()) {
            to.setItemModel(from.getItemModel());
        }
    }

    private static void copyCustomModel(ItemMeta from, ItemMeta to) {
        if (from.hasCustomModelDataComponent()) {
            CustomModelDataComponent component = from.getCustomModelDataComponent();
            if (!emptyCustomModel(component)) {
                to.setCustomModelDataComponent(component);
                return;
            }
        }
        if (from.hasCustomModelData()) {
            to.setCustomModelData(from.getCustomModelData());
        }
    }

    private static boolean emptyCustomModel(CustomModelDataComponent component) {
        return component.getFloats().isEmpty() && component.getFlags().isEmpty()
                && component.getStrings().isEmpty() && component.getColors().isEmpty();
    }

    private static Component plainName(ItemStack source) {
        return plainName(source, null);
    }

    private static Component plainName(ItemStack source, ItemMeta known) {
        ItemMeta from = known != null ? known : source.getItemMeta();
        String text = readPlain(source, from);
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
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
