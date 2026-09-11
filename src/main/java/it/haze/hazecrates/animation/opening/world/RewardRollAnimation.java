// made by haze
package it.haze.hazecrates.animation.opening.world;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.AnimationTemplate;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.animation.opening.gui.support.GuiOpeningSupport;
import it.haze.hazecrates.animation.opening.world.support.WorldOpeningSupport;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public final class RewardRollAnimation implements CrateAnimation {

    private final HazeCrates plugin;
    private final AnimationTemplate template;
    private final Random rng = new Random();

    public RewardRollAnimation(HazeCrates plugin, AnimationTemplate template) {
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
        if (pool.isEmpty() || !WorldOpeningSupport.chunkReady(location)) {
            reveal.run();
            return;
        }

        Location hover = location.clone().add(0, 1.35, 0);
        ItemDisplay display = WorldOpeningSupport.spawnItem(hover, pool.get(0).icon(), 0.7f, false);
        ArmorStand name = WorldOpeningSupport.spawnName(hover.clone().add(0, 0.45, 0), pool.get(0));
        AnimationSession session = WorldOpeningSupport.begin(plugin, player, reveal);
        int maxSpins = Math.max(16, template.duration() / 3);

        new BukkitRunnable() {
            int ticks = 0;
            int spins = 0;
            int linger = -1;

            @Override
            public void run() {
                if (GuiOpeningSupport.aborted(player, session)) {
                    cleanup();
                    cancel();
                    return;
                }

                ticks++;
                WorldOpeningSupport.ring(hover.getWorld(), hover, template.particle(), 0.55, 8);

                if (linger >= 0) {
                    if (++linger >= 20) {
                        cleanup();
                        plugin.takeAnimSession(player.getUniqueId());
                        if (session.finish()) {
                            reveal.run();
                        }
                        if (player.isOnline()) {
                            player.playSound(player.getLocation(), template.finalSound(), template.volume(), template.pitch());
                        }
                        cancel();
                    }
                    return;
                }

                if (ticks % interval(spins) != 0) {
                    return;
                }

                spins++;
                if (spins < maxSpins) {
                    RewardDefinition shown = GuiOpeningSupport.pickFiller(pool, reward, rng);
                    display.setItemStack(shown.icon());
                    name.customName(shown.displayComponent());
                    player.playSound(player.getLocation(), template.sound(), 0.4f, 1.3f - spins * 0.02f);
                    return;
                }

                display.setItemStack(reward.icon());
                display.setGlowing(true);
                name.customName(reward.displayComponent());
                hover.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, hover, 18, 0.2, 0.2, 0.2, 0.05);
                linger = 0;
            }

            private void cleanup() {
                WorldOpeningSupport.remove(display);
                WorldOpeningSupport.remove(name);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static int interval(int spins) {
        if (spins < 8) return 2;
        if (spins < 12) return 3;
        if (spins < 16) return 5;
        return 7;
    }
}
