package com.nyarutoru.nekoplugin.utils;

import org.bukkit.entity.Player;

/**
 * Reads and writes a player's current experience points (level + progress),
 * not their lifetime total.
 */
public final class PlayerExpUtils {

    private PlayerExpUtils() {
    }

    public static int getCurrentExp(Player player) {
        return player.calculateTotalExperiencePoints();
    }

    public static void setCurrentExp(Player player, int amount) {
        player.setExperienceLevelAndProgress(Math.max(0, amount));
    }
}
