// made by haze
package it.haze.hazecrates.animation.opening.gui;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.AnimationTemplate;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.animation.opening.gui.support.GuiOpeningSupport;
import it.haze.hazecrates.animation.opening.world.support.OpeningProps;
import it.haze.hazecrates.animation.opening.world.support.WorldOpeningSupport;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import it.haze.hazecrates.item.DisplayIcon;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CsgoScrollAnimation implements CrateAnimation {

    private static final int WIDTH = 7;
    private static final int CENTER = 3;

    private final HazeCrates plugin;
    private final AnimationTemplate template;
    private final Random rng = new Random();

    public CsgoScrollAnimation(HazeCrates plugin, AnimationTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    @Override
    public boolean isWorldAnimation() {
        return true;
    }

    @Override
    public void play(Player player, Location location, CrateDefinition crate, RewardDefinition reward, Runnable reveal) {
        List<RewardDefinition> pool = GuiOpeningSupport.pool(crate);
        if (pool.isEmpty() || !WorldOpeningSupport.chunkReady(player.getLocation())) {
            reveal.run();
            return;
        }

        List<RewardDefinition> row = new ArrayList<>(WIDTH);
        for (int i = 0; i < WIDTH; i++) {
            row.add(GuiOpeningSupport.pickFiller(pool, reward, rng));
        }

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location base = eye.clone().add(forward.multiply(2.1)).add(0, -0.15, 0);

        ItemDisplay[] shown = new ItemDisplay[WIDTH];
        for (int i = 0; i < WIDTH; i++) {
            Location at = base.clone().add(right.clone().multiply((i - CENTER) * 0.48));
            ItemDisplay display = WorldOpeningSupport.spawnItem(at, row.get(i).icon(), i == CENTER ? 0.55f : 0.38f, false);
            display.setPersistent(false);
            OpeningProps.track(player.getUniqueId(), display);
            shown[i] = display;
        }

        AnimationSession session = WorldOpeningSupport.begin(plugin, player, reveal);
        int maxSpins = Math.max(12, template.duration() / 3);
        int plantSpin = maxSpins - CENTER;

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            int spins = 0;
            int linger = -1;

            @Override
            public void run() {
                if (!player.isOnline() || session.isFinished()) {
                    finish(false);
                    return;
                }
                if (linger >= 0) {
                    if (++linger >= 16) {
                        finish(true);
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
                paint();
                player.playSound(player, template.sound(), 0.35f, 1.4f - spins * 0.015f);
                if (spins >= maxSpins) {
                    shown[CENTER].setGlowing(true);
                    linger = 0;
                }
            }

            private void paint() {
                for (int i = 0; i < WIDTH; i++) {
                    if (shown[i] != null && shown[i].isValid()) {
                        shown[i].setItemStack(DisplayIcon.light(row.get(i).icon()));
                    }
                }
                player.sendActionBar(DisplayIcon.visibleName(row.get(CENTER).icon()));
            }

            private void finish(boolean celebrate) {
                OpeningProps.clear(player.getUniqueId());
                plugin.takeAnimSession(player.getUniqueId());
                cancel();
                if (session.finish()) {
                    reveal.run();
                }
                if (celebrate && player.isOnline()) {
                    player.sendActionBar(DisplayIcon.visibleName(reward.icon()));
                    player.playSound(player, template.finalSound(), template.volume(), template.pitch());
                }
            }
        };
        session.bind(task.runTaskTimer(plugin, 2L, 2L));
    }

    private static int interval(int spins) {
        if (spins < 8) return 1;
        if (spins < 12) return 2;
        return 3;
    }
}
