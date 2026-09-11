// made by haze
package it.haze.hazecrates.animation.opening.gui.support;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GuiOpeningSupport {

    private GuiOpeningSupport() {}

    public static AnimationSession begin(HazeCrates plugin, Player player, Inventory inventory, Runnable reveal) {
        AnimationSession session = new AnimationSession(inventory, reveal);
        plugin.startAnimSession(player.getUniqueId(), session);
        player.openInventory(inventory);
        return session;
    }

    public static void complete(
            HazeCrates plugin,
            Player player,
            AnimationSession session,
            Runnable reveal,
            Sound finalSound,
            float volume,
            float pitch
    ) {
        plugin.takeAnimSession(player.getUniqueId());
        if (session.finish()) {
            reveal.run();
        }
        if (player.isOnline()) {
            player.closeInventory();
            player.playSound(player.getLocation(), finalSound, volume, pitch);
        }
    }

    public static List<RewardDefinition> pool(CrateDefinition crate) {
        return new ArrayList<>(crate.rewards());
    }

    public static RewardDefinition pickFiller(List<RewardDefinition> pool, RewardDefinition winner, Random rng) {
        if (pool.isEmpty()) {
            return winner;
        }
        if (pool.size() == 1) {
            return pool.get(0);
        }
        RewardDefinition picked;
        do {
            picked = pool.get(rng.nextInt(pool.size()));
        } while (picked == winner && pool.size() > 1);
        return picked;
    }

    public static boolean aborted(Player player, AnimationSession session) {
        return !player.isOnline() || session.isFinished();
    }
}
