// made by haze
package it.haze.hazecrates.animation;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.idle.IdleEffectTemplate;
import it.haze.hazecrates.animation.opening.gui.CsgoScrollAnimation;
import it.haze.hazecrates.animation.opening.gui.RouletteSpinAnimation;
import it.haze.hazecrates.animation.opening.world.LootboxOpenAnimation;
import it.haze.hazecrates.animation.opening.world.ParticleSpiralAnimation;
import it.haze.hazecrates.animation.opening.world.RewardRollAnimation;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.KeyType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AnimationRegistry {

    private static final Set<String> OFFICIAL = Set.of("csgo", "roulette", "roll", "spiral", "lootbox");

    private final HazeCrates plugin;
    private final Map<String, CrateAnimation> opening = new HashMap<>();
    private final List<String> openingIds = new ArrayList<>();
    private final Map<String, IdleEffectTemplate> idle = new HashMap<>();
    private final List<String> idleIds = new ArrayList<>();

    public AnimationRegistry(HazeCrates plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        opening.clear();
        openingIds.clear();
        idle.clear();
        idleIds.clear();

        File file = new File(plugin.getDataFolder(), "animations.yml");
        if (!file.exists()) {
            plugin.saveResource("animations.yml", false);
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ensureSplitStructure(file, yml);
        yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection openingSec = firstSection(yml, "opening_animations", "opening");
        ConfigurationSection ambientSec = firstSection(yml, "ambient_effects", "idle");

        if (openingSec != null) {
            for (String key : openingSec.getKeys(false)) {
                ConfigurationSection section = openingSec.getConfigurationSection(key);
                if (section != null) {
                    loadOpening(key, section);
                }
            }
        }
        if (ambientSec != null) {
            for (String key : ambientSec.getKeys(false)) {
                ConfigurationSection section = ambientSec.getConfigurationSection(key);
                if (section != null) {
                    loadAmbient(key, section);
                }
            }
        }

        registerFallback("default", new CsgoScrollAnimation(plugin, AnimationTemplate.defaults("default")));
        registerFallback("lootbox", new LootboxOpenAnimation(plugin, AnimationTemplate.defaults("lootbox")));
        if (!idle.containsKey("none")) {
            idle.put("none", IdleEffectTemplate.none());
            idleIds.add(0, "none");
        }

        plugin.getLogger().info("[HazeCrates] Loaded " + opening.size()
                + " opening animation(s), " + idle.size() + " ambient effect(s).");
    }

    public CrateAnimation animationFor(CrateDefinition crate) {
        if (crate.keyType() == KeyType.LOOTBOX) {
            return animation("lootbox");
        }
        return animation(crate.animation());
    }

    public CrateAnimation animation(String id) {
        String key = normalize(id);
        CrateAnimation found = opening.get(key);
        if (found != null) {
            return found;
        }
        return opening.getOrDefault("default", new CsgoScrollAnimation(plugin, AnimationTemplate.defaults("default")));
    }

    public static String normalize(String id) {
        if (id == null || id.isBlank()) {
            return "csgo";
        }
        return switch (id.toLowerCase(Locale.ROOT).trim()) {
            case "csgo", "default" -> "csgo";
            case "roulette" -> "roulette";
            case "roll" -> "roll";
            case "spiral" -> "spiral";
            case "lootbox" -> "lootbox";
            case "falling_runes", "reward_scroll", "mystery_scroll", "scroll" -> "csgo";
            case "triple_forge", "reward_wheel", "fortune_wheel", "wheel" -> "roulette";
            case "cards", "sealed_doors", "reward_cards", "tarot_cards", "cardflip" -> "csgo";
            case "rising_obelisk", "altar_reveal", "pedestal", "floating_pedestal" -> "roll";
            case "phoenix_ashes", "rift_tear", "galaxy_burst", "vortex",
                    "vortex_lift", "vortex_storm", "cosmic_supernova" -> "spiral";
            case "sealed_reliquary", "chest_drop", "lootbox_unbox" -> "lootbox";
            default -> "csgo";
        };
    }

    public List<String> ids() {
        return Collections.unmodifiableList(openingIds);
    }

    public String cycleNext(String current) {
        if (openingIds.isEmpty()) {
            return "default";
        }
        String cur = normalize(current);
        int idx = openingIds.indexOf(cur);
        return openingIds.get((idx + 1) % openingIds.size());
    }

    public IdleEffectTemplate idle(String id) {
        if (id == null || id.isBlank()) {
            return IdleEffectTemplate.none();
        }
        IdleEffectTemplate template = idle.get(id.toLowerCase(Locale.ROOT));
        return template != null ? template : IdleEffectTemplate.none();
    }

    public List<String> idleIds() {
        return Collections.unmodifiableList(idleIds);
    }

    public String cycleIdleNext(String current) {
        if (idleIds.isEmpty()) {
            return "none";
        }
        String cur = current == null || current.isBlank() ? "none" : current.toLowerCase(Locale.ROOT);
        int idx = idleIds.indexOf(cur);
        return idleIds.get((idx + 1) % idleIds.size());
    }

    private void registerFallback(String id, CrateAnimation animation) {
        if (!opening.containsKey(id)) {
            opening.put(id, animation);
            if (OFFICIAL.contains(id) && !openingIds.contains(id)) {
                openingIds.add(id);
            }
        }
    }

    private void loadOpening(String key, ConfigurationSection section) {
        String id = key.toLowerCase(Locale.ROOT);
        if (!OFFICIAL.contains(id) && !id.equals("default")) {
            return;
        }
        try {
            AnimationTemplate template = parseOpeningTemplate(key, section);
            CrateAnimation animation = buildOpening(template, section.getString("type", id));
            opening.put(id, animation);
            if (OFFICIAL.contains(id) && !openingIds.contains(id)) {
                openingIds.add(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load opening animation '" + key + "': " + e.getMessage());
        }
    }

    private void loadAmbient(String key, ConfigurationSection section) {
        try {
            String id = key.toLowerCase(Locale.ROOT);
            idle.put(id, new IdleEffectTemplate(
                    id,
                    IdleEffectTemplate.IdleStyle.parse(section.getString("style", "RING")),
                    parseParticle(section.getString("particle", "FLAME")),
                    section.getInt("count", 4),
                    section.getDouble("radius", 0.6),
                    section.getDouble("height", 0.15),
                    Math.max(1, section.getInt("interval-ticks", 10))
            ));
            idleIds.add(id);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load ambient effect '" + key + "': " + e.getMessage());
        }
    }

    private AnimationTemplate parseOpeningTemplate(String id, ConfigurationSection section) {
        return new AnimationTemplate(
                id,
                parseParticle(section.getString("particle", "END_ROD")),
                parseSound(section.getString("sound", "BLOCK_NOTE_BLOCK_HAT")),
                parseSound(section.getString("final-sound", "ENTITY_PLAYER_LEVELUP")),
                (float) section.getDouble("volume", 1.0),
                (float) section.getDouble("pitch", 1.0),
                section.getInt("duration-ticks", 50),
                section.getBoolean("camera-lock", false)
        );
    }

    private CrateAnimation buildOpening(AnimationTemplate template, String type) {
        String kind = type == null ? "CSGO" : type.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (kind) {
            case "ROULETTE" -> new RouletteSpinAnimation(plugin, template);
            case "ROLL" -> new RewardRollAnimation(plugin, template);
            case "SPIRAL" -> new ParticleSpiralAnimation(plugin, template);
            case "LOOTBOX" -> new LootboxOpenAnimation(plugin, template);
            default -> new CsgoScrollAnimation(plugin, template);
        };
    }

    private static ConfigurationSection firstSection(YamlConfiguration yml, String primary, String fallback) {
        ConfigurationSection section = yml.getConfigurationSection(primary);
        return section != null ? section : yml.getConfigurationSection(fallback);
    }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.END_ROD;
        }
    }

    private static Sound parseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_NOTE_BLOCK_HAT;
        }
    }

    private void ensureSplitStructure(File liveFile, YamlConfiguration live) {
        try (InputStream in = plugin.getResource("animations.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String section : List.of("opening_animations", "ambient_effects", "opening", "idle")) {
                ConfigurationSection bundledSection = bundled.getConfigurationSection(section);
                if (bundledSection == null) {
                    continue;
                }
                ConfigurationSection liveSection = live.getConfigurationSection(section);
                if (liveSection == null) {
                    live.set(section, bundled.get(section));
                    changed = true;
                    continue;
                }
                for (String key : bundledSection.getKeys(false)) {
                    if (!liveSection.contains(key)) {
                        liveSection.set(key, bundledSection.get(key));
                        changed = true;
                    }
                }
            }
            if (changed) {
                live.save(liveFile);
            }
        } catch (Exception ignored) {
        }
    }
}
