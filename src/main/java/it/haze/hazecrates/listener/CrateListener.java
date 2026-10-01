// made by haze
package it.haze.hazecrates.listener;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.CratePlacementService;
import it.haze.hazecrates.crate.KeyType;
import it.haze.hazecrates.crate.RewardDefinition;
import it.haze.hazecrates.gui.preview.PreviewInventory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class CrateListener implements Listener {

    private final HazeCrates plugin;
    private final NamespacedKey crateBlockKey;
    private final NamespacedKey crateItemKey;

    public CrateListener(HazeCrates plugin) {
        this.plugin        = plugin;
        this.crateBlockKey = new NamespacedKey(plugin, CratePlacementService.PDC_KEY);
        this.crateItemKey  = new NamespacedKey(plugin, "crate_item");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() != null
                && it.haze.hazecrates.animation.opening.world.support.TempOpenChest.isProtected(event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean bulkPlacedCrate = player.isSneaking()
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && getCrateId(event.getClickedBlock()) != null;

        if (!bulkPlacedCrate && hand.hasItemMeta()
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            String heldCrateId = hand.getItemMeta().getPersistentDataContainer().get(crateItemKey, PersistentDataType.STRING);
            if (heldCrateId != null) {
                CrateDefinition crate = plugin.crates().get(heldCrateId);
                if (crate == null) {
                    plugin.messages().send(player, "unknown-crate", Map.of("crate", heldCrateId));
                    event.setCancelled(true);
                    return;
                }
                if (crate.keyType() == KeyType.LOOTBOX) {
                    event.setCancelled(true);
                    Location openLoc;
                    if (event.getClickedBlock() != null) {
                        openLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
                    } else {
                        openLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.8));
                    }
                    if (player.isSneaking()) tryOpenAll(player, crate);
                    else tryOpen(player, crate, openLoc, true);
                    return;
                }

                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    Block target = event.getClickedBlock();
                    if (target == null) return;

                    String existingId = getCrateId(target);
                    if (existingId != null) {
                        plugin.messages().send(player, "invalid-target");
                        return;
                    }

                    if (!player.hasPermission("hazecrates.admin")) {
                        plugin.messages().send(player, "no-permission");
                        return;
                    }

                    Location loc = target.getLocation();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.placements().place(loc, crate);
                        if (plugin.externalPluginsReady()) {
                            plugin.display().stopAt(loc);
                            plugin.display().startAt(CratePlacementService.key(loc), loc, crate);
                        }
                        plugin.messages().send(player, "created");
                    });

                    if (hand.getAmount() > 1) {
                        hand.setAmount(hand.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                    return;
                }
            }
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        String crateId = getCrateId(clicked);
        if (crateId == null) return;
        event.setCancelled(true);

        CrateDefinition crate = plugin.crates().get(crateId);
        if (crate == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (crate.previewOnLeftClick()
                    && (player.hasPermission("hazecrates.preview")
                        || player.hasPermission("hazecrates.open." + crate.id())
                        || player.hasPermission("hazecrates.open.*"))) {
                PreviewInventory.open(player, crate, plugin);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (!bulkPlacedCrate && hand.hasItemMeta()) {
            String held = hand.getItemMeta().getPersistentDataContainer()
                    .get(crateItemKey, PersistentDataType.STRING);
            if (held != null && crate.keyType() != KeyType.LOOTBOX) return;
        }

        if (!player.hasPermission("hazecrates.open." + crate.id())
                && !player.hasPermission("hazecrates.open.*")) {
            plugin.messages().send(player, "no-permission");
            return;
        }

        if (plugin.hasAnimSession(player.getUniqueId())) {
            plugin.messages().send(player, "already-opening");
            return;
        }

        Location openAt = clicked.getLocation().add(0.5, 1.0, 0.5);
        if (player.isSneaking()) tryOpenAll(player, crate);
        else tryOpen(player, crate, openAt, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTempChestOpen(InventoryOpenEvent event) {
        org.bukkit.Location loc = event.getInventory().getLocation();
        if (loc != null && it.haze.hazecrates.animation.opening.world.support.TempOpenChest.isProtected(loc.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (it.haze.hazecrates.animation.opening.world.support.TempOpenChest.isProtected(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (getCrateId(event.getBlock()) == null) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("hazecrates.admin"))
            plugin.messages().send(event.getPlayer(), "use-break-command");
        else
            plugin.messages().send(event.getPlayer(), "no-permission");
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        it.haze.hazecrates.animation.opening.world.support.OpeningProps.clear(event.getPlayer().getUniqueId());
        var session = plugin.takeAnimSession(event.getPlayer().getUniqueId());
        if (session != null) {
            session.cancelTask();
            session.grantIfPending();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        org.bukkit.entity.Player player = event.getEntity();
        it.haze.hazecrates.animation.opening.world.support.OpeningProps.clear(player.getUniqueId());
        var session = plugin.takeAnimSession(player.getUniqueId());
        if (session != null) {
            session.cancelTask();
            session.grantIfPending();
        }
    }

    public void tryOpen(Player player, CrateDefinition crate, Location location, boolean preferHand) {
        tryOpen(player, crate, location, preferHand, true);
    }

    /**
     * @param animate false = grant istantaneo (pannelli/console): evita desync GUI
     *                quando CommandPanels chiude l'inventario nello stesso tick.
     */
    public void tryOpen(Player player, CrateDefinition crate, Location location,
                        boolean preferHand, boolean animate) {
        if (!player.hasPermission("hazecrates.open." + crate.id())
                && !player.hasPermission("hazecrates.open.*")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (!plugin.rewards().hasAccessible(player, crate)) {
            plugin.messages().send(player, "no-rewards");
            return;
        }
        java.util.UUID uuid = player.getUniqueId();
        if (!plugin.tryStartAnimSession(uuid, new AnimationSession(null, null))) {
            plugin.messages().send(player, "already-opening");
            return;
        }

        if (crate.keyType() == KeyType.LOOTBOX) {
            if (!consumePhysical(player, crate.id(), preferHand)) {
                plugin.takeAnimSession(uuid);
                plugin.messages().send(player, "need-crate", Map.of("crate", crate.displayName()));
                return;
            }
            finishOpen(player, crate, location, animate);
            return;
        }

        if (crate.keyType() == KeyType.PHYSICAL && consumePhysical(player, crate.id(), preferHand)) {
            finishOpen(player, crate, location, animate);
            return;
        }

        plugin.keys().consumeVirtual(player.getUniqueId(), crate.id())
                .whenComplete((consumed, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                plugin.takeAnimSession(uuid);
                                return;
                            }
                            if (error != null) {
                                plugin.takeAnimSession(uuid);
                                plugin.messages().send(player, "database-error");
                                return;
                            }
                            if (!consumed) {
                                plugin.takeAnimSession(uuid);
                                if (crate.keyType() == KeyType.PHYSICAL) {
                                    plugin.messages().send(player, "need-key",
                                            Map.of("crate", crate.displayName(), "type", "fisica o virtuale"));
                                } else {
                                    plugin.messages().send(player, "need-virtual-key",
                                            Map.of("crate", crate.displayName()));
                                }
                                return;
                            }
                            finishOpen(player, crate, location, animate);
                        }));
    }

    private void finishOpen(Player player, CrateDefinition crate, Location location, boolean animate) {
        if (animate) {
            open(player, crate, location);
            return;
        }
        // 2 tick: lascia chiudere il pannello CP prima di grantare.
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                plugin.takeAnimSession(uuid);
                return;
            }
            if (!plugin.hasAnimSession(uuid)) return;
            plugin.rewards().choose(player, crate).ifPresentOrElse(
                    reward -> {
                        plugin.takeAnimSession(uuid);
                        plugin.rewards().grant(player, crate, reward);
                        plugin.stats().recordOpening(player, crate);
                    },
                    () -> {
                        plugin.takeAnimSession(uuid);
                        plugin.messages().send(player, "no-rewards");
                    });
        }, 2L);
    }

    private boolean consumePhysical(Player player, String crateId, boolean preferHand) {
        if (preferHand) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (plugin.keys().isKey(hand, crateId)) {
                if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
                else player.getInventory().setItemInMainHand(null);
                return true;
            }
        }
        return plugin.keys().takePhysical(player, crateId, 1) > 0;
    }

    public void tryOpenAll(Player player, CrateDefinition crate) {
        if (!player.hasPermission("hazecrates.open." + crate.id())
                && !player.hasPermission("hazecrates.open.*")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (!plugin.rewards().hasAccessible(player, crate)) {
            plugin.messages().send(player, "no-rewards");
            return;
        }

        UUID uuid = player.getUniqueId();
        AnimationSession session = new AnimationSession(null, null);
        if (!plugin.tryStartAnimSession(uuid, session)) {
            plugin.messages().send(player, "already-opening");
            return;
        }

        plugin.playerData().load(uuid).whenComplete((ignored, error) -> {
            Runnable start = () -> {
                if (!player.isOnline() || plugin.animSession(uuid) != session) return;
                if (error != null) {
                    plugin.takeAnimSession(uuid);
                    plugin.messages().send(player, "database-error");
                    return;
                }
                // 2 tick dopo chiusura pannello CP, così l'inventario è stabile.
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> {
                            if (!player.isOnline() || plugin.animSession(uuid) != session) return;
                            startBulkOpen(player, crate, session);
                        }, 2L);
            };
            if (org.bukkit.Bukkit.isPrimaryThread()) start.run();
            else plugin.getServer().getScheduler().runTask(plugin, start);
        });
    }

    private void startBulkOpen(Player player, CrateDefinition crate, AnimationSession session) {
        UUID uuid = player.getUniqueId();
        int physical = crate.keyType() == KeyType.VIRTUAL ? 0
                : plugin.keys().countPhysical(player, crate.id());
        int virtual = crate.keyType() == KeyType.LOOTBOX ? 0
                : plugin.playerData().getKeys(uuid, crate.id());
        if (physical <= 0 && virtual <= 0) {
            plugin.takeAnimSession(uuid);
            plugin.messages().send(player, crate.keyType() == KeyType.LOOTBOX ? "need-crate"
                    : crate.keyType() == KeyType.VIRTUAL ? "need-virtual-key" : "need-key",
                    Map.of("crate", crate.displayName(), "type", "fisica o virtuale"));
            return;
        }

        int maxBatch = Math.clamp(plugin.getConfig().getInt("bulk-open.max-per-open", 5), 1, 64);
        int available = physical + virtual;
        int toOpen = Math.min(available, maxBatch);
        // Preferisci prima le chiavi fisiche, poi le virtuali.
        int physicalToUse = Math.min(physical, toOpen);
        int virtualToUse = toOpen - physicalToUse;

        int maxPerTick = Math.clamp(plugin.getConfig().getInt("bulk-open.max-per-tick", 4), 1, 16);
        long budgetNanos = Math.clamp(plugin.getConfig().getLong("bulk-open.time-budget-ms", 2), 1, 5)
                * 1_000_000L;
        BukkitRunnable task = new BukkitRunnable() {
            int physicalLeft = physicalToUse;
            int virtualLeft = virtualToUse;
            long opened;

            @Override
            public void run() {
                if (!player.isOnline() || plugin.animSession(uuid) != session) {
                    cancel();
                    return;
                }

                long started = System.nanoTime();
                for (int i = 0; i < maxPerTick && (physicalLeft > 0 || virtualLeft > 0); i++) {
                    RewardDefinition reward = plugin.rewards().choose(player, crate).orElse(null);
                    if (reward == null) {
                        finish();
                        plugin.messages().send(player, "no-rewards");
                        return;
                    }

                    if (physicalLeft > 0) {
                        if (plugin.keys().takePhysical(player, crate.id(), 1) != 1) {
                            physicalLeft = 0;
                            continue;
                        }
                        physicalLeft--;
                    } else if (!plugin.playerData().consumeKey(uuid, crate.id())) {
                        virtualLeft = 0;
                        continue;
                    } else {
                        virtualLeft--;
                    }

                    try {
                        plugin.rewards().grant(player, crate, reward, false);
                        plugin.stats().recordOpening(player, crate);
                        opened++;
                    } catch (Exception failure) {
                        plugin.getLogger().log(Level.SEVERE, "[HazeCrates] Bulk opening failed for " + uuid, failure);
                        finish();
                        plugin.messages().send(player, "bulk-opening-error");
                        return;
                    }
                    if (System.nanoTime() - started >= budgetNanos) break;
                }
                if (physicalLeft <= 0 && virtualLeft <= 0) finish();
            }

            private void finish() {
                cancel();
                plugin.takeAnimSession(uuid);
                if (opened > 0) {
                    plugin.messages().send(player, "bulk-opening-complete",
                            Map.of("amount", Long.toString(opened), "crate", crate.displayName()));
                }
            }
        };
        session.bind(task.runTaskTimer(plugin, 1L, 1L));
    }

    public void open(Player player, CrateDefinition crate, Location location) {
        java.util.UUID uuid = player.getUniqueId();
        if (!plugin.hasAnimSession(uuid)
                && !plugin.tryStartAnimSession(uuid, new AnimationSession(null, null))) {
            plugin.messages().send(player, "already-opening");
            return;
        }
        plugin.rewards().choose(player, crate).ifPresentOrElse(
                reward -> {
                    CrateAnimation anim = plugin.animations().animationFor(crate);
                    plugin.display().sendOpenTitle(player, crate);
                    plugin.messages().send(player, "opening", Map.of("crate", crate.displayName()));
                    anim.play(player, location, crate, reward, () -> {
                                plugin.takeAnimSession(uuid);
                                plugin.rewards().grant(player, crate, reward);
                                plugin.stats().recordOpening(player, crate);
                            });
                },
                () -> {
                    plugin.takeAnimSession(uuid);
                    plugin.messages().send(player, "no-rewards");
                });
    }

    public boolean breakCrateAt(Block block, Player player) {
        String crateId = getCrateId(block);
        if (crateId == null) return false;
        Location loc = block.getLocation();
        plugin.display().stopAt(loc);
        plugin.placements().remove(loc);
        CrateDefinition crate = plugin.crates().get(crateId);
        block.setType(org.bukkit.Material.AIR);
        if (crate != null && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), buildCrateItem(crate));
        }
        plugin.messages().send(player, "removed");
        return true;
    }

    public ItemStack buildCrateItem(CrateDefinition crate) {
        if (crate.keyType() == KeyType.LOOTBOX) {
            return plugin.keys().createPhysical(crate, 1);
        }
        ItemStack item = plugin.externalItems().resolve(
                crate.display().blockSpec(), 1,
                crate.displayName(), buildCrateItemLore(crate),
                crate.display().glowingOutline());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(crateItemKey, PersistentDataType.STRING, crate.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public NamespacedKey crateBlockKey() { return crateBlockKey; }
    public NamespacedKey crateItemKey()  { return crateItemKey; }

    private String getCrateId(Block b) {
        String fromMap = plugin.placements().crateIdAt(b.getLocation());
        if (fromMap != null) return fromMap;
        if (b.getState() instanceof TileState ts)
            return ts.getPersistentDataContainer().get(crateBlockKey, PersistentDataType.STRING);
        return null;
    }

    private List<String> buildCrateItemLore(CrateDefinition crate) {
        List<String> l = new ArrayList<>();
        l.add("&8ID: &7" + crate.id());
        l.add("&8Animation: &7" + crate.animation());
        l.add("&8Rewards: &7" + crate.rewards().size());
        if (!crate.display().hologramLines().isEmpty())
            l.add("&8Hologram: &7" + crate.display().hologramLines().get(0));
        if (crate.display().particle() != null)
            l.add("&8Particles: &7" + crate.display().particle().name());
        l.add("");
        l.add("&7Right-click a block to turn it into a crate");
        l.add("&7Use /crate break to pick up");
        return l;
    }
}
