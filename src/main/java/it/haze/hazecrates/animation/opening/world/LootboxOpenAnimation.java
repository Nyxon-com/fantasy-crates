// made by haze
package it.haze.hazecrates.animation.opening.world;

import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.animation.AnimationSession;
import it.haze.hazecrates.animation.AnimationTemplate;
import it.haze.hazecrates.animation.CrateAnimation;
import it.haze.hazecrates.animation.opening.gui.support.GuiOpeningSupport;
import it.haze.hazecrates.animation.opening.world.support.TempOpenChest;
import it.haze.hazecrates.animation.opening.world.support.WorldOpeningSupport;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class LootboxOpenAnimation implements CrateAnimation {

    private static final int MAX_ORBIT = 8;
    private static final int OPEN_TICK = 12;
    private static final int SPAWN_EVERY = 5;
    private static final int POP_TICKS = 10;
    private static final int ORBIT_AFTER = 36;
    private static final int LIFT_TICKS = 24;
    private static final int LINGER = 30;

    private final HazeCrates plugin;
    private final AnimationTemplate template;

    public LootboxOpenAnimation(HazeCrates plugin, AnimationTemplate template) {
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

        List<RewardDefinition> shown = pickShown(crate, reward);
        TempOpenChest chest = TempOpenChest.place(location, player, chestMaterial(crate));
        Location origin = chest != null
                ? chest.origin()
                : location.clone().add(0, 0.2, 0);
        ItemDisplay fake = null;
        if (chest == null) {
            fake = WorldOpeningSupport.spawnItem(origin, new ItemStack(chestMaterial(crate)), 1.05f, false);
            fake.setBillboard(Display.Billboard.FIXED);
        }
        ItemDisplay fakeChest = fake;
        AnimationSession session = WorldOpeningSupport.begin(plugin, player, reveal);

        new BukkitRunnable() {
            int ticks = 0;
            int spawned = 0;
            int winnerIndex = Math.max(0, shown.indexOf(reward));
            final ItemDisplay[] orbs = new ItemDisplay[shown.size()];
            ItemDisplay prize;
            ArmorStand name;
            int liftStart = -1;
            boolean opened = false;

            @Override
            public void run() {
                if (GuiOpeningSupport.aborted(player, session)) {
                    cleanup();
                    cancel();
                    return;
                }
                ticks++;

                if (ticks < OPEN_TICK) {
                    if (ticks % 3 == 0) {
                        origin.getWorld().spawnParticle(Particle.SMOKE, origin.clone().add(0, 0.4, 0), 2, 0.15, 0.05, 0.15, 0.01);
                    }
                    return;
                }

                if (!opened) {
                    opened = true;
                    if (chest != null) {
                        chest.openLid();
                    }
                    origin.getWorld().spawnParticle(Particle.CLOUD, origin.clone().add(0, 0.6, 0), 22, 0.28, 0.18, 0.28, 0.04);
                    origin.getWorld().spawnParticle(Particle.FIREWORK, origin.clone().add(0, 0.6, 0), 16, 0.22, 0.22, 0.22, 0.05);
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, template.volume(), 0.9f);
                    }
                }

                if (spawned < shown.size()) {
                    int due = (ticks - OPEN_TICK) / SPAWN_EVERY;
                    while (spawned < shown.size() && spawned <= due) {
                        ItemDisplay orb = WorldOpeningSupport.spawnItem(
                                origin.clone(), shown.get(spawned).icon(), 0.42f, false);
                        orb.setBillboard(Display.Billboard.CENTER);
                        orbs[spawned] = orb;
                        origin.getWorld().spawnParticle(
                                Particle.CRIT, origin.clone().add(0, 0.5, 0), 8, 0.1, 0.15, 0.1, 0.02);
                        if (player.isOnline()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.15f + spawned * 0.08f);
                        }
                        spawned++;
                    }
                }

                spinOrbs();

                int spawnDone = OPEN_TICK + shown.size() * SPAWN_EVERY;
                if (ticks < spawnDone + ORBIT_AFTER) {
                    if (ticks % 4 == 0) {
                        WorldOpeningSupport.ring(
                                origin.getWorld(),
                                origin.clone().add(0, 0.35, 0),
                                template.particle() != null ? template.particle() : Particle.FIREWORK,
                                1.2,
                                10);
                    }
                    return;
                }

                if (liftStart < 0) {
                    liftStart = ticks;
                    if (orbs[winnerIndex] != null) {
                        prize = orbs[winnerIndex];
                        prize.setGlowing(true);
                        name = WorldOpeningSupport.spawnName(origin.clone().add(0, 1.7, 0), reward);
                    }
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), template.finalSound(), template.volume(), template.pitch());
                    }
                    origin.getWorld().spawnParticle(
                            Particle.TOTEM_OF_UNDYING, origin.clone().add(0, 1.4, 0), 28, 0.22, 0.3, 0.22, 0.06);
                }

                int lifted = ticks - liftStart;
                if (prize != null && lifted <= LIFT_TICKS) {
                    float t = lifted / (float) LIFT_TICKS;
                    float eased = 1f - (1f - t) * (1f - t);
                    float y = 0.55f + eased * 1.25f;
                    float scale = 0.5f + eased * 0.45f;
                    prize.setTransformation(WorldOpeningSupport.transform(0, y, 0, lifted * 16f, scale));
                    if (name != null) {
                        name.teleport(origin.clone().add(0, y + 0.55, 0));
                    }
                }

                float collapse = Math.max(0f, 1f - lifted / (float) LIFT_TICKS);
                for (int i = 0; i < spawned; i++) {
                    if (i == winnerIndex || orbs[i] == null) continue;
                    float shrink = Math.max(0.04f, 0.42f * collapse);
                    orbs[i].setTransformation(WorldOpeningSupport.transform(
                            orbX(i, ticks) * collapse,
                            0.45f * collapse + 0.2f,
                            orbZ(i, ticks) * collapse,
                            ticks * 20f,
                            shrink));
                    if (lifted == LIFT_TICKS) {
                        WorldOpeningSupport.remove(orbs[i]);
                        orbs[i] = null;
                    }
                }

                if (lifted >= LIFT_TICKS + LINGER) {
                    cleanup();
                    plugin.takeAnimSession(player.getUniqueId());
                    if (session.finish()) {
                        reveal.run();
                    }
                    cancel();
                }
            }

            private void spinOrbs() {
                for (int i = 0; i < spawned; i++) {
                    if (orbs[i] == null || orbs[i] == prize) continue;
                    float pop = popProgress(i, ticks);
                    float radius = 1.2f * pop;
                    float y = popY(i, ticks, pop);
                    orbs[i].setTransformation(WorldOpeningSupport.transform(
                            (float) (Math.cos(orbAngle(i, ticks)) * radius),
                            y,
                            (float) (Math.sin(orbAngle(i, ticks)) * radius),
                            ticks * 16f,
                            0.38f + pop * 0.08f));
                }
            }

            private float popProgress(int i, int t) {
                int born = OPEN_TICK + i * SPAWN_EVERY;
                float raw = Math.min(1f, Math.max(0f, (t - born) / (float) POP_TICKS));
                return 1f - (1f - raw) * (1f - raw);
            }

            private float popY(int i, int t, float pop) {
                if (pop < 1f) {
                    return 0.25f + (float) Math.sin(pop * Math.PI) * 0.75f;
                }
                return 0.45f + (float) Math.sin(t * 0.22 + i) * 0.1f;
            }

            private double orbAngle(int i, int t) {
                return t * 0.16 + i * (Math.PI * 2 / Math.max(1, shown.size()));
            }

            private float orbX(int i, int t) {
                return (float) (Math.cos(orbAngle(i, t)) * 1.2);
            }

            private float orbZ(int i, int t) {
                return (float) (Math.sin(orbAngle(i, t)) * 1.2);
            }

            private void cleanup() {
                if (chest != null) {
                    chest.restore();
                }
                WorldOpeningSupport.remove(fakeChest);
                WorldOpeningSupport.remove(prize);
                WorldOpeningSupport.remove(name);
                for (ItemDisplay orb : orbs) {
                    WorldOpeningSupport.remove(orb);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Material chestMaterial(CrateDefinition crate) {
        String id = crate.display().blockSpec().id();
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isBlock()) {
            return Material.CHEST;
        }
        return material;
    }

    private static List<RewardDefinition> pickShown(CrateDefinition crate, RewardDefinition winner) {
        List<RewardDefinition> shown = new ArrayList<>();
        for (RewardDefinition r : crate.rewards()) {
            if (shown.size() >= MAX_ORBIT) break;
            shown.add(r);
        }
        if (shown.isEmpty()) {
            shown.add(winner);
        } else if (!shown.contains(winner)) {
            shown.set(shown.size() - 1, winner);
        }
        return shown;
    }
}
