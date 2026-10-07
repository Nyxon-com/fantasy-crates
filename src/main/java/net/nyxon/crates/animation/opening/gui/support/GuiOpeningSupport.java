// made by nyxon
package net.nyxon.crates.animation.opening.gui.support;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.animation.AnimationSession;
import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.crate.RewardDefinition;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GuiOpeningSupport {

    private GuiOpeningSupport() {}

    public static AnimationSession begin(NyxonCrates plugin, Player player, Inventory inventory, Runnable reveal) {
        AnimationSession session = new AnimationSession(inventory, reveal);
        plugin.startAnimSession(player.getUniqueId(), session);
        player.openInventory(inventory);
        return session;
    }

    public static void complete(
            NyxonCrates plugin,
            Player player,
            AnimationSession session,
            Runnable reveal,
            Sound finalSound,
            float volume,
            float pitch
    ) {
        session.cancelTask();
        plugin.takeAnimSession(player.getUniqueId());
        if (player.isOnline()) {
            player.closeInventory();
            player.playSound(player, finalSound, volume, pitch);
        }
        session.scheduleGrant(plugin);
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
