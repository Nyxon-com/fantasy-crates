// made by nyxon
package net.nyxon.crates.animation.opening.world.support;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.animation.AnimationSession;
import net.nyxon.crates.crate.RewardDefinition;
import net.nyxon.crates.item.DisplayIcon;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class WorldOpeningSupport {

    private WorldOpeningSupport() {}

    public static boolean chunkReady(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static AnimationSession begin(NyxonCrates plugin, Player player, Runnable reveal) {
        AnimationSession session = new AnimationSession(null, reveal);
        plugin.startAnimSession(player.getUniqueId(), session);
        return session;
    }

    public static ItemDisplay spawnItem(Location location, ItemStack item, float scale, boolean glow) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(DisplayIcon.light(item));
            display.setBillboard(Display.Billboard.CENTER);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setGlowing(glow);
            display.setTransformation(transform(0, 0, 0, 0, scale));
        });
    }

    public static ArmorStand spawnName(Location location, RewardDefinition reward) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(true);
            stand.customName(reward.displayComponent());
        });
    }

    public static Transformation transform(float x, float y, float z, float yawDegrees, float scale) {
        return transform(x, y, z, yawDegrees, 0f, scale);
    }

    public static Transformation transform(float x, float y, float z, float yawDegrees, float pitchDegrees, float scale) {
        return new Transformation(
                new Vector3f(x, y, z),
                new AxisAngle4f((float) Math.toRadians(yawDegrees), 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f((float) Math.toRadians(pitchDegrees), 1, 0, 0)
        );
    }

    public static void ring(World world, Location center, Particle particle, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            world.spawnParticle(
                    particle,
                    center.getX() + radius * Math.cos(angle),
                    center.getY(),
                    center.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0
            );
        }
    }

    public static void helix(World world, Location center, Particle particle, double radius, double height, double angle) {
        world.spawnParticle(
                particle,
                center.getX() + radius * Math.cos(angle),
                center.getY() + height,
                center.getZ() + radius * Math.sin(angle),
                1, 0.02, 0.02, 0.02, 0
        );
        world.spawnParticle(
                particle,
                center.getX() + radius * Math.cos(angle + Math.PI),
                center.getY() + height,
                center.getZ() + radius * Math.sin(angle + Math.PI),
                1, 0.02, 0.02, 0.02, 0
        );
    }

    public static void facePlayer(ItemDisplay display, Player player) {
        if (display == null || !display.isValid() || player == null || !player.isOnline()) {
            return;
        }
        Location from = display.getLocation();
        Location to = player.getEyeLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
        display.setBillboard(Display.Billboard.FIXED);
        display.setTeleportDuration(1);
        display.setRotation(yaw, 0f);
    }

    public static void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
}
