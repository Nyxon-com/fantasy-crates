package it.haze.hazecrates.animation.opening.gui.support;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Durante la GUI della crate il client riceve anche l'inventario del player.
 * Ogni premio MMOItems resta in quei pacchetti: dopo qualche key la coda non
 * si svuota più e il ping sale. Qui l'inventario vero viene tenuto da parte
 * e rimesso solo a GUI chiusa.
 */
public final class PlayerStorageMask {

    private static final Map<UUID, Saved> HELD = new HashMap<>();
    private static int nextId;

    private PlayerStorageMask() {}

    public static void hide(HazeCrates plugin, Player player) {
        UUID uuid = player.getUniqueId();
        if (HELD.containsKey(uuid)) {
            return;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            copy[i] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack cursorCopy = cursor == null || cursor.getType().isAir() ? null : cursor.clone();
        int id = ++nextId;
        HELD.put(uuid, new Saved(copy, cursorCopy, id));
        player.getInventory().setStorageContents(new ItemStack[storage.length]);
        player.setItemOnCursor(new ItemStack(Material.AIR));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Saved saved = HELD.get(uuid);
            if (saved != null && saved.id == id && player.isOnline()) {
                restore(player);
            }
        }, 300L);
    }

    public static void restore(Player player) {
        if (player == null) {
            return;
        }
        Saved saved = HELD.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        player.getInventory().setStorageContents(saved.storage);
        if (saved.cursor == null) {
            return;
        }
        if (player.getItemOnCursor() == null || player.getItemOnCursor().getType().isAir()) {
            player.setItemOnCursor(saved.cursor);
            return;
        }
        player.getInventory().addItem(saved.cursor).values()
                .forEach(left -> {
                    if (player.getWorld() != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                });
    }

    public static void restoreAll(org.bukkit.Server server) {
        for (UUID uuid : new ArrayList<>(HELD.keySet())) {
            Player player = server.getPlayer(uuid);
            if (player != null) {
                restore(player);
            }
        }
    }

    private record Saved(ItemStack[] storage, ItemStack cursor, int id) {}
}
