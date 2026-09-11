// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.gui.preview.CratePreviewConfig;
import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.Material;
import java.util.List;

public record CrateDefinition(
        String id,
        String displayName,
        Material material,
        boolean glow,
        KeyType keyType,
        ItemSpec keySpec,
        String animation,
        CrateDisplayConfig display,
        String broadcast,
        int broadcastThreshold,
        boolean previewOnLeftClick,
        CratePreviewConfig previewConfig,
        List<RewardDefinition> rewards,
        List<MilestoneDefinition> milestones
) {
    public CrateDefinition(
            String id,
            String displayName,
            Material material,
            boolean glow,
            KeyType keyType,
            ItemSpec keySpec,
            String animation,
            CrateDisplayConfig display,
            String broadcast,
            int broadcastThreshold,
            boolean previewOnLeftClick,
            List<RewardDefinition> rewards,
            List<MilestoneDefinition> milestones
    ) {
        this(id, displayName, material, glow, keyType, keySpec, animation, display,
                broadcast, broadcastThreshold, previewOnLeftClick, CratePreviewConfig.defaults(),
                rewards, milestones);
    }

    public List<String> hologram()  { return display().hologramLines(); }
    public double hologramHeight()  { return display().hologramHeight(); }
}
