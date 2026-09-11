// made by haze
package it.haze.hazecrates.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class GuiItems {

    private static final ItemStack FILLER_DARK;
    private static final ItemStack FILLER_LIGHT;
    private static final ItemStack FILLER_BLACK;

    static {
        FILLER_DARK  = pane(Material.GRAY_STAINED_GLASS_PANE);
        FILLER_LIGHT = pane(Material.GRAY_STAINED_GLASS_PANE);
        FILLER_BLACK = pane(Material.BLACK_STAINED_GLASS_PANE);
    }

    private static ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(m);
        return item;
    }

    public static ItemStack filler()      { return FILLER_DARK.clone(); }

    public static ItemStack fillerLight() { return FILLER_LIGHT.clone(); }

    public static ItemStack fillerBlack() { return FILLER_BLACK.clone(); }

    public static void fillAll(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, fillerBlack());
    }

    public static void fillBorder(Inventory inv) {
        int rows = inv.getSize() / 9;
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, filler());
            if (rows > 1) inv.setItem((rows - 1) * 9 + col, filler());
        }
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler());
            inv.setItem(row * 9 + 8, filler());
        }
    }

    public static void fillBorder27(Inventory inv) {
        fillBorder(inv);
    }

    public static ItemStack of(Material mat, String name, String... lore) {
        return of(mat, 1, name, lore);
    }

    public static ItemStack of(Material mat, int amount, String name, String... lore) {
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy(name));
        if (lore.length > 0)
            meta.lore(Arrays.stream(lore).map(GuiItems::legacy).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack ofGlow(Material mat, String name, String... lore) {
        ItemStack item = of(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack back() {
        return of(Material.ARROW,
                "&f← &7Indietro",
                "&8Torna alla schermata precedente");
    }

    public static ItemStack save() {
        return ofGlow(Material.EMERALD,
                "&aSalva",
                "&7Scrive le modifiche nel file YAML",
                "&8e ricarica il registro crate");
    }

    public static ItemStack delete() {
        return of(Material.BARRIER,
                "&cElimina crate",
                "&7Rimuove definitivamente il file",
                "&c&lAzione irreversibile!");
    }

    public static ItemStack add(String label) {
        return ofGlow(Material.LIME_STAINED_GLASS_PANE,
                "&a" + label,
                "&7Clicca per creare");
    }

    public static ItemStack toggle(String label, boolean value) {
        return of(value ? Material.LIME_DYE : Material.RED_DYE,
                label,
                value ? "&a● &fAttivo  &8(clicca per disattivare)"
                      : "&c● &fDisattivo &8(clicca per attivare)");
    }

    public static ItemStack separator() {
        return of(Material.GRAY_STAINED_GLASS_PANE, "&8─────────────────");
    }

    public static String probabilityColour(int weight, int totalWeight) {
        if (totalWeight == 0) return "&7N/A";
        double pct = (double) weight / totalWeight * 100.0;
        String colour;
        if      (pct >= 30) colour = "&a";
        else if (pct >= 10) colour = "&e";
        else if (pct >=  2) colour = "&6";
        else                colour = "&c";
        return colour + String.format(java.util.Locale.US, "%.2f%%", pct);
    }

    public static String rarityLabel(int weight, int totalWeight) {
        if (totalWeight == 0) return "&7Sconosciuto";
        double pct = (double) weight / totalWeight * 100.0;
        if      (pct >= 50) return "&aComune";
        else if (pct >= 20) return "&eNon comune";
        else if (pct >=  5) return "&6Raro";
        else if (pct >=  1) return "&cEpico";
        else                return "&5&lLeggendario";
    }

    public static Component legacy(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        String converted = it.haze.hazecrates.config.MessageService.legacyToMini(s);
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(converted)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("&x(&[0-9a-fA-F]){6}", "")
                .replaceAll("§x(§[0-9a-fA-F]){6}", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private GuiItems() {}
}
