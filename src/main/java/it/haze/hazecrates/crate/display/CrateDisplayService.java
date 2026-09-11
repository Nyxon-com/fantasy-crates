// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.idle.IdleEffectTemplate;
import it.haze.hazecrates.item.ItemProvider;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CrateDisplayService {

    private final HazeCrates plugin;
    private final Map<String, List<UUID>> hologramStands = new HashMap<>();
    private final Map<String, BukkitTask> particleTasks = new HashMap<>();
    private final Map<String, BukkitTask> hologramTasks = new HashMap<>();
    private final Map<String, AtomicInteger> particleTicks = new HashMap<>();
    private final Map<String, UUID> glowEntities = new HashMap<>();
    private final NamespacedKey hologramTag;

    public CrateDisplayService(HazeCrates plugin) {
        this.plugin = plugin;
        this.hologramTag = new NamespacedKey(plugin, "crate_holo");
    }

    public void startAll() {
        stopAll();
        plugin.placements().all().forEach((locKey, crateId) -> {
            CrateDefinition crate = plugin.crates().get(crateId);
            if (crate == null) return;
            Location loc = CratePlacementService.fromKey(locKey);
            if (loc == null || loc.getWorld() == null) return;
            startAt(locKey, loc, crate);
        });
    }

    public void stopAll() {
        particleTasks.values().forEach(BukkitTask::cancel);
        particleTasks.clear();
        particleTicks.clear();
        hologramTasks.values().forEach(BukkitTask::cancel);
        hologramTasks.clear();
        hologramStands.forEach((key, uuids) -> despawnStands(uuids));
        hologramStands.clear();
        glowEntities.values().forEach(this::removeEntity);
        glowEntities.clear();
        removeTaggedHolograms(null);
    }

    public void startAt(String locKey, Location loc, CrateDefinition crate) {
        spawnHologram(locKey, loc, crate);
        scheduleParticles(locKey, loc, crate);
        placeCustomBlock(loc, crate);
        if (crate.display().glowingOutline()) spawnGlowEntity(locKey, loc, crate);
    }

    public void stopAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        stopAt(CratePlacementService.key(loc), loc);
    }

    public void stopAt(String locKey) {
        stopAt(locKey, CratePlacementService.fromKey(locKey));
    }

    public void stopAt(String locKey, Location loc) {
        BukkitTask pt = particleTasks.remove(locKey);
        if (pt != null) pt.cancel();
        particleTicks.remove(locKey);
        BukkitTask ht = hologramTasks.remove(locKey);
        if (ht != null) ht.cancel();
        List<UUID> stands = hologramStands.remove(locKey);
        if (stands != null) despawnStands(stands);
        UUID glowId = glowEntities.remove(locKey);
        if (glowId != null) removeEntity(glowId);
        removeTaggedHolograms(locKey);
        if (loc != null) removeNearbyHolograms(loc);
    }

    private void spawnHologram(String locKey, Location base, CrateDefinition crate) {
        CrateDisplayConfig cfg = crate.display();
        if (cfg.hologramLines().isEmpty()) return;

        World world = base.getWorld();
        if (world == null) return;

        if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            world.loadChunk(base.getBlockX() >> 4, base.getBlockZ() >> 4, false);
            if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;
        }

        List<UUID> old = hologramStands.remove(locKey);
        if (old != null) despawnStands(old);

        List<UUID> uuids = new ArrayList<>();
        double lineSpacing = 0.28;
        int lineCount = cfg.hologramLines().size();

        for (int i = 0; i < lineCount; i++) {
            double yOffset = cfg.hologramHeight() + (lineCount - 1 - i) * lineSpacing;
            Location standLoc = base.clone().add(0.5, yOffset, 0.5);

            ArmorStand stand = world.spawn(standLoc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setCanPickupItems(false);
                s.setSmall(true);
                s.setMarker(true);
                s.setArms(false);
                s.setBasePlate(false);
                s.setCustomNameVisible(true);
                s.setPersistent(true);
                s.setRemoveWhenFarAway(false);
                s.getPersistentDataContainer().set(hologramTag, PersistentDataType.STRING, locKey);
            });
            stand.customName(plugin.messages().parse(cfg.hologramLines().get(i)));
            uuids.add(stand.getUniqueId());
        }
        hologramStands.put(locKey, uuids);

        if (cfg.hologramRefreshTicks() > 0) {
            BukkitTask old2 = hologramTasks.remove(locKey);
            if (old2 != null) old2.cancel();

            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    () -> refreshHologram(locKey, base, crate),
                    cfg.hologramRefreshTicks(),
                    cfg.hologramRefreshTicks()
            );
            hologramTasks.put(locKey, task);
        }
    }

    private void refreshHologram(String locKey, Location base, CrateDefinition crate) {
        CrateDisplayConfig cfg = crate.display();
        List<UUID> uuids = hologramStands.get(locKey);
        if (uuids == null || uuids.size() != cfg.hologramLines().size()) {
            spawnHologram(locKey, base, crate);
            return;
        }
        World world = base.getWorld();
        if (world == null) return;
        for (int i = 0; i < uuids.size(); i++) {
            var entity = world.getEntity(uuids.get(i));
            if (entity instanceof ArmorStand stand) {
                stand.customName(plugin.messages().parse(cfg.hologramLines().get(i)));
            }
        }
    }

    private void scheduleParticles(String locKey, Location base, CrateDefinition crate) {
        IdleEffectTemplate effect = resolveIdle(crate.display());
        if (effect.style() == IdleEffectTemplate.IdleStyle.NONE) return;

        AtomicInteger tick = new AtomicInteger();
        particleTicks.put(locKey, tick);

        int interval = Math.max(1, effect.intervalTicks());
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> spawnIdle(base, effect, tick.getAndAdd(interval)),
                10L,
                interval
        );
        particleTasks.put(locKey, task);
    }

    private IdleEffectTemplate resolveIdle(CrateDisplayConfig cfg) {
        String id = cfg.idleEffectId();
        IdleEffectTemplate base;
        if (id == null || id.isBlank() || id.equalsIgnoreCase("none")) {
            if (cfg.particle() != null) {
                base = IdleEffectTemplate.simpleRing(
                        cfg.particle(),
                        cfg.particleCount() > 0 ? cfg.particleCount() : 4,
                        cfg.particleRadius() > 0 ? cfg.particleRadius() : 0.6,
                        cfg.particleIntervalTicks() > 0 ? cfg.particleIntervalTicks() : 15);
            } else {
                return IdleEffectTemplate.none();
            }
        } else {
            base = plugin.animations().idle(id);
        }

        Particle particle = cfg.particle() != null ? cfg.particle() : base.particle();
        int count = cfg.particleCount() > 0 ? cfg.particleCount() : base.count();
        double radius = cfg.particleRadius() > 0 ? cfg.particleRadius() : base.radius();
        int interval = cfg.particleIntervalTicks() > 0 ? cfg.particleIntervalTicks() : base.intervalTicks();
        return new IdleEffectTemplate(base.id(), base.style(), particle, count, radius, base.height(), interval);
    }

    private void spawnIdle(Location base, IdleEffectTemplate effect, int tick) {
        World world = base.getWorld();
        if (world == null) return;

        boolean playerNearby = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(base) <= 2304) {
                playerNearby = true;
                break;
            }
        }
        if (!playerNearby) return;

        double cx = base.getBlockX() + 0.5;
        double cy = base.getBlockY() + effect.height();
        double cz = base.getBlockZ() + 0.5;
        double r = effect.radius();
        Particle p = effect.particle();
        int count = Math.max(1, effect.count());

        switch (effect.style()) {
            case RING -> {
                int steps = 12;
                for (int i = 0; i < steps; i++) {
                    double a = (Math.PI * 2 / steps) * i;
                    world.spawnParticle(p, cx + r * Math.cos(a), base.getBlockY() + 0.12,
                            cz + r * Math.sin(a), count, 0, 0, 0, 0);
                }
            }
            case ORBIT -> {
                double a = tick * 0.25;
                world.spawnParticle(p, cx + r * Math.cos(a), cy, cz + r * Math.sin(a), count, 0.02, 0.02, 0.02, 0);
                world.spawnParticle(p, cx + r * Math.cos(a + Math.PI), cy,
                        cz + r * Math.sin(a + Math.PI), count, 0.02, 0.02, 0.02, 0);
            }
            case HELIX -> {
                double a = tick * 0.3;
                double y = (tick % 40) * (effect.height() / 40.0);
                for (int strand = 0; strand < 2; strand++) {
                    double ang = a + strand * Math.PI;
                    world.spawnParticle(p, cx + r * Math.cos(ang), base.getBlockY() + 0.1 + y,
                            cz + r * Math.sin(ang), count, 0.02, 0.02, 0.02, 0);
                }
            }
            case SPIRAL -> {
                double a = tick * 0.35;
                double shrink = 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(tick * 0.08));
                double rr = r * shrink;
                world.spawnParticle(p, cx + rr * Math.cos(a), cy + Math.sin(tick * 0.15) * 0.2,
                        cz + rr * Math.sin(a), count, 0.03, 0.03, 0.03, 0);
            }
            case PULSE -> {
                double pulse = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(tick * 0.2));
                int steps = 10;
                for (int i = 0; i < steps; i++) {
                    double a = (Math.PI * 2 / steps) * i;
                    world.spawnParticle(p, cx + r * pulse * Math.cos(a), cy,
                            cz + r * pulse * Math.sin(a), count, 0, 0, 0, 0);
                }
            }
            case RAIN -> {
                for (int i = 0; i < count + 2; i++) {
                    double ox = (Math.random() - 0.5) * r * 2;
                    double oz = (Math.random() - 0.5) * r * 2;
                    double y = base.getBlockY() + effect.height() - (tick % 12) * 0.12;
                    world.spawnParticle(p, cx + ox, Math.max(base.getBlockY() + 0.2, y), cz + oz,
                            1, 0.02, 0.05, 0.02, 0);
                }
            }
            case HEART -> {
                double a = tick * 0.15;
                double t = a;
                double hx = 16 * Math.pow(Math.sin(t), 3);
                double hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
                world.spawnParticle(p, cx + hx * r * 0.04, cy + hy * r * 0.03, cz, count, 0.02, 0.02, 0.02, 0);
            }
            case NONE -> { }
        }
    }

    public void sendOpenTitle(Player player, CrateDefinition crate) {
        CrateDisplayConfig cfg = crate.display();
        var conf = plugin.getConfig();
        String titleText = resolveTitleLine(cfg.titleLine(), conf.getString("opening-title.text", ""));
        String subtitleText = resolveTitleLine(cfg.subtitleLine(), conf.getString("opening-title.subtitle", ""));
        if (titleText.isBlank() && subtitleText.isBlank()) return;

        boolean crateOverride = !isUnset(cfg.titleLine()) || !isUnset(cfg.subtitleLine());
        int fadeIn = crateOverride ? cfg.titleFadeIn() : conf.getInt("opening-title.fade-in", cfg.titleFadeIn());
        int stay = crateOverride ? cfg.titleStay() : conf.getInt("opening-title.stay", cfg.titleStay());
        int fadeOut = crateOverride ? cfg.titleFadeOut() : conf.getInt("opening-title.fade-out", cfg.titleFadeOut());

        Component title    = plugin.messages().parse(
                titleText.replace("%crate%", crate.displayName()));
        Component subtitle = plugin.messages().parse(
                subtitleText.replace("%crate%", crate.displayName()));

        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(
                        Duration.ofMillis(Math.max(0, fadeIn)  * 50L),
                        Duration.ofMillis(Math.max(1, stay)    * 50L),
                        Duration.ofMillis(Math.max(0, fadeOut) * 50L))));
    }

    private static String resolveTitleLine(String crateValue, String globalValue) {
        if (isUnset(crateValue)) {
            return globalValue == null ? "" : globalValue;
        }
        if (crateValue.equalsIgnoreCase("none")
                || crateValue.equals("-")
                || crateValue.equalsIgnoreCase("disabled")) {
            return "";
        }
        return crateValue;
    }

    private static boolean isUnset(String value) {
        return value == null || value.isBlank();
    }

    private void placeCustomBlock(Location loc, CrateDefinition crate) {
        CrateDisplayConfig cfg = crate.display();
        ItemProvider provider = cfg.blockSpec().provider();
        if (provider == ItemProvider.ITEMSADDER) {
            placeItemsAdderBlock(loc, cfg.blockSpec().id());
        } else if (provider == ItemProvider.NEXO) {
            placeNexoBlock(loc, cfg.blockSpec().id());
        }
    }

    private void placeItemsAdderBlock(Location loc, String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return;

        try {
            dev.lone.itemsadder.api.CustomBlock customBlock =
                    dev.lone.itemsadder.api.CustomBlock.getInstance(id);
            if (customBlock != null) {
                customBlock.place(loc);
            } else {
                plugin.getLogger().warning("[HazeCrates] Unknown ItemsAdder block '" + id
                        + "' – block not placed. Check the namespace:id in your crate YAML.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[HazeCrates] Failed to place ItemsAdder block '"
                    + id + "': " + e.getMessage());
        }
    }

    private void placeNexoBlock(Location loc, String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Nexo")) return;
        try {
            if (com.nexomc.nexo.api.NexoBlocks.isCustomBlock(id)) {
                com.nexomc.nexo.api.NexoBlocks.place(id, loc);
            } else if (com.nexomc.nexo.api.NexoFurniture.isFurniture(id)) {
                com.nexomc.nexo.api.NexoFurniture.place(id, loc, loc.getYaw(), org.bukkit.block.BlockFace.UP);
            } else {
                plugin.getLogger().warning("[HazeCrates] Unknown Nexo block/furniture '" + id
                        + "' – not placed. Check the item id in your crate YAML.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[HazeCrates] Failed to place Nexo block '" + id + "': " + e.getMessage());
        }
    }

    private void spawnGlowEntity(String locKey, Location base, CrateDefinition crate) {
        World world = base.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;

        UUID old = glowEntities.remove(locKey);
        if (old != null) removeEntity(old);

        try {
            org.bukkit.entity.BlockDisplay display = world.spawn(base, org.bukkit.entity.BlockDisplay.class, bd -> {
                bd.setBlock(base.getBlock().getBlockData());
                bd.setGlowing(true);
                bd.setInvulnerable(true);
                bd.setGravity(false);
            });
            glowEntities.put(locKey, display.getUniqueId());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not spawn BlockDisplay glow for " + locKey + ": " + t.getMessage());
        }
    }

    private void removeEntity(UUID uuid) {
        Entity found = Bukkit.getEntity(uuid);
        if (found != null) {
            found.remove();
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            Entity e = world.getEntity(uuid);
            if (e != null) {
                e.remove();
                return;
            }
        }
    }

    private void despawnStands(List<UUID> uuids) {
        for (UUID uuid : uuids) {
            removeEntity(uuid);
        }
    }

    private void removeTaggedHolograms(String locKey) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof ArmorStand stand)) continue;
                String tagged = stand.getPersistentDataContainer().get(hologramTag, PersistentDataType.STRING);
                if (tagged == null) continue;
                if (locKey == null || locKey.equals(tagged)) {
                    stand.remove();
                }
            }
        }
    }

    private void removeNearbyHolograms(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 1.6, 0.5);
        for (Entity entity : world.getNearbyEntities(center, 1.4, 4.5, 1.4)) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (stand.isVisible() || !stand.isCustomNameVisible()) continue;
            String tagged = stand.getPersistentDataContainer().get(hologramTag, PersistentDataType.STRING);
            if (tagged != null || stand.isMarker() || stand.isSmall()) {
                stand.remove();
            }
        }
    }
}
