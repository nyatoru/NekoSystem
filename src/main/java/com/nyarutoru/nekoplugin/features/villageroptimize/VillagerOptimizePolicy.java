package com.nyarutoru.nekoplugin.features.villageroptimize;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class VillagerOptimizePolicy {

    static long optimizeCooldownMillis = 10 * 60 * 1000L;
    static long levelCheckCooldownMillis = 5 * 1000L;
    static Set<String> optimizeNames = Set.of("optimize", "disableai");

    static void setOptimizeNames(String value) {
        Set<String> names = Arrays.stream(value.split(","))
            .map(name -> name.strip().toLowerCase(Locale.ROOT))
            .filter(name -> !name.isBlank())
            .collect(Collectors.toUnmodifiableSet());
        if (names.isEmpty()) throw new IllegalArgumentException("At least one optimize name is required");
        optimizeNames = names;
    }

    private VillagerOptimizePolicy() {
    }

    static boolean isOptimizeName(String name) {
        return optimizeNames.contains(name.strip().toLowerCase(Locale.ROOT));
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
