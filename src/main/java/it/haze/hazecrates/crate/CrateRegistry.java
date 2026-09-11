// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationRegistry;
import it.haze.hazecrates.config.MessageService;
import it.haze.hazecrates.gui.preview.CratePreviewConfig;
import it.haze.hazecrates.item.ExternalItemService;
import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public final class CrateRegistry {

    private final HazeCrates plugin;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public CrateRegistry(HazeCrates plugin) { this.plugin = plugin; }

    public void reload() {
        crates.clear();
        aliases.clear();
        File dir = new File(plugin.getDataFolder(), "crates");
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.saveResource("crates/example.yml", false);
            files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        }
        if (files != null) for (File f : files) load(f);
        plugin.getLogger().info("[HazeCrates] Loaded " + crates.size() + " crate(s).");
    }

    private void load(File file) {
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().replaceFirst("\\.yml$", "").toLowerCase(Locale.ROOT);
            ExternalItemService ext = plugin.externalItems();

            Material mat  = Optional.ofNullable(Material.matchMaterial(yml.getString("material","CHEST"))).orElse(Material.CHEST);
            boolean glow  = yml.getBoolean("glow", false);
            String anim = AnimationRegistry.normalize(
                    yml.getString("opening-animation", yml.getString("animation", "csgo")));
            String kt   = yml.getString("key-type", "PHYSICAL").toUpperCase(Locale.ROOT);
            KeyType keyType; try { keyType = KeyType.valueOf(kt); } catch (IllegalArgumentException e) { keyType = KeyType.PHYSICAL; }

            String rawKey = yml.getString("key.item", "TRIPWIRE_HOOK");
            ItemSpec keySpec = ItemSpec.parse(rawKey);

            CrateDisplayConfig display = loadDisplay(yml, mat, glow, id);
            CratePreviewConfig previewConfig = loadPreview(yml);

            List<RewardDefinition> rewards = loadRewards(yml, id, ext);
            if (rewards.isEmpty()) throw new IllegalArgumentException("no rewards defined");

            List<MilestoneDefinition> milestones = loadMilestones(yml);

            String displayName = yml.getString("display-name", "&b" + id);
            crates.put(id, new CrateDefinition(
                    id,
                    displayName,
                    mat, glow, keyType, keySpec,
                    anim,
                    display,
                    yml.getString("broadcast",""),
                    yml.getInt("broadcast-threshold", 0),
                    yml.getBoolean("preview-on-left-click",true),
                    previewConfig,
                    rewards, milestones));
            registerAlias(id, id);
            registerAlias(yml.getString("id"), id);
            for (String alias : yml.getStringList("aliases")) {
                registerAlias(alias, id);
            }
            String plain = plainName(displayName);
            registerAlias(plain, id);
            String[] words = plain.split("\\s+");
            if (words.length > 0) {
                registerAlias(words[words.length - 1], id);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Skipping invalid crate " + file.getName() + ": " + e.getMessage());
        }
    }

    private CrateDisplayConfig loadDisplay(YamlConfiguration yml, Material fallback, boolean fallbackGlow, String id) {
        CrateDisplayConfig def = CrateDisplayConfig.defaults();
        ConfigurationSection ds = yml.getConfigurationSection("display");

        String rawBlock = ds != null ? ds.getString("block","") : "";
        if (rawBlock.isBlank()) rawBlock = yml.getString("material","");
        ItemSpec blockSpec = rawBlock.isBlank() ? ItemSpec.vanilla(fallback.name()) : ItemSpec.parse(rawBlock);

        int cmd = ds != null ? ds.getInt("custom-model-data",0) : 0;
        if (cmd > 0 && blockSpec.provider() == it.haze.hazecrates.item.ItemProvider.VANILLA)
            blockSpec = ItemSpec.vanilla(blockSpec.id(), cmd);

        ConfigurationSection hs = ds != null ? ds.getConfigurationSection("hologram") : yml.getConfigurationSection("hologram");
        List<String> holoLines; double holoH; int holoR;
        if (hs != null) {
            holoLines = hs.getStringList("lines");
            holoH     = hs.getDouble("height-offset", def.hologramHeight());
            holoR     = hs.getInt("refresh-rate", def.hologramRefreshTicks());
        } else {
            holoLines = yml.getStringList("hologram.lines");
            holoH     = yml.getDouble("hologram.height-offset", def.hologramHeight());
            holoR     = yml.getInt("hologram.refresh-rate", def.hologramRefreshTicks());
        }
        if (holoLines.isEmpty()) holoLines = List.of("&b&l" + id.toUpperCase(Locale.ROOT), "&7Right-click with a key");

        Particle particle = null; int pc = 0; double pr = 0; int pi = 0;
        String idleEffect = yml.getString("idle-animation", yml.getString("idle-effect", "none"));
        if (ds != null) {
            String topIdle = ds.getString("idle-effect", ds.getString("idle-animation", ""));
            if (!topIdle.isBlank()) idleEffect = topIdle.toLowerCase(Locale.ROOT);

            ConfigurationSection ps = ds.getConfigurationSection("particles");
            if (ps != null) {
                idleEffect = ps.getString("effect", "");
                String pn = ps.getString("type","");
                if (!pn.isBlank()) try { particle = Particle.valueOf(pn.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
                pc = ps.getInt("count", 0);
                pr = ps.getDouble("radius", 0);
                pi = ps.getInt("interval-ticks", 0);

                if ((idleEffect == null || idleEffect.isBlank()) && particle != null) {
                    idleEffect = switch (particle) {
                        case FLAME -> "flame_ring";
                        case END_ROD -> "end_rod_orbit";
                        case SOUL_FIRE_FLAME -> "soul_helix";
                        case PORTAL -> "portal_spiral";
                        case ENCHANT -> "enchant_pulse";
                        case CHERRY_LEAVES -> "cherry_rain";
                        case HEART -> "heart_aura";
                        default -> "flame_ring";
                    };
                }
                if (idleEffect == null || idleEffect.isBlank()) idleEffect = "none";
            }
        }

        String titleText = ds != null ? ds.getString("title.text","") : "";
        String subtitle  = ds != null ? ds.getString("title.subtitle","") : "";
        int fi = ds != null ? ds.getInt("title.fade-in", def.titleFadeIn())  : def.titleFadeIn();
        int ts = ds != null ? ds.getInt("title.stay",    def.titleStay())    : def.titleStay();
        int fo = ds != null ? ds.getInt("title.fade-out",def.titleFadeOut()) : def.titleFadeOut();
        boolean glowOut = ds != null ? ds.getBoolean("glowing-outline", fallbackGlow) : fallbackGlow;

        return new CrateDisplayConfig(blockSpec, holoLines, holoH, holoR,
                idleEffect.toLowerCase(Locale.ROOT), particle, pc, pr, pi,
                titleText == null ? "" : titleText,
                subtitle  == null ? "" : subtitle,
                fi, ts, fo, glowOut);
    }

    private CratePreviewConfig loadPreview(YamlConfiguration yml) {
        ConfigurationSection ps = yml.getConfigurationSection("preview");
        if (ps != null) {
            return CratePreviewConfig.fromSection(ps);
        }
        ConfigurationSection defSec = plugin.getConfig().getConfigurationSection("default-preview");
        if (defSec != null) {
            return CratePreviewConfig.fromSection(defSec);
        }
        return CratePreviewConfig.defaults();
    }

    private List<RewardDefinition> loadRewards(YamlConfiguration yml, String crateId, ExternalItemService ext) {
        List<RewardDefinition> list = new ArrayList<>();
        for (Map<?,?> src : yml.getMapList("rewards")) {
            try {
                Map<String,Object> raw = new HashMap<>();
                src.forEach((k,v) -> raw.put(String.valueOf(k), v));

                String rid = String.valueOf(raw.get("id"));
                if (rid.equals("null") || rid.isBlank()) throw new IllegalArgumentException("missing id");
                int weight = ((Number) raw.getOrDefault("weight",1)).intValue();
                if (weight < 1) throw new IllegalArgumentException("weight < 1");

                String rawItem = String.valueOf(raw.getOrDefault("item",""));
                ItemSpec spec;
                if (!rawItem.isBlank() && !rawItem.equals("null")) spec = ItemSpec.parse(rawItem);
                else {
                    String mn  = String.valueOf(raw.getOrDefault("material","STONE"));
                    int mcmd   = ((Number) raw.getOrDefault("custom-model-data",0)).intValue();
                    spec = mcmd > 0 ? ItemSpec.vanilla(mn, mcmd) : ItemSpec.vanilla(mn);
                }

                int amount  = ((Number) raw.getOrDefault("amount",1)).intValue();
                String name = raw.get("name") == null ? null : String.valueOf(raw.get("name"));
                if (name != null && (name.isBlank() || name.equals("null") || MessageService.isBrokenLegacyHex(name))) {
                    name = null;
                }
                List<String> lore = castList(raw.get("lore"));
                boolean rGlow = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("glow",false)));
                boolean loreOverride = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("lore-override","false")));
                if (!loreOverride && lore != null && !lore.isEmpty()) {
                    loreOverride = true;
                }

                if (!loreOverride && spec.provider() != it.haze.hazecrates.item.ItemProvider.VANILLA) {
                    name = null;
                }
                org.bukkit.inventory.ItemStack icon = ext.resolve(spec, amount, name, loreOverride ? lore : null, rGlow);

                List<String> commands = castList(raw.get("commands"));
                String perm   = String.valueOf(raw.getOrDefault("permission",""));
                boolean bcast = !raw.containsKey("broadcast")
                        || Boolean.parseBoolean(String.valueOf(raw.get("broadcast")));
                int slot = -1;
                if (raw.containsKey("slot")) slot = ((Number) raw.get("slot")).intValue();
                else if (raw.containsKey("preview-slot")) slot = ((Number) raw.get("preview-slot")).intValue();

                list.add(new RewardDefinition(rid, spec, icon, commands, weight,
                        perm.equals("null") ? "" : perm, bcast, loreOverride, slot));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping reward in '" + crateId + "': " + e.getMessage());
            }
        }
        return list;
    }

    private List<MilestoneDefinition> loadMilestones(YamlConfiguration yml) {
        List<MilestoneDefinition> list = new ArrayList<>();
        ConfigurationSection ms = yml.getConfigurationSection("milestones");
        if (ms == null) return list;
        for (String key : ms.getKeys(false)) {
            ConfigurationSection m = ms.getConfigurationSection(key);
            if (m != null) list.add(new MilestoneDefinition(
                    key, m.getInt("openings-required"), m.getStringList("rewards"),
                    m.getBoolean("broadcast"), m.getBoolean("reset-after-claim")));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object v) {
        return v instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    public CrateDefinition get(String id) { return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT)); }

    public CrateDefinition findExact(String input) {
        if (input == null || input.isBlank()) return null;
        String key = input.trim().toLowerCase(Locale.ROOT);
        String aliased = aliases.get(key);
        if (aliased != null) return crates.get(aliased);
        return crates.get(key);
    }

    public CrateDefinition find(String input) {
        CrateDefinition exact = findExact(input);
        if (exact != null) return exact;
        if (input == null || input.isBlank()) return null;
        String key = input.trim().toLowerCase(Locale.ROOT);
        List<CrateDefinition> hits = new ArrayList<>();
        for (CrateDefinition crate : crates.values()) {
            if (crate.id().startsWith(key)) hits.add(crate);
        }
        if (hits.size() == 1) return hits.get(0);
        hits.clear();
        for (CrateDefinition crate : crates.values()) {
            if (crate.id().contains(key) || plainName(crate.displayName()).contains(key)) {
                hits.add(crate);
            }
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    public List<String> tabIds() {
        return crates.keySet().stream().sorted().toList();
    }

    public Collection<CrateDefinition> all() { return Collections.unmodifiableCollection(crates.values()); }

    private void registerAlias(String alias, String crateId) {
        if (alias == null || alias.isBlank()) return;
        aliases.putIfAbsent(alias.trim().toLowerCase(Locale.ROOT), crateId);
    }

    private static String plainName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("<[^>]+>", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
