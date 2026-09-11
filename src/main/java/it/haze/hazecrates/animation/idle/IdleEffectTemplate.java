// made by haze
package it.haze.hazecrates.animation.idle;

import org.bukkit.Particle;

public record IdleEffectTemplate(
        String id,
        IdleStyle style,
        Particle particle,
        int count,
        double radius,
        double height,
        int intervalTicks
) {
    public enum IdleStyle {
        RING, HELIX, SPIRAL, ORBIT, PULSE, RAIN, HEART, NONE;

        public static IdleStyle parse(String raw) {
            if (raw == null || raw.isBlank()) return RING;
            try {
                return IdleStyle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                return RING;
            }
        }
    }

    public static IdleEffectTemplate none() {
        return new IdleEffectTemplate("none", IdleStyle.NONE, Particle.FLAME, 0, 0, 0, 0);
    }

    public static IdleEffectTemplate simpleRing(Particle particle, int count, double radius, int interval) {
        return new IdleEffectTemplate("custom", IdleStyle.RING, particle, count, radius, 0.15, interval);
    }
}
