// made by haze
package it.haze.hazecrates.animation;

import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface CrateAnimation {
    void play(Player player, Location location,
              CrateDefinition crate, RewardDefinition reward,
              Runnable reveal);

    default boolean isWorldAnimation() { return false; }
}
