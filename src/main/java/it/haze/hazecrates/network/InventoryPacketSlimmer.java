package it.haze.hazecrates.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.gui.preview.PreviewHolder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durante l'apertura di una crate il client riceve di nuovo tutto l'inventario.
 * Gli MMOItem sono pesanti: a raffica la coda non si svuota e il ping sale.
 * Qui restano modello e nome, i dati veri tornano una volta sola a fine raffica.
 */
public final class InventoryPacketSlimmer extends PacketListenerAbstract implements CratePacketGuard {

    private static final long PASSTHROUGH_NANOS = 400_000_000L;
    private static final int FLUSH_DELAY_TICKS = 15;
    private static final int SAFETY_DELAY_TICKS = 30 * 20;
    private static final int PLAYER_SLOTS = 36;

    private static final ComponentType<?>[] DROP = {
            ComponentTypes.CUSTOM_DATA,
            ComponentTypes.LORE,
            ComponentTypes.ATTRIBUTE_MODIFIERS,
            ComponentTypes.ENCHANTMENTS,
            ComponentTypes.STORED_ENCHANTMENTS,
            ComponentTypes.ENTITY_DATA,
            ComponentTypes.TYPED_ENTITY_DATA,
            ComponentTypes.BUCKET_ENTITY_DATA,
            ComponentTypes.BLOCK_ENTITY_DATA,
            ComponentTypes.TYPED_BLOCK_ENTITY_DATA,
            ComponentTypes.CONTAINER,
            ComponentTypes.BUNDLE_CONTENTS,
            ComponentTypes.CHARGED_PROJECTILES,
            ComponentTypes.POTION_CONTENTS,
            ComponentTypes.WRITTEN_BOOK_CONTENT,
            ComponentTypes.WRITABLE_BOOK_CONTENT,
            ComponentTypes.FIREWORKS,
            ComponentTypes.FIREWORK_EXPLOSION,
            ComponentTypes.CAN_PLACE_ON,
            ComponentTypes.CAN_BREAK,
            ComponentTypes.RECIPES
    };

    private final HazeCrates plugin;
    private final Set<UUID> armed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> topSlots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> passthroughUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> flushTasks = new ConcurrentHashMap<>();

    public InventoryPacketSlimmer(HazeCrates plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("[HazeCrates] Inventario alleggerito durante l'apertura delle crate.");
    }

    @Override
    public void arm(Player player) {
        arm(player, 27);
    }

    @Override
    public void arm(Player player, int slots) {
        UUID id = player.getUniqueId();
        armed.add(id);
        topSlots.put(id, Math.max(9, slots));
        passthroughUntil.remove(id);
        reschedule(player, SAFETY_DELAY_TICKS);
    }

    @Override
    public void scheduleFlush(Player player) {
        if (!armed.contains(player.getUniqueId())) {
            return;
        }
        reschedule(player, FLUSH_DELAY_TICKS);
    }

    @Override
    public void disarm(UUID playerId) {
        armed.remove(playerId);
        topSlots.remove(playerId);
        passthroughUntil.remove(playerId);
        BukkitTask task = flushTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        for (UUID id : new ArrayList<>(flushTasks.keySet())) {
            disarm(id);
        }
        armed.clear();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (passthrough(id) || !armed.contains(id)) {
            return;
        }
        try {
            if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                slimWindow(event);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                slimSlot(event, id);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
                slimPlayerSlot(event);
            }
        } catch (Throwable ignored) {
            event.markForReEncode(false);
        }
    }

    private void slimWindow(PacketSendEvent event) {
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        List<ItemStack> items = wrapper.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        int from = wrapper.getWindowId() == 0 ? 0 : Math.max(0, items.size() - PLAYER_SLOTS);
        List<ItemStack> copy = new ArrayList<>(items.size());
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (i >= from) {
                ItemStack slim = slim(item);
                changed |= slim != item;
                copy.add(slim);
            } else {
                copy.add(item);
            }
        }
        ItemStack carried = wrapper.getCarriedItem().orElse(null);
        ItemStack slimCarried = slim(carried);
        changed |= slimCarried != carried;
        if (!changed) {
            return;
        }
        wrapper.setItems(copy);
        wrapper.setCarriedItem(slimCarried);
        event.markForReEncode(true);
    }

    private void slimSlot(PacketSendEvent event, UUID playerId) {
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        int windowId = wrapper.getWindowId();
        int top = topSlots.getOrDefault(playerId, 27);
        if (windowId != 0 && windowId != -1 && wrapper.getSlot() < top) {
            return;
        }
        ItemStack slim = slim(wrapper.getItem());
        if (slim == wrapper.getItem()) {
            return;
        }
        wrapper.setItem(slim);
        event.markForReEncode(true);
    }

    private void slimPlayerSlot(PacketSendEvent event) {
        WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(event);
        ItemStack slim = slim(wrapper.getStack());
        if (slim == wrapper.getStack()) {
            return;
        }
        wrapper.setStack(slim);
        event.markForReEncode(true);
    }

    private static ItemStack slim(ItemStack source) {
        if (source == null || source.isEmpty() || !source.hasComponentPatches()) {
            return source;
        }
        boolean heavy = false;
        for (ComponentType<?> type : DROP) {
            if (source.hasComponent(type)) {
                heavy = true;
                break;
            }
        }
        if (!heavy) {
            return source;
        }
        ItemStack copy = source.copy();
        for (ComponentType<?> type : DROP) {
            if (copy.hasComponent(type)) {
                copy.unsetComponent(type);
            }
        }
        return copy;
    }

    private boolean passthrough(UUID id) {
        Long until = passthroughUntil.get(id);
        if (until == null) {
            return false;
        }
        if (System.nanoTime() <= until) {
            return true;
        }
        passthroughUntil.remove(id, until);
        return false;
    }

    private void reschedule(Player player, int delayTicks) {
        UUID id = player.getUniqueId();
        BukkitTask previous = flushTasks.remove(id);
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> flush(player), delayTicks);
        flushTasks.put(id, task);
    }

    private void flush(Player player) {
        UUID id = player.getUniqueId();
        flushTasks.remove(id);
        if (!player.isOnline() || !armed.contains(id)) {
            return;
        }
        if (stillInCrateGui(player)) {
            reschedule(player, FLUSH_DELAY_TICKS);
            return;
        }
        armed.remove(id);
        topSlots.remove(id);
        passthroughUntil.put(id, System.nanoTime() + PASSTHROUGH_NANOS);
        player.updateInventory();
    }

    private boolean stillInCrateGui(Player player) {
        if (plugin.hasAnimSession(player.getUniqueId())) {
            return true;
        }
        return player.getOpenInventory().getTopInventory().getHolder() instanceof PreviewHolder;
    }
}
