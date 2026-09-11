// made by haze
package it.haze.hazecrates.animation.opening.world.support;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
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

    public static AnimationSession begin(HazeCrates plugin, Player player, Runnable reveal) {
        AnimationSession session = new AnimationSession(null, reveal);
        plugin.startAnimSession(player.getUniqueId(), session);
        return session;
    }

    public static ItemDisplay spawnItem(Location location, ItemStack item, float scale, boolean glow) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(item);
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

    public static void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }
}
