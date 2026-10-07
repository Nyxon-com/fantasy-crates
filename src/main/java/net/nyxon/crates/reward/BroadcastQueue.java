// made by nyxon
package net.nyxon.crates.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Objects;

public final class BroadcastQueue {

    private final Plugin plugin;
    private final ArrayDeque<Component> pending =
            new ArrayDeque<>(64);

    private final int periodTicks;
    private final int perTick;

    private final Runnable drainAction;

    private BukkitTask task;

    public BroadcastQueue(
            Plugin plugin,
            int periodTicks,
            int perTick
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.periodTicks = Math.max(1, periodTicks);
        this.perTick = Math.max(1, perTick);
        this.drainAction = this::drain;
    }

    public synchronized void enqueue(Component message) {
        if (message == null) {
            return;
        }

        pending.addLast(message);

        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    drainAction,
                    1L,
                    periodTicks
            );
        }
    }

    private void drain() {
        for (int i = 0; i < perTick; i++) {
            Component message;

            synchronized (this) {
                message = pending.pollFirst();

                if (message == null) {
                    stopTaskLocked();
                    return;
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }

        synchronized (this) {
            if (pending.isEmpty()) {
                stopTaskLocked();
            }
        }
    }

    public synchronized void clear() {
        pending.clear();
        stopTaskLocked();
    }

    private void stopTaskLocked() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}