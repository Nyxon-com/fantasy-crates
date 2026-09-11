// made by haze
package it.haze.hazecrates.key;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import it.haze.hazecrates.gui.preview.PreviewFormat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class LootboxLore {

    private LootboxLore() {}

    public static List<String> build(HazeCrates plugin, CrateDefinition crate) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> out = new ArrayList<>();
        String crateName = crate.displayName();

        List<String> header = cfg.getStringList("lootbox.header");
        if (header.isEmpty()) {
            header = cfg.getStringList("lootbox.lore");
        }
        for (String line : header) {
            out.add(apply(line, crateName, crate.rewards().size(), "", "", "", "", ""));
        }

        String template = cfg.getString("lootbox.reward-line",
                "<gray>• </gray><white>%reward%</white> <dark_gray>·</dark_gray> %rarity%");
        int total = crate.rewards().stream().mapToInt(RewardDefinition::weight).sum();
        for (RewardDefinition reward : crate.rewards()) {
            String tier = PreviewFormat.rarityTier(plugin, reward.weight(), total);
            String color = cfg.getString("lootbox.colors." + tier, "<white>");
            if (color == null || color.isBlank()) color = "<white>";
            out.add(apply(
                    template,
                    crateName,
                    crate.rewards().size(),
                    reward.plainName(),
                    String.valueOf(Math.max(1, reward.icon().getAmount())),
                    PreviewFormat.rarityMini(plugin, reward.weight(), total),
                    PreviewFormat.chanceMini(plugin, reward.weight(), total),
                    color
            ));
        }

        for (String line : cfg.getStringList("lootbox.footer")) {
            out.add(apply(line, crateName, crate.rewards().size(), "", "", "", "", ""));
        }
        return out;
    }

    private static String apply(
            String line,
            String crate,
            int count,
            String reward,
            String amount,
            String rarity,
            String chance,
            String color
    ) {
        if (line == null) return "";
        String close = closeTag(color);
        return line
                .replace("%crate%", crate)
                .replace("%count%", String.valueOf(count))
                .replace("%reward%", reward)
                .replace("%amount%", amount)
                .replace("%rarity%", rarity)
                .replace("%chance%", chance)
                .replace("%color_close%", close)
                .replace("%color%", color);
    }

    private static String closeTag(String open) {
        if (open == null || open.isBlank() || !open.startsWith("<")) {
            return "";
        }
        int nameEnd = 1;
        while (nameEnd < open.length() && Character.isLetter(open.charAt(nameEnd))) {
            nameEnd++;
        }
        if (nameEnd <= 1) {
            return "";
        }
        return "</" + open.substring(1, nameEnd) + ">";
    }
}
