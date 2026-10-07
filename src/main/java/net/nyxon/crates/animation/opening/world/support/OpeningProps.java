package net.nyxon.crates.animation.opening.world.support;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpeningProps {

    private static final Map<UUID, List<Entity>> LIVE = new ConcurrentHashMap<>();

    private OpeningProps() {}

    public static void track(UUID playerId, Entity entity) {
        if (playerId == null || entity == null) {
            return;
        }
        LIVE.computeIfAbsent(playerId, id -> new ArrayList<>()).add(entity);
    }

    public static void clear(UUID playerId) {
        List<Entity> entities = LIVE.remove(playerId);
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    public static void clearAll() {
        for (UUID playerId : new ArrayList<>(LIVE.keySet())) {
            clear(playerId);
        }
    }
}
