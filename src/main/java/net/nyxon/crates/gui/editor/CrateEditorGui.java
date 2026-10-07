// made by nyxon
package net.nyxon.crates.gui;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.item.ItemSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static net.nyxon.crates.gui.GuiItems.*;

public final class CrateEditorGui {

    public static final String TAG = "Modifica:";

    static final int SLOT_NAME      = 10;
    static final int SLOT_MATERIAL  = 11;
    static final int SLOT_GLOW      = 12;
    static final int SLOT_KEYTYPE   = 13;
    static final int SLOT_KEY_ITEM  = 14;
    static final int SLOT_ANIMATION = 15;
    static final int SLOT_HOLOGRAM  = 16;

    static final int SLOT_BROADCAST    = 28;
    static final int SLOT_BCAST_THRESH = 29;
    static final int SLOT_PARTICLE     = 30;
    static final int SLOT_TITLE        = 31;
    static final int SLOT_SUBTITLE     = 32;
    static final int SLOT_PREVIEW_TOGGLE = 33;

    static final int SLOT_REWARDS = 40;

    static final int SLOT_BACK   = 45;
    static final int SLOT_SAVE   = 49;
    static final int SLOT_DELETE = 53;

    private static final int[] SEP = {18,19,20,21,22,23,24,25,26};

    private CrateEditorGui() {}

    public static void open(Player player, NyxonCrates plugin, CrateEditorSession session) {
        String title = (session.isNew() ? "<gold>Nuova crate: </gold>" : "<gold>Modifica: </gold>") + session.crateId();
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.messages().parse(title));

        fillBorder(inv);
        for (int s : SEP) inv.setItem(s, fillerBlack());

        inv.setItem(SLOT_NAME, of(Material.NAME_TAG,
                "&bNome mostrato",
                "&8Attuale: &f" + strip(session.displayName()),
                "",
                "&7Clicca → scrivi in chat"));

        ItemStack blockItem = new ItemStack(Material.BARRIER);
        ItemMeta blockMeta = blockItem.getItemMeta();
        blockMeta.displayName(legacy("&bBlocco crate"));
        blockMeta.lore(List.of(
                legacy(""),
                legacy("&7La crate si attacca al blocco"),
                legacy("&7che clicchi con l item crate."),
                legacy(""),
                legacy("&7Clic destro su un blocco per posarla")));
        blockItem.setItemMeta(blockMeta);
        inv.setItem(SLOT_MATERIAL, blockItem);

        inv.setItem(SLOT_GLOW, toggle("&bContorno luminoso", session.glowingOutline()));

        inv.setItem(SLOT_KEYTYPE, of(Material.TRIPWIRE_HOOK,
                "&bTipo chiave",
                "&8Attuale: &e" + session.keyType(),
                "",
                "&7Clicca per cambiare:",
                "&7 • &ePHYSICAL &7(blocco + chiave in mano)",
                "&7 • &eVIRTUAL &7(blocco + saldo virtuale)",
                "&7 • &eLOOTBOX &7(item in mano, click destro)"));

        String keyLabel = session.keyItemSpec() != null ? formatSpec(session.keyItemSpec()) : "TRIPWIRE_HOOK";
        ItemStack keyPreview = plugin.externalItems().resolve(
                session.keyItemSpec() != null ? session.keyItemSpec() : ItemSpec.vanilla("TRIPWIRE_HOOK"));
        ItemMeta keyMeta = keyPreview.getItemMeta();
        if (keyMeta != null) {
            keyMeta.displayName(legacy("&bItem chiave"));
            keyMeta.lore(List.of(
                    legacy("&8Attuale: " + keyLabel),
                    legacy(""),
                    legacy("&8Formati: MATERIAL, nexo:id,"),
                    legacy("&8mmoitems:TYPE:ID, itemsadder:ns:id"),
                    legacy(""),
                    legacy("&7Tieni l item Nexo e cliccalo qui"),
                    legacy("&7oppure clicca a vuoto e scrivi l id")));
            keyPreview.setItemMeta(keyMeta);
        }
        inv.setItem(SLOT_KEY_ITEM, keyPreview);

        String animList = String.join("&7, &e", plugin.animations().ids());
        if (animList.isBlank()) animList = "default";
        inv.setItem(SLOT_ANIMATION, of(Material.CLOCK,
                "&bAnimazione",
                "&8Attuale: &e" + session.animation(),
                "",
                "&7Disponibili: &e" + animList,
                "",
                "&aClicca per cambiare"));

        inv.setItem(SLOT_HOLOGRAM, of(Material.OAK_SIGN,
                "&bLinee ologramma",
                "&8Linee: &f" + session.hologramLines().size(),
                "&8Prima: &f" + strip(session.hologramLines().isEmpty() ? "" : session.hologramLines().get(0)),
                "",
                "&7Separa le linee con &e|",
                "&7Clicca → scrivi in chat"));

        String bcast = session.broadcast().isBlank() ? "&8(disabled)" : strip(session.broadcast());
        inv.setItem(SLOT_BROADCAST, of(Material.GOAT_HORN,
                "&bMessaggio broadcast",
                "&8Attuale: &f" + bcast,
                "",
                "&7Placeholder: &e%player%&7, &e%reward%",
                "&7Lascia vuoto per disattivare",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_BCAST_THRESH, of(Material.COMPARATOR,
                "&bSoglia broadcast",
                "&80 = annuncio per ogni premio",
                "&8>0 = solo se peso ≤ valore",
                "&8Attuale: &f" + session.broadcastThreshold(),
                "",
                "&7Clicca → scrivi in chat"));

        String idleId = session.particleType().isBlank() ? "none" : session.particleType();
        String idleList = String.join("&7, &e", plugin.animations().idleIds());
        if (idleList.isBlank()) idleList = "none";
        inv.setItem(SLOT_PARTICLE, of(Material.BLAZE_POWDER,
                "&6Effetto idle",
                "&8Attuale: &e" + idleId,
                "&7Disponibili: &e" + idleList,
                "",
                "&aClicca per cambiare"));

        String titleStr = session.titleText() == null || session.titleText().isBlank()
                ? "&8(config.yml)" : "&f" + strip(session.titleText());
        inv.setItem(SLOT_TITLE, of(Material.PAPER,
                "&bTitolo apertura",
                "&8Testo: " + titleStr,
                "&8Durata: &f" + session.titleStay() + "t",
                "",
                "&7Vuoto = valore di config.yml",
                "&7Scrivi none per disattivare",
                "&7Clicca → scrivi in chat"));

        String sub = session.titleSubtitle() == null || session.titleSubtitle().isBlank()
                ? "&8(config.yml)" : "&f" + strip(session.titleSubtitle());
        inv.setItem(SLOT_SUBTITLE, of(Material.PAPER,
                "&bSottotitolo apertura",
                "&8Testo: " + sub,
                "",
                "&7Vuoto = valore di config.yml",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_PREVIEW_TOGGLE, toggle(
                "&bAnteprima click sinistro",
                session.previewOnLeftClick()));

        int totalWeight = session.rewards().stream()
                .mapToInt(net.nyxon.crates.crate.RewardDefinition::weight).sum();
        inv.setItem(SLOT_REWARDS, ofGlow(Material.CHEST,
                "&aModifica premi &7(" + session.rewards().size() + ")",
                "&8Peso totale: &f" + totalWeight,
                "",
                "&7Clicca per gestire i premi"));

        inv.setItem(SLOT_BACK,   back());
        inv.setItem(SLOT_SAVE,   save());
        inv.setItem(SLOT_DELETE, session.isDeleteConfirmArmed() ? deleteConfirm() : delete());

        fillAll(inv);
        player.openInventory(inv);
    }

    private static String formatSpec(net.nyxon.crates.item.ItemSpec spec) {
        if (spec == null) return "none";
        return switch (spec.provider()) {
            case MMOITEMS   -> "&d[MMOItems] &f" + spec.id();
            case ITEMSADDER -> "&b[ItemsAdder] &f" + spec.id();
            case NEXO       -> "&a[Nexo] &f" + spec.id();
            case VANILLA    -> "&7" + spec.id();
        };
    }
}
