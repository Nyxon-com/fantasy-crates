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

public final class ParticleSpiralAnimation implements CrateAnimation {

    private final HazeCrates plugin;
    private final AnimationTemplate template;

    public ParticleSpiralAnimation(HazeCrates plugin, AnimationTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    @Override
    public boolean isWorldAnimation() {
        return true;
    }

    @Override
    public void play(Player player, Location location, CrateDefinition crate, RewardDefinition reward, Runnable reveal) {
        if (!WorldOpeningSupport.chunkReady(location)) {
            reveal.run();
            return;
        }

        Location center = location.clone().add(0, 0.2, 0);
        AnimationSession session = WorldOpeningSupport.begin(plugin, player, reveal);
        int spin = Math.max(24, template.duration() / 2);

        new BukkitRunnable() {
            int ticks = 0;
            ItemDisplay display;
            ArmorStand name;

            @Override
            public void run() {
                if (GuiOpeningSupport.aborted(player, session)) {
                    cleanup();
                    cancel();
                    return;
                }

                ticks++;
                double angle = ticks * 0.35;
                double height = Math.min(1.4, ticks * 0.04);
                WorldOpeningSupport.helix(center.getWorld(), center, template.particle(), 0.7, height, angle);

                if (ticks == spin / 2 && player.isOnline()) {
                    player.playSound(player.getLocation(), template.sound(), template.volume(), 1.0f);
                }

                if (ticks < spin) {
                    return;
                }

                if (display == null) {
                    Location hover = center.clone().add(0, 1.3, 0);
                    display = WorldOpeningSupport.spawnItem(hover, reward.icon(), 0.75f, true);
                    name = WorldOpeningSupport.spawnName(hover.clone().add(0, 0.45, 0), reward);
                    hover.getWorld().spawnParticle(Particle.FIREWORK, hover, 20, 0.25, 0.25, 0.25, 0.08);
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), template.finalSound(), template.volume(), template.pitch());
                    }
                }

                if (ticks >= spin + 22) {
                    cleanup();
                    plugin.takeAnimSession(player.getUniqueId());
                    if (session.finish()) {
                        reveal.run();
                    }
                    cancel();
                }
            }

            private void cleanup() {
                WorldOpeningSupport.remove(display);
                WorldOpeningSupport.remove(name);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
