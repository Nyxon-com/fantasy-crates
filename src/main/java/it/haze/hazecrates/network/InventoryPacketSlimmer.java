package it.haze.hazecrates.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import it.haze.hazecrates.HazeCrates;
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

    private static final ComponentType<?>[] KEPT = {
            ComponentTypes.CUSTOM_NAME,
            ComponentTypes.ITEM_NAME,
            ComponentTypes.ITEM_MODEL,
            ComponentTypes.CUSTOM_MODEL_DATA,
            ComponentTypes.CUSTOM_MODEL_DATA_LISTS,
            ComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,
            ComponentTypes.DYED_COLOR
    };

    private final HazeCrates plugin;
    private final Set<UUID> armed = ConcurrentHashMap.newKeySet();
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
        UUID id = player.getUniqueId();
        armed.add(id);
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
                slimSlot(event);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
                slimPlayerSlot(event);
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                slimEquipment(event, player);
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

    private void slimSlot(PacketSendEvent event) {
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        int windowId = wrapper.getWindowId();
        if (windowId != 0 && windowId != -1 && wrapper.getSlot() < 27) {
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

    private void slimEquipment(PacketSendEvent event, Player player) {
        WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
        if (wrapper.getEntityId() != player.getEntityId()) {
            return;
        }
        List<Equipment> list = wrapper.getEquipment();
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Equipment> copy = new ArrayList<>(list.size());
        boolean changed = false;
        for (Equipment piece : list) {
            ItemStack slim = slim(piece.getItem());
            changed |= slim != piece.getItem();
            copy.add(new Equipment(piece.getSlot(), slim));
        }
        if (!changed) {
            return;
        }
        wrapper.setEquipment(copy);
        event.markForReEncode(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack slim(ItemStack source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        ItemStack.Builder builder = ItemStack.builder()
                .type(source.getType())
                .amount(Math.max(1, source.getAmount()))
                .version(source.getVersion())
                .registryHolder(source.getRegistryHolder());
        boolean extra = false;
        for (ComponentType type : KEPT) {
            if (!source.hasComponent(type)) {
                continue;
            }
            Object value = source.getComponent(type).orElse(null);
            if (value != null) {
                builder.component(type, value);
            }
        }
        if (source.hasComponentPatches()) {
            extra = source.getComponents().getPatches().size() > keptCount(source);
        }
        if (!extra && !source.hasComponentPatches()) {
            return source;
        }
        if (!extra) {
            return source;
        }
        return builder.build();
    }

    private static int keptCount(ItemStack source) {
        int count = 0;
        for (ComponentType<?> type : KEPT) {
            if (source.hasComponent(type)) {
                count++;
            }
        }
        return count;
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
        if (!armed.remove(id) || !player.isOnline()) {
            return;
        }
        passthroughUntil.put(id, System.nanoTime() + PASSTHROUGH_NANOS);
        player.updateInventory();
    }
}
