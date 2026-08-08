package com.nyarutoru.nekoplugin.features.villageroptimize;

import java.util.Locale;
import java.util.Set;

final class VillagerOptimizePolicy {

    static final long OPTIMIZE_COOLDOWN_MILLIS = 10 * 60 * 1000L;
    static final long LEVEL_CHECK_COOLDOWN_MILLIS = 5 * 1000L;
    static final Set<String> OPTIMIZE_NAMES = Set.of("optimize", "disableai");

    private VillagerOptimizePolicy() {
    }

    static boolean isOptimizeName(String name) {
        return OPTIMIZE_NAMES.contains(name.strip().toLowerCase(Locale.ROOT));
    }

    static boolean cooldownElapsed(long now, long lastAction, long cooldown) {
        return lastAction <= 0 || now - lastAction >= cooldown;
    }

    static int levelForExperience(int experience) {
        if (experience >= 250) {
            return 5;
        }
        if (experience >= 150) {
            return 4;
        }
        if (experience >= 70) {
            return 3;
        }
        if (experience >= 10) {
            return 2;
        }
        return 1;
    }

    static long latestRestockTime(long fullTime) {
        long dayStart = Math.floorDiv(fullTime, 24000L) * 24000L;
        long timeOfDay = Math.floorMod(fullTime, 24000L);
        if (timeOfDay >= 13000L) {
            return dayStart + 13000L;
        }
        if (timeOfDay >= 1000L) {
            return dayStart + 1000L;
        }
        return dayStart - 11000L;
    }
}
