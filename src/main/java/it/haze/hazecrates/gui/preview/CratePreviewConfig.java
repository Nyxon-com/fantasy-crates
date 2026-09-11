// made by haze
package it.haze.hazecrates.gui.preview;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public record CratePreviewConfig(
        boolean enabled,
        String title,
        int rows,
        List<Integer> rewardSlots,
        PreviewFillerConfig filler,
        Map<String, PreviewItemConfig> items,
        List<String> rewardLore
) {
    public static CratePreviewConfig defaults() {
        return new CratePreviewConfig(
                true,
                null,
                6,
                defaultInnerSlots(6),
                PreviewFillerConfig.defaults(),
                Map.of(),
                List.of()
        );
    }

    public static CratePreviewConfig fromSection(ConfigurationSection section) {
        if (section == null) return defaults();

        boolean enabled = section.getBoolean("enabled", true);
        String title = section.getString("title", null);

        List<Integer> rewardSlots = new ArrayList<>();
        Map<String, PreviewItemConfig> items = new LinkedHashMap<>();

        ConfigurationSection slotsSec = section.getConfigurationSection("slots");
        int rowsFromSlots = parseSlotMap(slotsSec, rewardSlots, items);

        List<String> pattern = section.getStringList("pattern");
        if (pattern.isEmpty()) pattern = section.getStringList("schema");
        if (pattern.isEmpty()) pattern = section.getStringList("layout");

        int rows = section.getInt("rows", section.getInt("size", 0) / 9);
        if (rowsFromSlots > 0) {
            rows = Math.max(rows, rowsFromSlots);
        }
        if (!pattern.isEmpty() && slotsSec == null) {
            rows = Math.max(1, Math.min(6, pattern.size()));
        } else if (rows <= 0) {
            rows = 6;
        }
        rows = Math.max(1, Math.min(6, rows));

        if (section.contains("reward-slots")) {
            rewardSlots.addAll(PreviewFillerConfig.parseSlots(section.get("reward-slots")));
        } else if (section.contains("slots") && !(section.get("slots") instanceof org.bukkit.configuration.ConfigurationSection)) {
            rewardSlots.addAll(PreviewFillerConfig.parseSlots(section.get("slots")));
        }

        PreviewFillerConfig filler = PreviewFillerConfig.fromSection(section.getConfigurationSection("filler"));

        ConfigurationSection itemsSec = section.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection is = itemsSec.getConfigurationSection(key);
                if (is != null) {
                    PreviewItemConfig item = PreviewItemConfig.fromSection(key, is);
                    if (item != null) items.put(key, item);
                }
            }
        }

        ConfigurationSection buttonsSec = section.getConfigurationSection("buttons");
        if (buttonsSec != null) {
            for (String key : buttonsSec.getKeys(false)) {
                ConfigurationSection bs = buttonsSec.getConfigurationSection(key);
                if (bs != null) {
                    PreviewItemConfig item = PreviewItemConfig.fromSection(key, bs);
                    if (item != null) items.put(key, item);
                }
            }
        }

        if (!pattern.isEmpty() && slotsSec == null) {
            applyPattern(pattern, rows, rewardSlots, items);
        }

        if (rewardSlots.isEmpty()) {
            rewardSlots.addAll(defaultInnerSlots(rows));
        }

        List<String> rewardLore = section.getStringList("reward-lore");

        return new CratePreviewConfig(enabled, title, rows, rewardSlots, filler, items, rewardLore);
    }

    private static int parseSlotMap(
            ConfigurationSection slotsSec,
            List<Integer> rewardSlots,
            Map<String, PreviewItemConfig> items
    ) {
        if (slotsSec == null) return 0;
        int maxSlot = -1;
        for (String key : slotsSec.getKeys(false)) {
            List<Integer> nums = PreviewFillerConfig.parseSlots(key.contains(",") || key.contains("-") ? key : List.of(key));
            if (nums.isEmpty()) {
                try {
                    nums = List.of(Integer.parseInt(key.trim()));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            for (int n : nums) maxSlot = Math.max(maxSlot, n);

            if (slotsSec.isConfigurationSection(key)) {
                ConfigurationSection cs = slotsSec.getConfigurationSection(key);
                if (cs == null) continue;
                if (cs.getString("type", "").equalsIgnoreCase("reward")
                        || cs.getString("item", "").equalsIgnoreCase("reward")) {
                    rewardSlots.addAll(nums);
                    continue;
                }
                PreviewItemConfig item = PreviewItemConfig.fromSection(key, cs);
                if (item == null) continue;
                List<Integer> merged = new ArrayList<>(item.slots());
                merged.addAll(nums);
                items.put(key, new PreviewItemConfig(
                        item.id(), item.symbol(), merged, item.itemSpec(), item.amount(),
                        item.name(), item.lore(), item.glow(), item.customModelData(), item.sound(), item.action()
                ));
            } else {
                String value = String.valueOf(slotsSec.get(key)).trim();
                if (value.equalsIgnoreCase("reward") || value.equalsIgnoreCase("R")) {
                    rewardSlots.addAll(nums);
                }
            }
        }
        if (maxSlot < 0) return 0;
        return Math.min(6, maxSlot / 9 + 1);
    }

    private static void applyPattern(
            List<String> pattern,
            int rows,
            List<Integer> rewardSlots,
            Map<String, PreviewItemConfig> items
    ) {
        Map<String, List<Integer>> symbolSlots = new HashMap<>();
        for (int r = 0; r < rows && r < pattern.size(); r++) {
            String line = pattern.get(r).trim();
            String[] tokens = line.contains(" ") ? line.split("\\s+") : line.split("");
            for (int c = 0; c < 9 && c < tokens.length; c++) {
                String token = tokens[c].trim();
                if (token.isEmpty() || token.equals(".") || token.equals("_")) continue;
                int slot = r * 9 + c;
                if (token.equalsIgnoreCase("R") || token.equalsIgnoreCase("REWARD")) {
                    rewardSlots.add(slot);
                } else {
                    symbolSlots.computeIfAbsent(token, k -> new ArrayList<>()).add(slot);
                }
            }
        }
        for (Map.Entry<String, List<Integer>> entry : symbolSlots.entrySet()) {
            String symbol = entry.getKey();
            List<Integer> slots = entry.getValue();
            for (Map.Entry<String, PreviewItemConfig> itemEntry : items.entrySet()) {
                PreviewItemConfig itm = itemEntry.getValue();
                if (itm.id().equalsIgnoreCase(symbol) || (itm.symbol() != null && itm.symbol().equalsIgnoreCase(symbol))) {
                    List<Integer> merged = new ArrayList<>(itm.slots());
                    merged.addAll(slots);
                    items.put(itemEntry.getKey(), new PreviewItemConfig(
                            itm.id(), itm.symbol(), merged, itm.itemSpec(), itm.amount(),
                            itm.name(), itm.lore(), itm.glow(), itm.customModelData(), itm.sound(), itm.action()
                    ));
                }
            }
        }
    }

    public static List<Integer> defaultInnerSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        if (rows <= 2) {
            for (int i = 0; i < rows * 9; i++) slots.add(i);
            return slots;
        }
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c <= 7; c++) {
                slots.add(r * 9 + c);
            }
        }
        return slots;
    }
}
