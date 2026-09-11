// made by haze
package it.haze.hazecrates.key;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.database.DatabaseService;
import it.haze.hazecrates.item.ExternalItemService;
import it.haze.hazecrates.item.ItemProvider;
import it.haze.hazecrates.item.ItemSpec;
import it.haze.hazecrates.util.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;

public final class KeyService {

    private final HazeCrates plugin;
    private final DatabaseService database;
    private final NamespacedKey keyTag;
    private final NamespacedKey crateTag;
    private final NamespacedKey crateItemTag;

    private final ConcurrentMap<String, Integer> cache = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Integer> memKeys = new ConcurrentHashMap<>();

    public KeyService(HazeCrates plugin, DatabaseService database) {
        this.plugin   = plugin;
        this.database = database;
        this.keyTag       = new NamespacedKey(plugin, "key");
        this.crateTag     = new NamespacedKey(plugin, "crate_id");
        this.crateItemTag = new NamespacedKey(plugin, "crate_item");
    }

    public org.bukkit.configuration.file.YamlConfiguration keysConfig() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "keys.yml");
        if (!file.exists()) plugin.saveResource("keys.yml", false);
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public ItemStack createPhysical(CrateDefinition crate, int amount) {
        ExternalItemService ext = plugin.externalItems();
        var keysYml = keysConfig();
        String path = "keys." + crate.id();
        boolean isLootbox = crate.keyType() == it.haze.hazecrates.crate.KeyType.LOOTBOX;

        ItemSpec spec = crate.keySpec();
        String overrideItem = keysYml.getString(path + ".item", "");
        if (overrideItem != null && !overrideItem.isBlank()) {
            spec = ItemSpec.parse(overrideItem);
        }

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
        } else {
            String loreKey = isLootbox ? "lootbox.lore" : "key.lore";
            lore = keysYml.getStringList(path + ".lore");
            if (lore.isEmpty()) lore = plugin.getConfig().getStringList(loreKey);
            if (lore.isEmpty()) {
                lore = List.of(
                        "<dark_gray>Valary RPG</dark_gray>",
                        isLootbox
                                ? "<gray>Clic destro ovunque per svelare il premio.</gray>"
                                : "<gray>Clic destro sulla crate per aprirla.</gray>"
                );
            }
            lore = lore.stream().map(l -> l.replace("%crate%", crate.displayName())).toList();
        }

        boolean glow = keysYml.getBoolean(path + ".glow", plugin.getConfig().getBoolean("key.glow", true));
        int stack = Math.max(1, Math.min(64, amount));
        ItemStack item = ext.resolve(spec, stack, name, lore, glow);
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
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        if (meta.getPersistentDataContainer().has(keyTag, PersistentDataType.BYTE)
                && crateId.equalsIgnoreCase(
                        meta.getPersistentDataContainer().get(crateTag, PersistentDataType.STRING)))
            return true;

        CrateDefinition crate = plugin.crates().get(crateId);
        if (crate != null && crate.keyType() == it.haze.hazecrates.crate.KeyType.LOOTBOX
                && crateId.equalsIgnoreCase(
                        meta.getPersistentDataContainer().get(crateItemTag, PersistentDataType.STRING))) {
            return true;
        }
        if (crate == null) return false;
        ItemSpec spec = crate.keySpec();

        if (spec.provider() == ItemProvider.MMOITEMS && plugin.externalItems().isMmoitemsEnabled())
            return isMmoKey(item, spec.id());
        if (spec.provider() == ItemProvider.ITEMSADDER && plugin.externalItems().isItemsadderEnabled())
            return isIaKey(item, spec.id());
        if (spec.provider() == ItemProvider.NEXO && plugin.externalItems().isNexoEnabled())
            return isNexoKey(item, spec.id());
        return false;
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

    private String mk(UUID uuid, String crate) { return uuid + ":" + crate; }

    public CompletableFuture<Integer> virtual(UUID uuid, String crate) {
        if (!database.isAvailable())
            return CompletableFuture.completedFuture(memKeys.getOrDefault(mk(uuid, crate), 0));
        Integer c = cache.get(mk(uuid, crate));
        if (c != null) return CompletableFuture.completedFuture(c);
        return database.query(conn -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT amount FROM fc_virtual_keys WHERE uuid=? AND crate_id=?")) {
                s.setString(1, uuid.toString()); s.setString(2, crate);
                ResultSet r = s.executeQuery();
                int v = r.next() ? r.getInt(1) : 0;
                cache.put(mk(uuid, crate), v);
                return v;
            } catch (Exception e) { throw new CompletionException(e); }
        }, 0);
    }

    public CompletableFuture<Boolean> consumeVirtual(UUID uuid, String crate) {
        if (!database.isAvailable()) {
            String k = mk(uuid, crate);
            int[] consumed = {0};
            memKeys.compute(k, (key, v) -> {
                if (v != null && v > 0) { consumed[0] = 1; return v - 1; }
                return v;
            });
            return CompletableFuture.completedFuture(consumed[0] == 1);
        }
        return database.query(conn -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "UPDATE fc_virtual_keys SET amount=amount-1 WHERE uuid=? AND crate_id=? AND amount>0")) {
                s.setString(1, uuid.toString()); s.setString(2, crate);
                boolean ok = s.executeUpdate() == 1;
                if (ok) cache.computeIfPresent(mk(uuid, crate), (k, v) -> Math.max(0, v - 1));
                return ok;
            } catch (Exception e) { throw new CompletionException(e); }
        }, false);
    }

    public CompletableFuture<Void> setVirtual(UUID uuid, String crate, int amount) {
        if (!database.isAvailable()) {
            memKeys.put(mk(uuid, crate), Math.max(0, amount));
            return CompletableFuture.completedFuture(null);
        }
        return database.query(conn -> {
            try {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT OR IGNORE INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,0)")) {
                    ins.setString(1, uuid.toString()); ins.setString(2, crate); ins.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE fc_virtual_keys SET amount=? WHERE uuid=? AND crate_id=?")) {
                    upd.setInt(1, Math.max(0, amount)); upd.setString(2, uuid.toString()); upd.setString(3, crate);
                    upd.executeUpdate();
                }
                cache.put(mk(uuid, crate), Math.max(0, amount));
                return null;
            } catch (Exception e) { throw new CompletionException(e); }
        }, null);
    }

    public CompletableFuture<Boolean> takeVirtual(UUID uuid, String crate, int amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(true);
        if (!database.isAvailable()) {
            String k = mk(uuid, crate);
            int current = memKeys.getOrDefault(k, 0);
            if (current >= amount) {
                memKeys.put(k, current - amount);
                return CompletableFuture.completedFuture(true);
            }
            return CompletableFuture.completedFuture(false);
        }
        return database.query(conn -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "UPDATE fc_virtual_keys SET amount=amount-? WHERE uuid=? AND crate_id=? AND amount>=?")) {
                s.setInt(1, amount); s.setString(2, uuid.toString()); s.setString(3, crate); s.setInt(4, amount);
                boolean ok = s.executeUpdate() == 1;
                if (ok) cache.computeIfPresent(mk(uuid, crate), (k, v) -> Math.max(0, v - amount));
                return ok;
            } catch (Exception e) { throw new CompletionException(e); }
        }, false);
    }

    public CompletableFuture<Void> addVirtual(UUID uuid, String crate, int amount) {
        if (!database.isAvailable()) {
            memKeys.merge(mk(uuid, crate), amount, Integer::sum);
            return CompletableFuture.completedFuture(null);
        }
        return database.query(conn -> {
            try {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT OR IGNORE INTO fc_virtual_keys(uuid,crate_id,amount) VALUES(?,?,0)")) {
                    ins.setString(1, uuid.toString()); ins.setString(2, crate); ins.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE fc_virtual_keys SET amount=MAX(0,amount+?) WHERE uuid=? AND crate_id=?")) {
                    upd.setInt(1, amount); upd.setString(2, uuid.toString()); upd.setString(3, crate);
                    upd.executeUpdate();
                }
                cache.computeIfPresent(mk(uuid, crate), (k, v) -> Math.max(0, v + amount));
                return null;
            } catch (Exception e) { throw new CompletionException(e); }
        }, null);
    }

    public CompletableFuture<Map<String, Integer>> allVirtual(UUID uuid) {
        if (!database.isAvailable()) {
            String prefix = uuid + ":";
            Map<String, Integer> out = new HashMap<>();
            memKeys.forEach((k, v) -> { if (k.startsWith(prefix) && v > 0) out.put(k.substring(prefix.length()), v); });
            return CompletableFuture.completedFuture(out);
        }
        return database.query(conn -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT crate_id, amount FROM fc_virtual_keys WHERE uuid=? AND amount>0")) {
                s.setString(1, uuid.toString());
                ResultSet r = s.executeQuery();
                Map<String, Integer> map = new HashMap<>();
                while (r.next()) {
                    String id = r.getString("crate_id"); int amt = r.getInt("amount");
                    map.put(id, amt); cache.put(mk(uuid, id), amt);
                }
                return map;
            } catch (Exception e) { throw new CompletionException(e); }
        }, Map.of());
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
