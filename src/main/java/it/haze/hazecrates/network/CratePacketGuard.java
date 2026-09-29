package it.haze.hazecrates.network;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface CratePacketGuard {

    CratePacketGuard NOOP = new CratePacketGuard() {};

    default void arm(Player player) {}

    default void scheduleFlush(Player player) {}

    default void disarm(UUID playerId) {}

    default void shutdown() {}
}
