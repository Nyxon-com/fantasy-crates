// made by haze
package it.haze.hazecrates.animation;

import org.bukkit.inventory.Inventory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnimationSession {

    private final Inventory inventory;
    private final Runnable reveal;
    private final AtomicBoolean granted = new AtomicBoolean(false);

    public AnimationSession(Inventory inventory, Runnable reveal) {
        this.inventory = inventory;
        this.reveal    = reveal;
    }

    public Inventory inventory() { return inventory; }

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
