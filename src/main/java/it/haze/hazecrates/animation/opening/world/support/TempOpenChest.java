// made by haze
package it.haze.hazecrates.animation.opening.world.support;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TempOpenChest {

    private static final Set<String> PROTECTED = ConcurrentHashMap.newKeySet();
    private static final Set<TempOpenChest> LIVE = ConcurrentHashMap.newKeySet();

    private final Block block;
    private final BlockData previous;
    private final boolean placed;

    private TempOpenChest(Block block, BlockData previous, boolean placed) {
        this.block = block;
        this.previous = previous;
        this.placed = placed;
    }

    public static TempOpenChest place(Location origin, Player player, Material type) {
        Material mat = lidded(type);
        Block spot = findSpot(origin, player);
        if (spot == null) {
            return null;
        }

        BlockData previous = spot.getBlockData().clone();
        BlockData data = mat.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facePlayer(player));
        }
        if (data instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        }
        spot.setBlockData(data, false);

        TempOpenChest chest = new TempOpenChest(spot, previous, true);
        PROTECTED.add(key(spot));
        LIVE.add(chest);
        return chest;
    }

    public Location origin() {
        return block.getLocation().add(0.5, 0.55, 0.5);
    }

    public void openLid() {
        if (block.getState() instanceof Lidded lidded) {
            lidded.open();
        }
    }

    public void closeLid() {
        if (block.getState() instanceof Lidded lidded) {
            lidded.close();
        }
    }

    public void restore() {
        closeLid();
        PROTECTED.remove(key(block));
        LIVE.remove(this);
        if (placed) {
            block.setBlockData(previous, false);
        }
    }

    public static boolean isProtected(Block block) {
        return block != null && PROTECTED.contains(key(block));
    }

    public static void restoreAll() {
        for (TempOpenChest chest : Set.copyOf(LIVE)) {
            try {
                chest.restore();
            } catch (Exception ignored) {
            }
        }
        LIVE.clear();
        PROTECTED.clear();
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    private static Material lidded(Material type) {
        if (type != null && isLidded(type)) {
            return type;
        }
        return Material.CHEST;
    }

    private static boolean isLidded(Material type) {
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.ENDER_CHEST
                || type == Material.BARREL
                || type.name().endsWith("SHULKER_BOX");
    }

    private static BlockFace facePlayer(Player player) {
        BlockFace facing = player.getFacing().getOppositeFace();
        if (facing == BlockFace.UP || facing == BlockFace.DOWN || facing == BlockFace.SELF) {
            facing = yawFace(player.getLocation().getYaw()).getOppositeFace();
        }
        return facing;
    }

    private static BlockFace yawFace(float yaw) {
        float rot = (yaw % 360 + 360) % 360;
        if (rot >= 45 && rot < 135) return BlockFace.WEST;
        if (rot >= 135 && rot < 225) return BlockFace.NORTH;
        if (rot >= 225 && rot < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private static Block findSpot(Location origin, Player player) {
        if (origin.getWorld() == null) {
            return null;
        }
        Block base = origin.getBlock();
        Block[] first = {
                base,
                base.getRelative(BlockFace.UP),
                player.getLocation().getBlock(),
                player.getLocation().getBlock().getRelative(BlockFace.UP)
        };
        for (Block block : first) {
            if (usable(block)) {
                return block;
            }
        }
        for (int y = 0; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = base.getRelative(x, y, z);
                    if (usable(block)) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private static boolean usable(Block block) {
        if (block == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        if (PROTECTED.contains(key(block))) {
            return false;
        }
        return block.isReplaceable() && !block.isLiquid();
    }
}
