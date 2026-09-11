// made by haze
package it.haze.hazecrates.gui.preview;

import it.haze.hazecrates.HazeCrates;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PreviewFormat {

    private PreviewFormat() {}

    public static String chanceMini(HazeCrates plugin, int weight, int total) {
        FileConfiguration cfg = plugin.getConfig();
        if (total <= 0) {
            return cfg.getString("preview.chance.na", "<gray>N/A</gray>");
        }
        double pct = (double) weight / total * 100.0;
        String path;
        if      (pct >= cfg.getDouble("preview.chance-thresholds.high", 30)) path = "preview.chance.high";
        else if (pct >= cfg.getDouble("preview.chance-thresholds.mid",  10)) path = "preview.chance.mid";
        else if (pct >= cfg.getDouble("preview.chance-thresholds.low",   2)) path = "preview.chance.low";
        else                                                                  path = "preview.chance.epic";

        String template = cfg.getString(path, "");
        if (template == null || template.isBlank()) {
            return switch (path) {
                case "preview.chance.high" -> "<green>" + fmt(pct) + "</green>";
                case "preview.chance.mid"  -> "<yellow>" + fmt(pct) + "</yellow>";
                case "preview.chance.low"  -> "<gold>" + fmt(pct) + "</gold>";
                default                    -> "<red>" + fmt(pct) + "</red>";
            };
        }
        return template.replace("%chance%", fmt(pct));
    }

    public static String rarityMini(HazeCrates plugin, int weight, int total) {
        String tier = rarityTier(plugin, weight, total);
        String label = plugin.getConfig().getString("preview.rarity." + tier, "");
        if (label != null && !label.isBlank()) return label;
        return switch (tier) {
            case "common"    -> "<green>Common</green>";
            case "uncommon"  -> "<yellow>Uncommon</yellow>";
            case "rare"      -> "<gold>Rare</gold>";
            case "epic"      -> "<red>Epic</red>";
            case "legendary" -> "<light_purple><bold>Legendary</bold></light_purple>";
            default          -> "<gray>Unknown</gray>";
        };
    }

    public static String chanceLegacy(HazeCrates plugin, int weight, int total) {
        if (total <= 0) return "&7N/A";
        double pct = (double) weight / total * 100.0;
        FileConfiguration cfg = plugin.getConfig();
        String colour;
        if      (pct >= cfg.getDouble("preview.chance-thresholds.high", 30)) colour = "&a";
        else if (pct >= cfg.getDouble("preview.chance-thresholds.mid",  10)) colour = "&e";
        else if (pct >= cfg.getDouble("preview.chance-thresholds.low",   2)) colour = "&6";
        else                                                                 colour = "&c";
        return colour + fmt(pct);
    }

    public static String rarityLegacy(HazeCrates plugin, int weight, int total) {
        return switch (rarityTier(plugin, weight, total)) {
            case "common"    -> "&aCommon";
            case "uncommon"  -> "&eUncommon";
            case "rare"      -> "&6Rare";
            case "epic"      -> "&cEpic";
            case "legendary" -> "&5&lLegendary";
            default          -> "&7Unknown";
        };
    }

    public static String rarityTier(HazeCrates plugin, int weight, int total) {
        if (total <= 0) return "unknown";
        double pct = (double) weight / total * 100.0;
        FileConfiguration cfg = plugin.getConfig();
        if (pct >= cfg.getDouble("preview.rarity-thresholds.common",   50)) return "common";
        if (pct >= cfg.getDouble("preview.rarity-thresholds.uncommon", 20)) return "uncommon";
        if (pct >= cfg.getDouble("preview.rarity-thresholds.rare",      5)) return "rare";
        if (pct >= cfg.getDouble("preview.rarity-thresholds.epic",      1)) return "epic";
        return "legendary";
    }

    public static List<String> rewardLoreLines(HazeCrates plugin, int weight, int total, String id, String permission) {
        return rewardLoreLines(plugin, weight, total, id, permission, null);
    }

    public static List<String> rewardLoreLines(HazeCrates plugin, int weight, int total, String id, String permission, List<String> customTemplate) {
        Map<String, String> values = placeholders(plugin, weight, total, id, permission);
        List<String> raw = (customTemplate != null && !customTemplate.isEmpty())
                ? customTemplate
                : plugin.getConfig().getStringList("preview.reward-lore");
        if (raw.isEmpty()) {
            raw = List.of(
                    "",
                    "<dark_gray>────────────────────</dark_gray>",
                    "<gray>Possibilità  %chance%</gray>",
                    "<gray>Rarità       %rarity%</gray>",
                    "<dark_gray>────────────────────</dark_gray>"
            );
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(apply(line, values));
        }
        return out;
    }

    public static String permissionLine(HazeCrates plugin, String permission) {
        if (permission == null || permission.isBlank()) return "";
        String template = plugin.getConfig().getString("preview.reward-permission",
                "<dark_gray>  <gray>Requires: <red>%permission%</red></gray>");
        return apply(template, Map.of("permission", permission));
    }

    private static Map<String, String> placeholders(HazeCrates plugin, int weight, int total,
                                                    String id, String permission) {
        Map<String, String> m = new HashMap<>();
        m.put("chance", chanceMini(plugin, weight, total));
        m.put("rarity", rarityMini(plugin, weight, total));
        m.put("weight", String.valueOf(weight));
        m.put("total", String.valueOf(total));
        m.put("id", id == null ? "" : id);
        m.put("permission", permission == null ? "" : permission);
        return m;
    }

    private static String apply(String line, Map<String, String> values) {
        if (line == null) return "";
        String out = line;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue());
        }
        return out;
    }

    private static String fmt(double pct) {
        return String.format(Locale.US, "%.2f%%", pct);
    }
}
