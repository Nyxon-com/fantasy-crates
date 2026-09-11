// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.config.MessageService;
import it.haze.hazecrates.gui.CrateEditorSession;
import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrateWriter {

    private final HazeCrates plugin;

    public CrateWriter(HazeCrates plugin) { this.plugin = plugin; }

    public void save(CrateEditorSession session) throws IOException {
        File cratesDir = new File(plugin.getDataFolder(), "crates");
        cratesDir.mkdirs();
        File file = new File(cratesDir, session.crateId().toLowerCase() + ".yml");

        YamlConfiguration yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        yml.set("display-name",       session.displayName());
        yml.set("key-type",           session.keyType());
        yml.set("opening-animation",  session.animation());
        yml.set("animation",          session.animation());
        yml.set("idle-animation",     session.particleType());
        yml.set("idle-effect",        session.particleType());
        yml.set("broadcast",          session.broadcast());
        yml.set("broadcast-threshold", session.broadcastThreshold());
        yml.set("preview-on-left-click", session.previewOnLeftClick());

        yml.set("key.item", session.keyItemSpec().serialize());

        yml.set("display.block",             session.blockSpec().serialize());
        yml.set("display.custom-model-data", session.blockCustomModelData());
        yml.set("display.glowing-outline",   session.glowingOutline());

        yml.set("display.hologram.lines",        session.hologramLines());
        yml.set("display.hologram.height-offset", session.hologramHeight());
        yml.set("display.hologram.refresh-rate",  session.hologramRefreshTicks());

        String effect = session.particleType();
        if (effect == null || effect.isBlank()) effect = "none";
        yml.set("display.particles.effect", effect.toLowerCase(java.util.Locale.ROOT));
        yml.set("display.particles.type", null);
        if (session.particleCount() > 0)
            yml.set("display.particles.count", session.particleCount());
        if (session.particleRadius() > 0)
            yml.set("display.particles.radius", session.particleRadius());
        if (session.particleIntervalTicks() > 0)
            yml.set("display.particles.interval-ticks", session.particleIntervalTicks());

        yml.set("display.title.text",     session.titleText());
        yml.set("display.title.subtitle", session.titleSubtitle());
        yml.set("display.title.fade-in",  session.titleFadeIn());
        yml.set("display.title.stay",     session.titleStay());
        yml.set("display.title.fade-out", session.titleFadeOut());

        yml.set("material", session.blockSpec().provider().name().equals("VANILLA")
                ? session.blockSpec().id()
                : "CHEST");
        yml.set("glow", session.glowingOutline());

        List<Map<String, Object>> rewardMaps = session.rewards().stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.id());

            map.put("item", r.itemSpec().serialize());

            map.put("amount", r.icon().getAmount());

            if (!r.liveProviderAppearance()) {
                String name = "";
                if (r.icon().hasItemMeta() && r.icon().getItemMeta().hasDisplayName()
                        && r.icon().getItemMeta().displayName() != null) {
                    name = MessageService.serialize(r.icon().getItemMeta().displayName());
                    if (MessageService.isBrokenLegacyHex(name)) {
                        name = "";
                    }
                }
                map.put("name", name);
            }

            map.put("lore-override", r.loreOverride());
            if (r.loreOverride() && r.icon().hasItemMeta() && r.icon().getItemMeta().hasLore()
                    && r.icon().getItemMeta().lore() != null) {
                map.put("lore", r.icon().getItemMeta().lore().stream()
                        .map(MessageService::serialize)
                        .toList());
            }
            map.put("glow",      !r.icon().getEnchantments().isEmpty());
            map.put("commands",  r.commands());
            map.put("weight",    r.weight());
            if (!r.permission().isBlank()) map.put("permission", r.permission());
            map.put("broadcast", r.broadcast());
            return map;
        }).toList();
        yml.set("rewards", rewardMaps);

        yml.save(file);
        plugin.crates().reload();

        plugin.startDisplay();
    }

    public boolean delete(String crateId) {
        File file = new File(new File(plugin.getDataFolder(), "crates"),
                crateId.toLowerCase() + ".yml");
        boolean deleted = file.exists() && file.delete();
        if (deleted) {
            plugin.crates().reload();
            plugin.startDisplay();
        }
        return deleted;
    }
}
