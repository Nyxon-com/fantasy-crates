// made by nyxon
package net.nyxon.crates.key;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.database.DatabaseService;
import net.nyxon.crates.item.ExternalItemService;
import net.nyxon.crates.item.ItemProvider;
import net.nyxon.crates.item.ItemSpec;
import net.nyxon.crates.util.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.*;

public final class KeyService {

    private final NyxonCrates plugin;
    @SuppressWarnings("unused")
    private final DatabaseService database;
    private final NamespacedKey keyTag;
    private final NamespacedKey crateTag;
    private final NamespacedKey crateItemTag;

    private final ConcurrentMap<String, ItemSpec> specCache = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Material> materialCache = new ConcurrentHashMap<>();

    private volatile org.bukkit.configuration.file.YamlConfiguration keysYml;

    public KeyService(NyxonCrates plugin, DatabaseService database) {
        this.plugin   = plugin;
        this.database = database;
        this.keyTag       = new NamespacedKey(plugin, "key");
        this.crateTag     = new NamespacedKey(plugin, "crate_id");
        this.crateItemTag = new NamespacedKey(plugin, "crate_item");
    }

    public void reload() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "keys.yml");
        if (!file.exists()) plugin.saveResource("keys.yml", false);
        org.bukkit.configuration.file.YamlConfiguration loaded =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        specCache.clear();
        materialCache.clear();
        keysYml = loaded;
    }

    public org.bukkit.configuration.file.YamlConfiguration keysConfig() {
        return keysYml;
    }

    public ItemStack createPhysical(CrateDefinition crate, int amount) {
        ExternalItemService ext = plugin.externalItems();
        var keysYml = keysConfig();
        String path = "keys." + crate.id();
        boolean isLootbox = crate.keyType() == net.nyxon.crates.crate.KeyType.LOOTBOX;

        ItemSpec spec = keySpecOf(crate);

        boolean live = spec != null && spec.provider() != ItemProvider.VANILLA;
        boolean overrideAppearance = keysYml.getBoolean(path + ".override-appearance", false);
        boolean hasCustomName = keysYml.isSet(path + ".name");
        boolean hasCustomLore = !keysYml.getStringList(path + ".lore").isEmpty();

        String nameKey = isLootbox ? "lootbox.name" : "key.name";
        String defName = isLootbox
                ? "<gold>Forziere %crate%</gold>"
                : "<gold>Chiave %crate%</gold>";
        String name = keysYml.getString(path + ".name", plugin.getConfig().getString(nameKey, defName));
        if (name == null || name.isBlank()) name = defName;
        name = name.replace("%crate%", crate.displayName()).replace("%name%", crate.displayName());

        List<String> lore;
        if (isLootbox && plugin.getConfig().getBoolean("lootbox.auto-lore", true)) {
            lore = LootboxLore.build(plugin, crate);
        } else if (hasCustomLore) {
            lore = keysYml.getStringList(path + ".lore").stream()
                    .map(l -> l.replace("%crate%", crate.displayName()))
                    .toList();
        } else {
            lore = plugin.getConfig().getStringList(isLootbox ? "lootbox.lore" : "key.lore");
            lore = lore.stream().map(l -> l.replace("%crate%", crate.displayName())).toList();
        }

        boolean glow = keysYml.getBoolean(path + ".glow", plugin.getConfig().getBoolean("key.glow", true));
        int stack = Math.max(1, Math.min(64, amount));
        ItemStack item;
        if (live && !overrideAppearance && !isLootbox) {
            item = ext.resolve(spec, stack, hasCustomName ? name : null, hasCustomLore ? lore : null, false);
            if (!hasCustomLore && item != null) {
                ItemMeta wipe = item.getItemMeta();
                if (wipe != null) {
                    wipe.lore(List.of());
                    item.setItemMeta(wipe);
                }
            }
        } else {
            item = ext.resolve(spec, stack, name, lore.isEmpty() ? null : lore, glow);
        }
        if (item == null || item.getType().isAir()) {
            item = ext.resolve(ItemSpec.vanilla("TRIPWIRE_HOOK"), stack, name, lore, glow);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyTag,   PersistentDataType.BYTE,   (byte) 1);
            meta.getPersistentDataContainer().set(crateTag, PersistentDataType.STRING, crate.id());
            if (isLootbox) {
                meta.getPersistentDataContainer().set(crateItemTag, PersistentDataType.STRING, crate.id());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createPhysical(String crateId, int amount) {
        CrateDefinition crate = plugin.crates().get(crateId);
        if (crate != null) return createPhysical(crate, amount);

        ItemStack item = ItemFactory.create(Material.TRIPWIRE_HOOK, amount,
                "&b" + crateId + " Key", List.of("&7Right-click the crate"), true);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keyTag,   PersistentDataType.BYTE,   (byte) 1);
            meta.getPersistentDataContainer().set(crateTag, PersistentDataType.STRING, crateId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isKey(ItemStack item, String crateId) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();

        if (pdc.has(keyTag, PersistentDataType.BYTE)) {
            if (!crateId.equalsIgnoreCase(pdc.get(crateTag, PersistentDataType.STRING))) return false;
            CrateDefinition crate = plugin.crates().get(crateId);
            return crate == null || matchesMaterial(item, crate);
        }
        String lootboxId = pdc.get(crateItemTag, PersistentDataType.STRING);
        if (lootboxId != null) {
            if (!crateId.equalsIgnoreCase(lootboxId)) return false;
            CrateDefinition crate = plugin.crates().get(crateId);
            return crate == null || matchesMaterial(item, crate);
        }
        CrateDefinition crate = plugin.crates().get(crateId);
        if (crate == null) return false;
        ItemSpec spec = keySpecOf(crate);
        if (spec.provider() == ItemProvider.VANILLA || !matchesMaterial(item, crate)) return false;

        if (spec.provider() == ItemProvider.MMOITEMS && plugin.externalItems().isMmoitemsEnabled())
            return isMmoKey(item, spec.id());
        if (spec.provider() == ItemProvider.ITEMSADDER && plugin.externalItems().isItemsadderEnabled())
            return isIaKey(item, spec.id());
        if (spec.provider() == ItemProvider.NEXO && plugin.externalItems().isNexoEnabled())
            return isNexoKey(item, spec.id());
        return false;
    }

    private boolean matchesMaterial(ItemStack item, CrateDefinition crate) {
        Material expected = expectedMaterial(crate);
        return expected == null || item.getType() == expected;
    }

    public ItemSpec keySpecOf(CrateDefinition crate) {
        return specCache.computeIfAbsent(crate.id(), id -> {
            String overrideItem = keysConfig().getString("keys." + id + ".item", "");
            if (overrideItem != null && !overrideItem.isBlank()) {
                return ItemSpec.parse(overrideItem);
            }
            return crate.keySpec();
        });
    }

    private Material expectedMaterial(CrateDefinition crate) {
        Material cached = materialCache.get(crate.id());
        if (cached != null) return cached;
        ItemSpec spec = keySpecOf(crate);
        if (spec.provider() == ItemProvider.VANILLA) {
            Material mat = Material.matchMaterial(spec.id().toUpperCase(Locale.ROOT));
            Material resolved = mat != null ? mat : Material.TRIPWIRE_HOOK;
            materialCache.put(crate.id(), resolved);
            return resolved;
        }
        if ((spec.provider() == ItemProvider.NEXO && !plugin.externalItems().isNexoEnabled())
                || (spec.provider() == ItemProvider.MMOITEMS && !plugin.externalItems().isMmoitemsEnabled())
                || (spec.provider() == ItemProvider.ITEMSADDER && !plugin.externalItems().isItemsadderEnabled())) {
            return null;
        }
        ItemStack built = plugin.externalItems().resolve(spec);
        if (built == null || built.getType().isAir() || built.getType() == Material.BARRIER) {
            return null;
        }
        materialCache.put(crate.id(), built.getType());
        return built.getType();
    }

    public void givePhysical(Player player, CrateDefinition crate, int amount) {
        int left = Math.max(1, amount);
        while (left > 0) {
            int stack = Math.min(64, left);
            player.getInventory().addItem(createPhysical(crate, stack)).values()
                    .forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
            left -= stack;
        }
        play(player, crate.id(), "given");
    }

    public void givePhysical(Player player, String crateId, int amount) {
        CrateDefinition crate = plugin.crates().get(crateId);
        if (crate != null) {
            givePhysical(player, crate, amount);
            return;
        }
        player.getInventory().addItem(createPhysical(crateId, amount)).values()
                .forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
    }

    public int countPhysical(Player player, String crateId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isKey(item, crateId)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public int takePhysical(Player player, String crateId, int amount) {
        int left = Math.max(0, amount);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !isKey(item, crateId)) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        player.getInventory().setContents(contents);
        return amount - left;
    }

    public CompletableFuture<Integer> virtual(UUID uuid, String crate) {
        var store = plugin.playerData();
        if (store.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(store.getKeys(uuid, crate));
        }
        return store.load(uuid).thenApply(v -> store.getKeys(uuid, crate));
    }

    public CompletableFuture<Boolean> consumeVirtual(UUID uuid, String crate) {
        var store = plugin.playerData();
        if (store.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(store.consumeKey(uuid, crate));
        }
        return store.load(uuid).thenApply(v -> store.consumeKey(uuid, crate));
    }

    public CompletableFuture<Void> setVirtual(UUID uuid, String crate, int amount) {
        var store = plugin.playerData();
        Runnable apply = () -> store.setKeys(uuid, crate, amount);
        if (store.isLoaded(uuid)) {
            apply.run();
            return CompletableFuture.completedFuture(null);
        }
        return store.load(uuid).thenRun(apply);
    }

    public CompletableFuture<Boolean> takeVirtual(UUID uuid, String crate, int amount) {
        var store = plugin.playerData();
        if (store.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(store.takeKeys(uuid, crate, amount));
        }
        return store.load(uuid).thenApply(v -> store.takeKeys(uuid, crate, amount));
    }

    public CompletableFuture<Void> addVirtual(UUID uuid, String crate, int amount) {
        var store = plugin.playerData();
        Runnable apply = () -> store.addKeys(uuid, crate, amount);
        if (store.isLoaded(uuid)) {
            apply.run();
            return CompletableFuture.completedFuture(null);
        }
        return store.load(uuid).thenRun(apply);
    }

    public CompletableFuture<Map<String, Integer>> allVirtual(UUID uuid) {
        var store = plugin.playerData();
        if (store.isLoaded(uuid)) {
            return CompletableFuture.completedFuture(store.allKeys(uuid));
        }
        return store.load(uuid).thenApply(v -> store.allKeys(uuid));
    }

    public void play(Player player, String crateId, String event) {
        var keysYml = keysConfig();
        String path = "keys." + crateId + ".sound." + event;
        String soundName = keysYml.getString(path, keysYml.getString("defaults.sound." + event, "ENTITY_PLAYER_LEVELUP"));
        if (soundName == null || soundName.isBlank()) return;
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName.toUpperCase(Locale.ROOT)), 1f, 1.2f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean isMmoKey(ItemStack item, String typeAndId) {
        try {
            String[] p = typeAndId.split(":", 2);
            if (p.length != 2 || !item.hasItemMeta()) return false;
            var pdc  = item.getItemMeta().getPersistentDataContainer();
            var tk   = new NamespacedKey("mmoitems", "item-type");
            var ik   = new NamespacedKey("mmoitems", "item-id");
            String t = pdc.get(tk, PersistentDataType.STRING);
            String i = pdc.get(ik, PersistentDataType.STRING);
            return p[0].equalsIgnoreCase(t) && p[1].equalsIgnoreCase(i);
        } catch (Exception e) { return false; }
    }

    private boolean isIaKey(ItemStack item, String nsId) {
        try {
            var stack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            return stack != null && nsId.equalsIgnoreCase(stack.getNamespacedID());
        } catch (Exception e) { return false; }
    }

    private boolean isNexoKey(ItemStack item, String itemId) {
        try {
            String id = com.nexomc.nexo.api.NexoItems.idFromItem(item);
            return id != null && itemId.equalsIgnoreCase(id);
        } catch (Exception e) { return false; }
    }
}
