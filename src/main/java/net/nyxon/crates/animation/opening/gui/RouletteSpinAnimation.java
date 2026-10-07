// made by nyxon
package net.nyxon.crates.animation.opening.gui;

import net.nyxon.crates.NyxonCrates;
import net.nyxon.crates.animation.AnimationSession;
import net.nyxon.crates.animation.AnimationTemplate;
import net.nyxon.crates.animation.CrateAnimation;
import net.nyxon.crates.animation.opening.gui.support.GuiOpeningSupport;
import net.nyxon.crates.animation.opening.gui.support.GuiOpeningTheme;
import net.nyxon.crates.crate.CrateDefinition;
import net.nyxon.crates.crate.RewardDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RouletteSpinAnimation implements CrateAnimation {

    private static final int[] RING = {4, 5, 15, 23, 22, 21, 11, 3};
    private static final int POINTER = 13;

    private final NyxonCrates plugin;
    private final AnimationTemplate template;
    private final Random rng = new Random();

    public RouletteSpinAnimation(NyxonCrates plugin, AnimationTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    @Override
    public void play(Player player, Location location, CrateDefinition crate, RewardDefinition reward, Runnable reveal) {
        List<RewardDefinition> pool = GuiOpeningSupport.pool(crate);
        if (pool.isEmpty()) {
            reveal.run();
            return;
        }

        List<RewardDefinition> ring = new ArrayList<>(RING.length);
        for (int i = 0; i < RING.length; i++) {
            ring.add(GuiOpeningSupport.pickFiller(pool, reward, rng));
        }

        Inventory inventory = Bukkit.createInventory(null, 27, GuiOpeningTheme.openingTitle());
        GuiOpeningTheme.fillAll(inventory);
        inventory.setItem(POINTER, GuiOpeningTheme.pointer());
        paintRing(inventory, ring, false);
        AnimationSession session = GuiOpeningSupport.begin(plugin, player, inventory, reveal);
        int maxSpins = Math.max(20, template.duration() / 2);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            int spins = 0;
            int linger = -1;

            @Override
            public void run() {
                if (GuiOpeningSupport.aborted(player, session)) {
                    cancel();
                    return;
                }
                if (linger >= 0) {
                    if (++linger >= 6) {
                        GuiOpeningSupport.complete(plugin, player, session, reveal,
                                template.finalSound(), template.volume(), template.pitch());
                        cancel();
                    }
                    return;
                }

                ticks++;
                if (ticks % interval(spins) != 0) {
                    return;
                }

                spins++;
                if (spins < maxSpins) {
                    Collections.rotate(ring, 1);
                    ring.set(0, GuiOpeningSupport.pickFiller(pool, reward, rng));
                    player.playSound(player, template.sound(), 0.35f, 0.9f + spins * 0.02f);
                    paintRing(inventory, ring, false);
                    return;
                }

                ring.set(0, reward);
                inventory.setItem(POINTER, GuiOpeningTheme.winPointer());
                paintRing(inventory, ring, true);
                linger = 0;
            }
        };
        session.bind(task.runTaskTimer(plugin, 4L, 4L));
    }

    private static int interval(int spins) {
        if (spins < 10) return 1;
        if (spins < 16) return 2;
        if (spins < 20) return 3;
        return 4;
    }

    private static void paintRing(Inventory inventory, List<RewardDefinition> ring, boolean won) {
        for (int i = 0; i < RING.length; i++) {
            var icon = ring.get(i).preview();
            if (won && i == 0) {
                icon = GuiOpeningTheme.glow(icon);
            }
            GuiOpeningTheme.show(inventory, RING[i], icon);
        }
    }
}
