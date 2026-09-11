// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.item.ItemSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static it.haze.hazecrates.gui.GuiItems.*;

public final class RewardEditorGui {

    public static final String TAG = "Modifica premio:";

    static final int SLOT_ID         = 10;
    static final int SLOT_MATERIAL   = 11;
    static final int SLOT_AMOUNT     = 12;
    static final int SLOT_WEIGHT     = 13;
    static final int SLOT_GLOW       = 14;
    static final int SLOT_BROADCAST  = 15;
    static final int SLOT_PERMISSION = 16;

    static final int SLOT_NAME     = 28;
    static final int SLOT_LORE     = 29;
    static final int SLOT_LORE_TOGGLE = 30;
    static final int SLOT_COMMANDS = 31;

    static final int SLOT_BACK    = 45;
    static final int SLOT_REMOVE  = 47;
    static final int SLOT_PREVIEW = 49;
    static final int SLOT_APPLY   = 53;

    private static final int[] SEP_ROW = {18,19,20,21,22,23,24,25,26};

    private RewardEditorGui() {}

    public static void open(Player player, HazeCrates plugin, CrateEditorSession session) {
        boolean isNew = session.editingRewardIndex() == -1;
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.messages().parse(isNew ? "<gold>Nuovo premio</gold>" : "<gold>Modifica premio: </gold>" + session.rewardId()));

        fillBorder(inv);
        for (int s : SEP_ROW) inv.setItem(s, fillerBlack());

        inv.setItem(SLOT_ID, of(Material.NAME_TAG,
                "&bID premio",
                "&8Attuale: &f" + session.rewardId(),
                "",
                "&7Lettere minuscole, numeri, underscore",
                "&7Clicca → scrivi in chat"));

        ItemSpec spec = session.rewardItemSpec();
        String specLabel = spec != null ? spec.serialize() : "STONE";
        String providerColour = spec != null ? switch (spec.provider()) {
            case MMOITEMS   -> "&d[MMOItems] ";
            case ITEMSADDER -> "&b[ItemsAdder] ";
            case NEXO       -> "&a[Nexo] ";
            case VANILLA    -> "&7";
        } : "&7";
        Material previewMat = session.rewardMaterial();
        ItemStack matItem = new ItemStack(previewMat);
        ItemMeta matMeta = matItem.getItemMeta();
        matMeta.displayName(legacy("&b⬛ &fReward Item"));
        matMeta.lore(List.of(
                legacy("&8Attuale: " + providerColour + (spec != null ? spec.id() : "STONE")),
                legacy(""),
                legacy("&7Prendi un item e cliccalo qui"),
                legacy("&7per usare il materiale base."),
                legacy(""),
                legacy("&7Clicca a vuoto → scrivi in chat")));
        matItem.setItemMeta(matMeta);
        inv.setItem(SLOT_MATERIAL, matItem);

        inv.setItem(SLOT_AMOUNT, of(Material.PAPER,
                "&bQuantità",
                "&8Attuale: &f" + session.rewardAmount(),
                "",
                "&7Clicca → scrivi in chat"));

        int totalWeight = session.rewards().stream()
                .mapToInt(it.haze.hazecrates.crate.RewardDefinition::weight).sum();

        if (session.editingRewardIndex() < 0) totalWeight += session.rewardWeight();

        inv.setItem(SLOT_WEIGHT, of(Material.COMPARATOR,
                "&bPeso",
                "&8Attuale: &f" + session.rewardWeight(),
                "&8Probabilità:  " + probabilityColour(session.rewardWeight(), totalWeight),
                "&8Rarità:  " + rarityLabel(session.rewardWeight(), totalWeight),
                "",
                "&7Più alto = più comune",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_GLOW,      toggle("&bEffetto glow",  session.rewardGlow()));
        inv.setItem(SLOT_BROADCAST, toggle("&bBroadcast",   session.rewardBroadcast()));

        inv.setItem(SLOT_PERMISSION, of(Material.IRON_DOOR,
                "&bPermesso",
                "&8Attuale: &f" + (session.rewardPermission().isBlank()
                        ? "(nessuno – tutti)" : session.rewardPermission()),
                "",
                "&7Solo i giocatori con questo permesso",
                "&7possono vincere questo premio",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_NAME, of(Material.OAK_SIGN,
                "&bNome mostrato",
                "&8Attuale: &f" + (session.rewardName() == null || session.rewardName().isBlank()
                        ? "&7(nome dell'item)" : strip(session.rewardName())),
                "",
                "&7Vuoto = display name dell'item",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_LORE, of(Material.BOOK,
                "&bLinee lore",
                "&8Linee: &f" + session.rewardLore().size(),
                "&8Anteprima: &7" + (session.rewardLore().isEmpty()
                        ? "(vuota)" : strip(session.rewardLore().get(0))),
                "",
                "&7Separa le linee con &e|",
                "&7Supporta codici colore &",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_LORE_TOGGLE, toggle(
                "&bLore personalizzata",
                session.rewardLoreOverride()));

        inv.setItem(SLOT_COMMANDS, of(Material.COMMAND_BLOCK,
                "&bComandi",
                "&8Numero: &f" + session.rewardCommands().size(),
                (session.rewardCommands().isEmpty()
                        ? "&8Anteprima: &7(nessuno)"
                        : "&8Primo: &7" + session.rewardCommands().get(0)),
                "",
                "&7Eseguiti dalla console all'assegnazione",
                "&7Placeholder: &e%player%",
                "&7Separa con &e|",
                "&7Clicca → scrivi in chat"));

        inv.setItem(SLOT_BACK,   back());
        inv.setItem(SLOT_REMOVE, of(Material.BARRIER,
                "&cRimuovi premio",
                "&7Rimuove questo premio dalla lista",
                "&c&lAzione irreversibile!"));

        if (spec != null) {
            String previewName = blankToNull(session.rewardName());
            if (!session.rewardLoreOverride()
                    && spec.provider() != it.haze.hazecrates.item.ItemProvider.VANILLA) {
                previewName = null;
            }
            ItemStack preview = plugin.externalItems().resolve(
                    spec, session.rewardAmount(),
                    previewName,
                    session.rewardLoreOverride() ? session.rewardLore() : null,
                    session.rewardGlow());
            ItemMeta pm = preview.getItemMeta();
            List<Component> pLore = pm.lore() == null
                    ? new java.util.ArrayList<>() : new java.util.ArrayList<>(pm.lore());
            pLore.add(legacy(""));
            pLore.add(legacy("&8Anteprima – clicca Applica per salvare"));
            pm.lore(pLore);
            preview.setItemMeta(pm);
            inv.setItem(SLOT_PREVIEW, preview);
        } else {
            inv.setItem(SLOT_PREVIEW, of(Material.GRAY_DYE, "&8Anteprima non disponibile"));
        }

        inv.setItem(SLOT_APPLY, ofGlow(Material.EMERALD,
                "&aApplica",
                "&7Salva il premio nella lista",
                "&8(non scrive ancora il file)"));

        fillAll(inv);
        player.openInventory(inv);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
