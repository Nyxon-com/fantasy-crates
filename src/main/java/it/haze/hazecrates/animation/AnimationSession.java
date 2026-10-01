// made by haze
package it.haze.hazecrates.animation;

import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AnimationSession {

    private final Inventory inventory;
    private final Runnable reveal;
    private final AtomicBoolean granted = new AtomicBoolean(false);
    private BukkitTask task;

    public AnimationSession(Inventory inventory, Runnable reveal) {
        this.inventory = inventory;
        this.reveal    = reveal;
    }

    public Inventory inventory() { return inventory; }

    public boolean hasReveal() { return reveal != null; }

    public void bind(BukkitTask task) {
        cancelTask();
        this.task = task;
    }

    public void cancelTask() {
        BukkitTask current = this.task;
        this.task = null;
        if (current != null) {
            current.cancel();
        }
    }

    public boolean scheduleGrant(org.bukkit.plugin.Plugin plugin) {
        if (!granted.compareAndSet(false, true)) {
            return false;
        }
        if (reveal != null) {
            plugin.getServer().getScheduler().runTask(plugin, reveal);
        }
        return true;
    }

    public boolean finish() {
        return granted.compareAndSet(false, true);
    }

    public boolean isFinished() { return granted.get(); }

    public void grantIfPending() {
        if (granted.compareAndSet(false, true)) {
            if (reveal != null) reveal.run();
        }
    }
}
