// made by haze
package it.haze.hazecrates.crate;

import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.Particle;

import java.util.List;

public record CrateDisplayConfig(
        ItemSpec blockSpec,
        List<String> hologramLines,
        double hologramHeight,
        int hologramRefreshTicks,
        String idleEffectId,
        Particle particle,
        int particleCount,
        double particleRadius,
        int particleIntervalTicks,
        String titleLine,
        String subtitleLine,
        int titleFadeIn,
        int titleStay,
        int titleFadeOut,
        boolean glowingOutline
) {
    public static CrateDisplayConfig defaults() {
        return new CrateDisplayConfig(
                ItemSpec.vanilla("CHEST"),
                List.of("&b&lCRATE", "&7Right-click with a key"),
                1.5,
                40,
                "none",
                null,
                0,
                0,
                0,
                "",
                "",
                10, 40, 10,
                false
        );
    }
}
