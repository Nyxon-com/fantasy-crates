// made by haze
package it.haze.hazecrates.gui.preview;

import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record PreviewItemConfig(
        String id,
        String symbol,
        List<Integer> slots,
        ItemSpec itemSpec,
        int amount,
        String name,
        List<String> lore,
        boolean glow,
        int customModelData,
        String sound,
        String action
) {
    public static PreviewItemConfig fromSection(String id, ConfigurationSection section) {
        if (section == null) return null;

        String symbol = section.getString("symbol", section.getString("character", id));
        List<Integer> slots = new ArrayList<>();
        if (section.contains("slot")) {
            slots.add(section.getInt("slot"));
        }
        if (section.contains("slots")) {
            slots.addAll(PreviewFillerConfig.parseSlots(section.get("slots")));
        }

        String rawItem = section.getString("item", section.getString("material", "STONE"));
        ItemSpec spec = rawItem.isBlank() ? ItemSpec.vanilla("STONE") : ItemSpec.parse(rawItem);
        int cmd = section.getInt("custom-model-data", 0);
        if (cmd > 0 && spec.provider() == it.haze.hazecrates.item.ItemProvider.VANILLA) {
            spec = ItemSpec.vanilla(spec.id(), cmd);
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        String name = section.getString("name", "");
        List<String> lore = section.getStringList("lore");
        boolean glow = section.getBoolean("glow", false);
        String sound = section.getString("sound", "");
        String action = section.getString("action", "NONE").trim();

        return new PreviewItemConfig(id, symbol, slots, spec, amount, name, lore, glow, cmd, sound, action);
    }
}
