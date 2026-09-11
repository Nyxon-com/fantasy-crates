// made by haze
package it.haze.hazecrates.listener;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.CratePlacementService;
import it.haze.hazecrates.crate.KeyType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        if (hand.hasItemMeta() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
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
                    if (!player.hasPermission("hazecrates.open." + crate.id()) && !player.hasPermission("hazecrates.open.*")) {
                        plugin.messages().send(player, "no-permission");
                        return;
                    }
                    if (plugin.hasAnimSession(player.getUniqueId())) {
                        plugin.messages().send(player, "already-opening");
                        return;
                    }
                    if (hand.getAmount() > 1) {
                        hand.setAmount(hand.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                    Location openLoc;
                    if (event.getClickedBlock() != null) {
                        openLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
                    } else {
                        openLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.8));
                    }
                    open(player, crate, openLoc);
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

        if (hand.hasItemMeta()) {
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
        tryOpen(player, crate, openAt, true);
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
        var session = plugin.takeAnimSession(event.getPlayer().getUniqueId());
        if (session != null) {
            session.grantIfPending();
        }
    }

    public void tryOpen(Player player, CrateDefinition crate, Location location, boolean preferHand) {
        if (!player.hasPermission("hazecrates.open." + crate.id())
                && !player.hasPermission("hazecrates.open.*")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.hasAnimSession(player.getUniqueId())) {
            plugin.messages().send(player, "already-opening");
            return;
        }

        if (crate.keyType() == KeyType.LOOTBOX) {
            if (!consumePhysical(player, crate.id(), preferHand)) {
                plugin.messages().send(player, "need-crate", Map.of("crate", crate.displayName()));
                return;
            }
            open(player, crate, location);
            return;
        }

        if (crate.keyType() == KeyType.PHYSICAL && consumePhysical(player, crate.id(), preferHand)) {
            open(player, crate, location);
            return;
        }

        plugin.keys().consumeVirtual(player.getUniqueId(), crate.id())
                .whenComplete((consumed, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null) {
                                plugin.messages().send(player, "database-error");
                                return;
                            }
                            if (!consumed) {
                                if (crate.keyType() == KeyType.PHYSICAL) {
                                    plugin.messages().send(player, "need-key",
                                            Map.of("crate", crate.displayName(), "type", "fisica o virtuale"));
                                } else {
                                    plugin.messages().send(player, "need-virtual-key",
                                            Map.of("crate", crate.displayName()));
                                }
                                return;
                            }
                            if (plugin.hasAnimSession(player.getUniqueId())) {
                                plugin.messages().send(player, "already-opening");
                                return;
                            }
                            open(player, crate, location);
                        }));
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

    public void open(Player player, CrateDefinition crate, Location location) {
        if (plugin.hasAnimSession(player.getUniqueId())) {
            plugin.messages().send(player, "already-opening");
            return;
        }
        plugin.rewards().choose(player, crate).ifPresentOrElse(
                reward -> {
                    CrateAnimation anim = plugin.animations().animationFor(crate);
                    plugin.display().sendOpenTitle(player, crate);
                    plugin.messages().send(player, "opening", Map.of("crate", crate.displayName()));
                    anim.play(player, location, crate, reward, () -> {
                                plugin.rewards().grant(player, crate, reward);
                                plugin.stats().recordOpening(player, crate);
                            });
                },
                () -> plugin.messages().send(player, "no-rewards"));
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
