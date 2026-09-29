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
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RouletteSpinAnimation implements CrateAnimation {

    private static final int COUNT = 8;

    private final HazeCrates plugin;
    private final AnimationTemplate template;
    private final Random rng = new Random();

    public RouletteSpinAnimation(HazeCrates plugin, AnimationTemplate template) {
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

        List<RewardDefinition> ring = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            ring.add(GuiOpeningSupport.pickFiller(pool, reward, rng));
        }

        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location center = eye.clone().add(forward.clone().multiply(2.0)).add(0, -0.2, 0);

        ItemDisplay[] shown = new ItemDisplay[COUNT];
        for (int i = 0; i < COUNT; i++) {
            double angle = (Math.PI * 2 / COUNT) * i;
            Location at = center.clone()
                    .add(right.clone().multiply(Math.cos(angle) * 1.15))
                    .add(0, Math.sin(angle) * 0.55, 0);
            ItemDisplay display = WorldOpeningSupport.spawnItem(at, ring.get(i).icon(), 0.35f, false);
            display.setPersistent(false);
            OpeningProps.track(player.getUniqueId(), display);
            shown[i] = display;
        }

        AnimationSession session = WorldOpeningSupport.begin(plugin, player, reveal);
        int maxSpins = Math.max(12, template.duration() / 3);

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
                if (spins < maxSpins) {
                    Collections.rotate(ring, 1);
                    ring.set(0, GuiOpeningSupport.pickFiller(pool, reward, rng));
                } else {
                    ring.set(0, reward);
                    if (shown[0] != null) {
                        shown[0].setGlowing(true);
                    }
                    linger = 0;
                }
                paint();
                player.playSound(player, template.sound(), 0.35f, 0.9f + spins * 0.02f);
            }

            private void paint() {
                for (int i = 0; i < COUNT; i++) {
                    if (shown[i] != null && shown[i].isValid()) {
                        shown[i].setItemStack(DisplayIcon.light(ring.get(i).icon()));
                    }
                }
                player.sendActionBar(DisplayIcon.visibleName(ring.get(0).icon()));
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
