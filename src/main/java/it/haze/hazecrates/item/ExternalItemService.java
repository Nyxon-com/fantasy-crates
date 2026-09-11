// made by haze
package it.haze.hazecrates.item;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public final class ExternalItemService {

    private final HazeCrates plugin;
    private final boolean mmoitemsEnabled;
    private final boolean itemsadderEnabled;
    private final boolean nexoEnabled;

    public ExternalItemService(HazeCrates plugin) {
        this.plugin = plugin;
        this.mmoitemsEnabled  = plugin.getServer().getPluginManager().isPluginEnabled("MMOItems");
        this.itemsadderEnabled = plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder");
        this.nexoEnabled = plugin.getServer().getPluginManager().isPluginEnabled("Nexo");

        if (mmoitemsEnabled)   plugin.getLogger().info("[HazeCrates] MMOItems integration enabled.");
        if (itemsadderEnabled) plugin.getLogger().info("[HazeCrates] ItemsAdder integration enabled.");
        if (nexoEnabled)       plugin.getLogger().info("[HazeCrates] Nexo integration enabled.");
    }

    public ItemStack resolve(ItemSpec spec, int amount, String name,
                             List<String> lore, boolean glow) {
        ItemStack base = resolveBase(spec, amount);
        if (base == null) base = fallback(amount);
        applyMeta(base, name, lore, glow, spec.customModelData());
        return base;
    }

    public ItemStack resolve(ItemSpec spec) {
        return resolve(spec, 1, null, null, false);
    }

    public boolean isMmoitemsEnabled()   { return mmoitemsEnabled; }
    public boolean isItemsadderEnabled() { return itemsadderEnabled; }
    public boolean isNexoEnabled()       { return nexoEnabled; }

    public ItemStack resolveById(String raw, int amount) {
        if (raw == null || raw.isBlank()) return null;
        String id = stripNamespace(raw.trim());
        int qty = Math.max(1, amount);

        if (looksPrefixed(raw)) {
            ItemStack prefixed = resolveBase(ItemSpec.parse(raw.trim()), qty);
            if (isRealItem(prefixed)) return prefixed;
        }

        if (mmoitemsEnabled) {
            ItemStack mmo = resolveMmoItemsById(id, qty);
            if (isRealItem(mmo)) return mmo;
        }
        if (nexoEnabled) {
            ItemStack nexo = resolveNexo(ItemSpec.nexo(id), qty);
            if (isRealItem(nexo)) return nexo;
            ItemStack nexoLower = resolveNexo(ItemSpec.nexo(id.toLowerCase(Locale.ROOT)), qty);
            if (isRealItem(nexoLower)) return nexoLower;
        }
        if (itemsadderEnabled) {
            ItemStack ia = resolveItemsAdder(ItemSpec.itemsadder(id), qty);
            if (isRealItem(ia)) return ia;
        }

        Material mat = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        if (mat != null && !mat.isAir() && mat.isItem()) {
            return new ItemStack(mat, qty);
        }
        return null;
    }

    public ItemSpec identify(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ItemSpec.vanilla("STONE");
        }
        if (itemsadderEnabled) {
            try {
                var stack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
                if (stack != null && stack.getNamespacedID() != null && !stack.getNamespacedID().isBlank()) {
                    return ItemSpec.itemsadder(stack.getNamespacedID());
                }
            } catch (Exception ignored) {}
        }
        if (nexoEnabled) {
            try {
                String id = com.nexomc.nexo.api.NexoItems.idFromItem(item);
                if (id != null && !id.isBlank()) {
                    return ItemSpec.nexo(id);
                }
            } catch (Exception ignored) {}
        }
        if (mmoitemsEnabled) {
            try {
                Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
                Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, item);
                boolean hasType = Boolean.TRUE.equals(nbtClass.getMethod("hasType").invoke(nbt));
                if (hasType) {
                    String type = String.valueOf(nbtClass.getMethod("getType").invoke(nbt));
                    String id = String.valueOf(nbtClass.getMethod("getString", String.class)
                            .invoke(nbt, "MMOITEMS_ITEM_ID"));
                    if (type != null && !"null".equals(type) && id != null && !id.isBlank() && !"null".equals(id)) {
                        return ItemSpec.mmoitems(type + ":" + id);
                    }
                }
            } catch (Exception ignored) {}
        }
        return ItemSpec.fromBaseStack(item);
    }

    private ItemStack resolveBase(ItemSpec spec, int amount) {
        return switch (spec.provider()) {
            case VANILLA    -> resolveVanilla(spec, amount);
            case MMOITEMS   -> resolveMmoItems(spec, amount);
            case ITEMSADDER -> resolveItemsAdder(spec, amount);
            case NEXO       -> resolveNexo(spec, amount);
        };
    }

    private ItemStack resolveVanilla(ItemSpec spec, int amount) {
        Material mat = Material.matchMaterial(spec.id().toUpperCase(Locale.ROOT));
        if (mat == null) {
            plugin.getLogger().warning("[HazeCrates] Unknown material: " + spec.id() + " – falling back to STONE.");
            mat = Material.STONE;
        }
        return new ItemStack(mat, Math.max(1, amount));
    }

    private ItemStack resolveMmoItems(ItemSpec spec, int amount) {
        if (!mmoitemsEnabled) {
            plugin.getLogger().warning(
                    "[HazeCrates] MMOItems item '" + spec.id()
                    + "' requested but MMOItems is not installed – using fallback.");
            return fallback(amount);
        }
        try {
            String[] parts = spec.id().split(":", 2);
            if (parts.length != 2)
                throw new IllegalArgumentException("Expected TYPE:ID format, got: " + spec.id());

            net.Indyuce.mmoitems.api.Type type =
                    net.Indyuce.mmoitems.MMOItems.plugin.getTypes()
                            .get(parts[0].toUpperCase(Locale.ROOT));
            if (type == null)
                throw new IllegalArgumentException("Unknown MMOItems type: " + parts[0]);

            ItemStack item = net.Indyuce.mmoitems.MMOItems.plugin.getItem(type, parts[1]);
            if (item == null)
                throw new IllegalArgumentException("Unknown MMOItems item ID: " + parts[1]);

            item.setAmount(Math.max(1, amount));
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[HazeCrates] Could not resolve MMOItems item '" + spec.id()
                    + "': " + e.getMessage());
            return fallback(amount);
        }
    }

    private ItemStack resolveItemsAdder(ItemSpec spec, int amount) {
        if (!itemsadderEnabled) {
            plugin.getLogger().warning(
                    "[HazeCrates] ItemsAdder item '" + spec.id()
                    + "' requested but ItemsAdder is not installed – using fallback.");
            return fallback(amount);
        }
        try {
            dev.lone.itemsadder.api.CustomStack customStack =
                    dev.lone.itemsadder.api.CustomStack.getInstance(spec.id());
            if (customStack == null) {

                return fallback(amount);
            }
            ItemStack item = customStack.getItemStack().clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (Exception e) {

            plugin.getLogger().warning(
                    "[HazeCrates] Could not resolve ItemsAdder item '" + spec.id()
                    + "' (may not be loaded yet): " + e.getMessage());
            return fallback(amount);
        }
    }

    private ItemStack resolveNexo(ItemSpec spec, int amount) {
        if (!nexoEnabled) {
            plugin.getLogger().warning(
                    "[HazeCrates] Nexo item '" + spec.id()
                    + "' requested but Nexo is not installed – using fallback.");
            return fallback(amount);
        }
        try {
            String id = spec.id() == null ? "" : spec.id().trim();
            if (id.isBlank()) {
                throw new IllegalArgumentException("empty Nexo item id");
            }
            var builder = com.nexomc.nexo.api.NexoItems.itemFromId(id);
            if (builder == null) {
                throw new IllegalArgumentException("Unknown Nexo item ID: " + id);
            }
            ItemStack item = builder.build();
            if (item == null) {
                throw new IllegalArgumentException("Nexo item '" + id + "' built to null");
            }
            item = item.clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[HazeCrates] Could not resolve Nexo item '" + spec.id()
                    + "' (may not be loaded yet): " + e.getMessage());
            return fallback(amount);
        }
    }

    private void applyMeta(ItemStack item, String name, List<String> lore,
                           boolean glow, int customModelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (name != null && !name.isBlank()) {
            meta.displayName(plugin.messages().parse(name));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(l -> plugin.messages().parse(l))
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
    }

    private ItemStack resolveMmoItemsById(String id, int amount) {
        if (id.contains(":")) {
            ItemStack typed = resolveMmoItems(ItemSpec.mmoitems(id), amount);
            if (isRealItem(typed)) return typed;
        }
        try {
            for (net.Indyuce.mmoitems.api.Type type : net.Indyuce.mmoitems.MMOItems.plugin.getTypes().getAll()) {
                ItemStack item = net.Indyuce.mmoitems.MMOItems.plugin.getItem(type, id);
                if (item == null) {
                    item = net.Indyuce.mmoitems.MMOItems.plugin.getItem(type, id.toUpperCase(Locale.ROOT));
                }
                if (item != null && !item.getType().isAir()) {
                    item.setAmount(Math.max(1, amount));
                    return item;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean looksPrefixed(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.startsWith("mmoitems:") || lower.startsWith("itemsadder:") || lower.startsWith("nexo:");
    }

    private static String stripNamespace(String id) {
        if (id.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            return id.substring("minecraft:".length());
        }
        return id;
    }

    private static boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType() != Material.BARRIER;
    }

    private static ItemStack fallback(int amount) {
        return new ItemStack(Material.BARRIER, Math.max(1, amount));
    }
}
