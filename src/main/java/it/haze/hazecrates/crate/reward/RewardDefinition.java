// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.item.DisplayIcon;
import it.haze.hazecrates.item.ItemProvider;
import it.haze.hazecrates.item.ItemSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public record RewardDefinition(
        String id,
        ItemSpec itemSpec,
        ItemStack icon,
        List<String> commands,
        int weight,
        String permission,
        boolean broadcast,
        boolean loreOverride,
        int previewSlot
) {
    public RewardDefinition(String id, ItemSpec itemSpec, ItemStack icon, List<String> commands,
                            int weight, String permission, boolean broadcast, boolean loreOverride) {
        this(id, itemSpec, icon, commands, weight, permission, broadcast, loreOverride, -1);
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
}
