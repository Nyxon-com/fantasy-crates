// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class CratePlacementService {

    public static final String PDC_KEY = "crate_block";

    private final HazeCrates plugin;
    private final NamespacedKey crateBlockKey;
    private final File storageFile;
    private final Map<String, String> placements = new LinkedHashMap<>();

    public CratePlacementService(HazeCrates plugin) {
        this.plugin = plugin;
        this.crateBlockKey = new NamespacedKey(plugin, PDC_KEY);
        this.storageFile = new File(plugin.getDataFolder(), "placed_crates.yml");
        load();
    }

    public void scanAndRepair() {
        int found = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState ts)) continue;
                    String crateId = ts.getPersistentDataContainer()
                            .get(crateBlockKey, PersistentDataType.STRING);
                    if (crateId == null) continue;
                    String k = key(ts.getLocation());
                    if (!placements.containsKey(k)) {
                        placements.put(k, crateId);
                        found++;
                        plugin.getLogger().info("[HazeCrates] Recovered missing placement: "
                                + crateId + " at " + k);
                    }
                }
            }
        }
        if (found > 0) {
            save();
            plugin.getLogger().info("[HazeCrates] Recovered " + found + " missing crate placements.");
        }
    }

    public void place(Location loc, CrateDefinition crate) {
        Block block = loc.getBlock();

        if (block.getState() instanceof TileState state) {
            state.getPersistentDataContainer()
                    .set(crateBlockKey, PersistentDataType.STRING, crate.id());
            state.update(true, false);
        }

        placements.put(key(loc), crate.id());
        save();
    }

    public void remove(Location loc) {
        Block block = loc.getBlock();
        if (block.getState() instanceof TileState state) {
            state.getPersistentDataContainer().remove(crateBlockKey);
            state.update(true, false);
        }
        placements.remove(key(loc));
        save();
    }

    public String crateIdAt(Location loc) {
        return placements.get(key(loc));
    }

    public CrateDefinition crateAt(Location loc) {
        String id = crateIdAt(loc);
        return id == null ? null : plugin.crates().get(id);
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(placements);
    }

    public static Location fromKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        World world = org.bukkit.Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void load() {
        placements.clear();
        if (!storageFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(storageFile);
        for (Map<?, ?> entry : yml.getMapList("placements")) {
            try {
                String worldName = String.valueOf(entry.get("world"));
                int x = ((Number) entry.get("x")).intValue();
                int y = ((Number) entry.get("y")).intValue();
                int z = ((Number) entry.get("z")).intValue();
                String crateId = String.valueOf(entry.get("crate"));
                placements.put(worldName + "," + x + "," + y + "," + z, crateId);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping malformed placement entry: " + e.getMessage());
            }
        }
        plugin.getLogger().info("[HazeCrates] Loaded " + placements.size() + " crate placements.");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : placements.entrySet()) {
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", parts[0]);
            map.put("x", Integer.parseInt(parts[1]));
            map.put("y", Integer.parseInt(parts[2]));
            map.put("z", Integer.parseInt(parts[3]));
            map.put("crate", entry.getValue());
            list.add(map);
        }
        yml.set("placements", list);
        try {
            yml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save placed_crates.yml: " + e.getMessage(), e);
        }
    }

    public static String key(Location loc) {
        return Objects.requireNonNull(loc.getWorld()).getName()
                + "," + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
    }}
