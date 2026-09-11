// made by haze
package it.haze.hazecrates.animation;

import org.bukkit.Particle;
import org.bukkit.Sound;

public record AnimationTemplate(
        String id,
        Particle particle,
        Sound sound,
        Sound finalSound,
        float volume,
        float pitch,
        int duration,
        boolean cameraLock
) {

    public static AnimationTemplate defaults(String id) {
        return new AnimationTemplate(
                id,
                Particle.END_ROD,
                Sound.BLOCK_NOTE_BLOCK_HAT,
                Sound.ENTITY_PLAYER_LEVELUP,
                1.0f, 1.0f, 80, false);
    }
}
