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
        List<MilestoneDefinition> milestones,
        int totalWeight
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
            CratePreviewConfig previewConfig,
            List<RewardDefinition> rewards,
            List<MilestoneDefinition> milestones
    ) {
        this(id, displayName, material, glow, keyType, keySpec, animation, display,
                broadcast, broadcastThreshold, previewOnLeftClick, previewConfig,
                List.copyOf(rewards), List.copyOf(milestones), sumWeights(rewards));
    }

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

    private static int sumWeights(List<RewardDefinition> rewards) {
        int total = 0;
        for (RewardDefinition r : rewards) {
            if (r.weight() > 0) total += r.weight();
        }
        return total;
    }

    public List<String> hologram()  { return display().hologramLines(); }
    public double hologramHeight()  { return display().hologramHeight(); }
}
