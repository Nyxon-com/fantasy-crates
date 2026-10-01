// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.item.DisplayIcon;
import it.haze.hazecrates.item.ItemProvider;
import it.haze.hazecrates.item.ItemSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Reward crate. Icon = item completo (anche MMOItems) risolto a load.
 * displayIcon = preview leggero stile ExcellentCrates getPreviewItem().
 */
public record RewardDefinition(
        String id,
        ItemSpec itemSpec,
        ItemStack icon,
        ItemStack displayIcon,
        List<String> commands,
        int weight,
        String permission,
        boolean broadcast,
        boolean loreOverride,
        int previewSlot
) {
    public RewardDefinition(String id, ItemSpec itemSpec, ItemStack icon, List<String> commands,
                            int weight, String permission, boolean broadcast, boolean loreOverride) {
        this(id, itemSpec, icon, DisplayIcon.light(icon), commands, weight, permission, broadcast, loreOverride, -1);
    }

    public RewardDefinition(String id, ItemSpec itemSpec, ItemStack icon, List<String> commands,
                            int weight, String permission, boolean broadcast, boolean loreOverride, int previewSlot) {
        this(id, itemSpec, icon, DisplayIcon.light(icon), commands, weight, permission, broadcast, loreOverride, previewSlot);
    }

    public Component displayComponent() {
        if (icon == null || icon.getType().isAir()) {
            return Component.text(id);
        }
        return DisplayIcon.visibleName(icon);
    }

    public String displayName() {
        return plainName();
    }

    public String plainName() {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(displayComponent())
                .trim();
    }

    public boolean liveProviderAppearance() {
        return !loreOverride
                && itemSpec != null
                && itemSpec.provider() != ItemProvider.VANILLA;
    }

    /** Preview per animazioni GUI/world: clone leggero, no NBT MMOItems. */
    public ItemStack preview() {
        if (displayIcon == null || displayIcon.getType().isAir()) {
            return DisplayIcon.light(icon);
        }
        return displayIcon.clone();
    }

    /** Premio reale: clone del template risolto a load (niente mi give). */
    public ItemStack prize() {
        if (icon == null || icon.getType().isAir()) {
            return null;
        }
        return icon.clone();
    }
}
