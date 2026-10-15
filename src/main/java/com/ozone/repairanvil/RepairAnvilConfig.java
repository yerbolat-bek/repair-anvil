package com.ozone.repairanvil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RepairAnvilConfig {
    // Храним UUID игроков, которые ОТКЛЮЧИЛИ наш мод (выбрали ванильную наковальню).
    // По умолчанию (если игрока нет в сете) — мод включён.
    private static final Set<UUID> vanillaModePlayers = new HashSet<>();

    public static boolean isEnabledFor(UUID playerId) {
        return !vanillaModePlayers.contains(playerId);
    }

    public static boolean toggle(UUID playerId) {
        if (vanillaModePlayers.contains(playerId)) {
            vanillaModePlayers.remove(playerId);
            return true; // теперь включён (наш мод)
        } else {
            vanillaModePlayers.add(playerId);
            return false; // теперь выключен (ванилла)
        }
    }
}