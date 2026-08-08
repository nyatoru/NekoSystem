package com.nyarutoru.nekoplugin.features.villageroptimize;

import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerOptimizeValidationTest {

    @Test
    void featureUsesExpectedIdentityAndLifecycle() {
        VillagerOptimizeFeature feature = new VillagerOptimizeFeature();

        assertEquals("villager_optimize", feature.getId());
        assertEquals("Villager Optimize", feature.getName());
        assertFalse(feature.isEnabled());
        assertTrue(Feature.class.isAssignableFrom(VillagerOptimizeFeature.class));
        assertTrue(AbstractFeature.class.isAssignableFrom(VillagerOptimizeFeature.class));
        assertTrue(Listener.class.isAssignableFrom(VillagerOptimizeListener.class));
    }

    @Test
    void recognizesOptimizeNameTagsCaseInsensitively() {
        assertTrue(VillagerOptimizePolicy.isOptimizeName("Optimize"));
        assertTrue(VillagerOptimizePolicy.isOptimizeName(" disableAI "));
        assertFalse(VillagerOptimizePolicy.isOptimizeName("Villager"));
    }

    @Test
    void enforcesCooldownBoundaries() {
        long now = 1_000_000L;

        assertTrue(VillagerOptimizePolicy.cooldownElapsed(now, 0, 10_000L));
        assertFalse(VillagerOptimizePolicy.cooldownElapsed(now, 995_000L, 10_000L));
        assertTrue(VillagerOptimizePolicy.cooldownElapsed(now, 990_000L, 10_000L));
    }

    @Test
    void mapsVillagerExperienceToProfessionLevels() {
        assertEquals(1, VillagerOptimizePolicy.levelForExperience(0));
        assertEquals(2, VillagerOptimizePolicy.levelForExperience(10));
        assertEquals(3, VillagerOptimizePolicy.levelForExperience(70));
        assertEquals(4, VillagerOptimizePolicy.levelForExperience(150));
        assertEquals(5, VillagerOptimizePolicy.levelForExperience(250));
    }

    @Test
    void selectsLatestScheduledRestock() {
        assertEquals(-11_000L, VillagerOptimizePolicy.latestRestockTime(500L));
        assertEquals(1_000L, VillagerOptimizePolicy.latestRestockTime(1_000L));
        assertEquals(13_000L, VillagerOptimizePolicy.latestRestockTime(13_500L));
        assertEquals(25_000L, VillagerOptimizePolicy.latestRestockTime(25_500L));
        assertEquals(37_000L, VillagerOptimizePolicy.latestRestockTime(38_000L));
    }
}
