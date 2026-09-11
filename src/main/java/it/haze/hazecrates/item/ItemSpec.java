// made by haze
package it.haze.hazecrates.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public record ItemSpec(ItemProvider provider, String id, int customModelData) {

    public static ItemSpec vanilla(String material) {
        return new ItemSpec(ItemProvider.VANILLA, material, 0);
    }

    public static ItemSpec vanilla(String material, int cmd) {
        return new ItemSpec(ItemProvider.VANILLA, material, cmd);
    }

    public static ItemSpec mmoitems(String typeAndId) {
        return new ItemSpec(ItemProvider.MMOITEMS, typeAndId, 0);
    }

    public static ItemSpec itemsadder(String namespaceId) {
        return new ItemSpec(ItemProvider.ITEMSADDER, namespaceId, 0);
    }

    public static ItemSpec nexo(String itemId) {
        return new ItemSpec(ItemProvider.NEXO, itemId, 0);
    }

    public static ItemSpec fromBaseStack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return vanilla("STONE");
        }
        int cmd = 0;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            cmd = meta.getCustomModelData();
        }
        return vanilla(item.getType().name(), cmd);
    }

    public static ItemSpec parse(String raw) {
        if (raw == null || raw.isBlank()) return vanilla("STONE");
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("mmoitems:")) {
            return mmoitems(raw.substring("mmoitems:".length()));
        }
        if (lower.startsWith("itemsadder:")) {
            return itemsadder(raw.substring("itemsadder:".length()));
        }
        if (lower.startsWith("nexo:")) {
            return nexo(raw.substring("nexo:".length()));
        }
        return vanilla(raw);
    }

    public String serialize() {
        return switch (provider) {
            case MMOITEMS   -> "mmoitems:"   + id;
            case ITEMSADDER -> "itemsadder:" + id;
            case NEXO       -> "nexo:"       + id;
            case VANILLA    -> id;
        };
    }
}
