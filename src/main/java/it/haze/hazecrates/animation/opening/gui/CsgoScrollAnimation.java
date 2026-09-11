// made by haze
package it.haze.hazecrates.animation.opening.gui;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.AnimationTemplate;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.animation.opening.gui.support.GuiOpeningSupport;
import it.haze.hazecrates.animation.opening.gui.support.GuiOpeningTheme;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CsgoScrollAnimation implements CrateAnimation {

    private static final int[] STRIP = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private static final int CENTER = 4;

    private final HazeCrates plugin;
    private final AnimationTemplate template;
    private final Random rng = new Random();

    public CsgoScrollAnimation(HazeCrates plugin, AnimationTemplate template) {
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

        List<RewardDefinition> row = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            row.add(GuiOpeningSupport.pickFiller(pool, reward, rng));
        }

        Inventory inventory = Bukkit.createInventory(null, 27, GuiOpeningTheme.openingTitle());
        paintFrame(inventory, false);
        paintRow(inventory, row, false);
        AnimationSession session = GuiOpeningSupport.begin(plugin, player, inventory, reveal);

        int maxSpins = Math.max(18, template.duration() / 2);

        int plantSpin = maxSpins - CENTER;

        new BukkitRunnable() {
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
                    if (++linger >= 18) {
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
                row.remove(row.size() - 1);
                row.add(0, spins == plantSpin ? reward : GuiOpeningSupport.pickFiller(pool, reward, rng));

                boolean stopped = spins >= maxSpins;
                player.playSound(player.getLocation(), template.sound(), 0.35f, 1.4f - spins * 0.02f);
                paintFrame(inventory, stopped);
                paintRow(inventory, row, stopped);
                if (stopped) {
                    linger = 0;
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private static int interval(int spins) {
        if (spins < 10) return 1;
        if (spins < 16) return 2;
        if (spins < 20) return 3;
        return 5;
    }

    private static void paintFrame(Inventory inventory, boolean won) {

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, GuiOpeningTheme.fill());
            inventory.setItem(18 + i, GuiOpeningTheme.fill());
        }
        inventory.setItem(4, won ? GuiOpeningTheme.winPointer() : GuiOpeningTheme.pointer());
        inventory.setItem(22, won ? GuiOpeningTheme.winPointer() : GuiOpeningTheme.pointer());
    }

    private static void paintRow(Inventory inventory, List<RewardDefinition> row, boolean won) {
        for (int i = 0; i < STRIP.length; i++) {
            var icon = row.get(i).icon().clone();
            if (won && i == CENTER) {
                icon = GuiOpeningTheme.glow(icon);
            }
            inventory.setItem(STRIP[i], icon);
        }
    }
}
