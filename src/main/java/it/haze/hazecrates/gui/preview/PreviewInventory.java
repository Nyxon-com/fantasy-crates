// made by haze
package it.haze.hazecrates.gui.preview;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static it.haze.hazecrates.gui.GuiItems.strip;

public final class PreviewInventory {

    public static final String TITLE_PREFIX = "Preview: ";

    private PreviewInventory() {}

    public static void open(Player player, CrateDefinition crate, HazeCrates plugin) {
        open(player, crate, plugin, 1);
    }

    public static void open(Player player, CrateDefinition crate, HazeCrates plugin, int page) {
        CratePreviewConfig preview = crate.previewConfig() != null
                ? crate.previewConfig()
                : CratePreviewConfig.defaults();

        if (!preview.enabled()) return;

        List<RewardDefinition> allRewards = new ArrayList<>(crate.rewards());
        allRewards.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        int totalWeight = allRewards.stream().mapToInt(RewardDefinition::weight).sum();

        int rows = Math.max(1, Math.min(6, preview.rows()));
        int size = rows * 9;
        int keysCount = countPhysicalKeys(player, crate, plugin);

        List<RewardDefinition> fixedRewards = new ArrayList<>();
        List<RewardDefinition> autoRewards = new ArrayList<>();
        for (RewardDefinition r : allRewards) {
            if (r.previewSlot() >= 0 && r.previewSlot() < size) {
                fixedRewards.add(r);
            } else {
                autoRewards.add(r);
            }
        }

        Set<Integer> occupiedSlots = new HashSet<>();
        for (RewardDefinition r : fixedRewards) occupiedSlots.add(r.previewSlot());
        for (PreviewItemConfig itm : preview.items().values()) {
            occupiedSlots.addAll(itm.slots());
        }

        List<Integer> availableRewardSlots = new ArrayList<>();
        for (int s : preview.rewardSlots()) {
            if (s >= 0 && s < size && !occupiedSlots.contains(s)) {
                availableRewardSlots.add(s);
            }
        }

        if (availableRewardSlots.isEmpty()) {
            for (int s = 0; s < size; s++) {
                if (!occupiedSlots.contains(s)) availableRewardSlots.add(s);
            }
        }

        int pageSize = Math.max(1, availableRewardSlots.size());
        int totalPages = Math.max(1, (int) Math.ceil((double) autoRewards.size() / pageSize));
        int currentPage = Math.max(1, Math.min(page, totalPages));

        int startIdx = (currentPage - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, autoRewards.size());
        List<RewardDefinition> pageRewards = autoRewards.subList(startIdx, endIdx);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("crate", crate.displayName());
        placeholders.put("crate_plain", strip(crate.displayName()));
        placeholders.put("player", player.getName());
        placeholders.put("page", String.valueOf(currentPage));
        placeholders.put("total_pages", String.valueOf(totalPages));
        placeholders.put("prev_page", String.valueOf(Math.max(1, currentPage - 1)));
        placeholders.put("next_page", String.valueOf(Math.min(totalPages, currentPage + 1)));
        placeholders.put("rewards", String.valueOf(allRewards.size()));
        placeholders.put("totalweight", String.valueOf(totalWeight));
        placeholders.put("keys", String.valueOf(keysCount));

        String titleRaw = preview.title();
        if (titleRaw == null || titleRaw.isBlank()) {
            titleRaw = plugin.messages().raw("preview-title", Map.of("crate", strip(crate.displayName())));
        }
        titleRaw = applyPlaceholders(titleRaw, placeholders);
        if (PreviewTheme.genericTitle(titleRaw)) {
            titleRaw = PreviewTheme.vaultTitle(strip(crate.displayName()));
        }
        Component title = plugin.messages().parse(titleRaw);

        PreviewHolder holder = new PreviewHolder(crate, currentPage, totalPages);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        PreviewTheme.paintVault(inv);

        for (PreviewItemConfig itemCfg : preview.items().values()) {
            String action = itemCfg.action() == null ? "NONE" : itemCfg.action();
            boolean clickable = !action.isBlank() && !action.equalsIgnoreCase("NONE");
            boolean isPrev = action.equalsIgnoreCase("PREVIOUS_PAGE");
            boolean isNext = action.equalsIgnoreCase("NEXT_PAGE");
            boolean hideNav = (isPrev && currentPage <= 1) || (isNext && currentPage >= totalPages);

            for (int slot : itemCfg.slots()) {
                if (slot < 0 || slot >= size) {
                    continue;
                }
                if (hideNav) {
                    inv.setItem(slot, PreviewTheme.socket());
                    continue;
                }
                ItemStack is = buildPreviewItem(itemCfg, plugin, placeholders);
                PreviewTheme.silence(is);
                inv.setItem(slot, is);
                if (clickable) {
                    holder.registerAction(slot, action);
                    holder.registerSound(slot, itemCfg.sound());
                }
            }
        }

        for (RewardDefinition r : fixedRewards) {
            inv.setItem(r.previewSlot(), buildRewardIcon(r, totalWeight, plugin, preview.rewardLore()));
        }

        List<Integer> placed = centerInGrid(availableRewardSlots, pageRewards.size());
        for (int i = 0; i < availableRewardSlots.size(); i++) {
            int slot = availableRewardSlots.get(i);
            int placedAt = placed.indexOf(slot);
            if (placedAt >= 0 && placedAt < pageRewards.size()) {
                inv.setItem(slot, buildRewardIcon(pageRewards.get(placedAt), totalWeight, plugin, preview.rewardLore()));
            } else if (preview.rewardSlots().contains(slot)) {
                inv.setItem(slot, PreviewTheme.socket());
            }
        }

        ItemStack backdrop = PreviewTheme.socket();
        PreviewFillerConfig filler = preview.filler();
        if (filler != null) {
            backdrop = buildFillerItem(filler, plugin, placeholders);
            PreviewTheme.silence(backdrop);
        }
        for (int i = 0; i < size; i++) {
            ItemStack current = inv.getItem(i);
            if (current == null || current.getType().isAir()) {
                inv.setItem(i, backdrop.clone());
            }
        }

        player.openInventory(inv);
    }

    static List<Integer> centerInGrid(List<Integer> slots, int count) {
        List<Integer> placed = new ArrayList<>();
        if (count <= 0 || slots.isEmpty()) {
            return placed;
        }
        if (count >= slots.size()) {
            placed.addAll(slots);
            return placed;
        }

        int minCol = 8;
        int maxCol = 0;
        int minRow = 5;
        int maxRow = 0;
        for (int slot : slots) {
            minCol = Math.min(minCol, slot % 9);
            maxCol = Math.max(maxCol, slot % 9);
            minRow = Math.min(minRow, slot / 9);
            maxRow = Math.max(maxRow, slot / 9);
        }
        int cols = Math.max(1, maxCol - minCol + 1);
        int rows = Math.max(1, maxRow - minRow + 1);
        int usedRows = Math.min(rows, (count + cols - 1) / cols);
        int startRow = minRow + (rows - usedRows) / 2;
        int remaining = count;

        for (int r = 0; r < usedRows && remaining > 0; r++) {
            int rowCount = (r == usedRows - 1) ? remaining : Math.min(cols, remaining);
            int startCol = minCol + (cols - rowCount) / 2;
            for (int c = 0; c < rowCount; c++) {
                int slot = (startRow + r) * 9 + (startCol + c);
                if (slots.contains(slot)) {
                    placed.add(slot);
                    remaining--;
                }
            }
        }

        if (placed.size() < count) {
            for (int slot : slots) {
                if (!placed.contains(slot)) {
                    placed.add(slot);
                    if (placed.size() >= count) {
                        break;
                    }
                }
            }
        }
        return placed;
    }

    private static ItemStack buildRewardIcon(RewardDefinition r, int totalWeight, HazeCrates plugin, List<String> customLoreTemplate) {
        ItemStack item = r.icon().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<Component> lore = meta.lore() == null
                ? new ArrayList<>()
                : new ArrayList<>(meta.lore());

        List<String> extra = PreviewFormat.rewardLoreLines(
                plugin, r.weight(), totalWeight, r.id(), r.permission(), customLoreTemplate);
        for (String line : extra) {
            lore.add(plugin.messages().parse(line));
        }

        if (!r.permission().isBlank()) {
            String permLine = PreviewFormat.permissionLine(plugin, r.permission());
            if (!permLine.isBlank() && extra.stream().noneMatch(l -> l.contains("%permission%"))) {
                lore.add(plugin.messages().parse(permLine));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        PreviewTheme.silence(item);
        return item;
    }

    private static ItemStack buildPreviewItem(PreviewItemConfig cfg, HazeCrates plugin, Map<String, String> placeholders) {
        return plugin.externalItems().resolve(
                cfg.itemSpec(),
                cfg.amount(),
                cfg.name().isBlank() ? null : applyPlaceholders(cfg.name(), placeholders),
                cfg.lore().isEmpty() ? null : cfg.lore().stream().map(l -> applyPlaceholders(l, placeholders)).toList(),
                cfg.glow()
        );
    }

    private static ItemStack buildFillerItem(PreviewFillerConfig cfg, HazeCrates plugin, Map<String, String> placeholders) {
        return plugin.externalItems().resolve(
                cfg.itemSpec(),
                1,
                cfg.name().isBlank() ? " " : applyPlaceholders(cfg.name(), placeholders),
                cfg.lore().isEmpty() ? null : cfg.lore().stream().map(l -> applyPlaceholders(l, placeholders)).toList(),
                cfg.glow()
        );
    }

    public static int countPhysicalKeys(Player player, CrateDefinition crate, HazeCrates plugin) {
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && plugin.keys().isKey(is, crate.id())) {
                count += is.getAmount();
            }
        }
        return count;
    }

    public static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        String res = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            res = res.replace("%" + e.getKey() + "%", e.getValue());
        }
        return res;
    }
}
