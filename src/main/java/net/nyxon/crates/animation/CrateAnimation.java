// made by nyxon
package net.nyxon.crates.animation;

import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.crate.RewardDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface CrateAnimation {
    void play(Player player, Location location,
              CrateDefinition crate, RewardDefinition reward,
              Runnable reveal);

    default boolean isWorldAnimation() { return false; }
}
