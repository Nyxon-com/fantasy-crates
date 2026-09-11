// made by haze
package it.haze.hazecrates.gui.preview;

import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record PreviewFillerConfig(
        ItemSpec itemSpec,
        String name,
        List<String> lore,
        boolean glow,
        int customModelData,
        List<Integer> slots,
        boolean fillEmpty
) {
    public static PreviewFillerConfig defaults() {
        return new PreviewFillerConfig(
                ItemSpec.vanilla("BLACK_STAINED_GLASS_PANE"),
                " ",
                List.of(),
                false,
                0,
                List.of(),
                true
        );
    }

    public static PreviewFillerConfig fromSection(ConfigurationSection section) {
        if (section == null) return defaults();

        String rawItem = section.getString("item", section.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemSpec spec = rawItem.isBlank() ? ItemSpec.vanilla("BLACK_STAINED_GLASS_PANE") : ItemSpec.parse(rawItem);
        int cmd = section.getInt("custom-model-data", 0);
        if (cmd > 0 && spec.provider() == it.haze.hazecrates.item.ItemProvider.VANILLA) {
            spec = ItemSpec.vanilla(spec.id(), cmd);
        }

        String name = section.getString("name", " ");
        List<String> lore = section.getStringList("lore");
        boolean glow = section.getBoolean("glow", false);
        List<Integer> slots = parseSlots(section.get("slots"));
        boolean fillEmpty = section.getBoolean("fill-empty", slots.isEmpty());

        return new PreviewFillerConfig(spec, name, lore, glow, cmd, slots, fillEmpty);
    }

    public static List<Integer> parseSlots(Object obj) {
        List<Integer> list = new ArrayList<>();
        if (obj == null) return list;
        if (obj instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) {
                    list.add(n.intValue());
                } else if (o != null) {
                    String str = String.valueOf(o).trim();
                    if (str.contains("-")) {
                        String[] parts = str.split("-");
                        try {
                            int start = Integer.parseInt(parts[0].trim());
                            int end = Integer.parseInt(parts[1].trim());
                            for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                                list.add(i);
                            }
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try {
                            list.add(Integer.parseInt(str));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } else if (obj instanceof String s) {
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (trimmed.contains("-")) {
                    String[] range = trimmed.split("-");
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                            list.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                } else if (!trimmed.isEmpty()) {
                    try {
                        list.add(Integer.parseInt(trimmed));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } else if (obj instanceof Number n) {
            list.add(n.intValue());
        }
        return list;
    }
}
