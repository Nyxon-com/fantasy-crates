// made by haze
package it.haze.hazecrates.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Coda annunci crate stile ExcellentCrates: evita spike MS quando
 * openall manda N broadcast allo stesso tick a tutti i player.
 */
public final class BroadcastQueue {

    private final Plugin plugin;
    private final Deque<Component> pending = new ArrayDeque<>();
    private BukkitTask task;
    private final int periodTicks;
    private final int perTick;

    public BroadcastQueue(Plugin plugin, int periodTicks, int perTick) {
        this.plugin = plugin;
        this.periodTicks = Math.max(1, periodTicks);
        this.perTick = Math.max(1, perTick);
    }

    public synchronized void enqueue(Component message) {
        if (message == null) return;
        pending.addLast(message);
        ensureRunning();
    }

    private void ensureRunning() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < perTick; i++) {
                Component next;
                synchronized (this) {
                    next = pending.pollFirst();
                    if (next == null) {
                        if (task != null) {
                            task.cancel();
                            task = null;
                        }
                        return;
                    }
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(next);
                }
            }
            synchronized (this) {
                if (pending.isEmpty() && task != null) {
                    task.cancel();
                    task = null;
                }
            }
        }, 1L, periodTicks);
    }

    public synchronized void clear() {
        pending.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
