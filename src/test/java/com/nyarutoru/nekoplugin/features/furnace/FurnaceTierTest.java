package com.nyarutoru.nekoplugin.features.furnace;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceTierTest {

    @Test
    void testAllTiersExist() {
        assertEquals(10, FurnaceTier.values().length, "Should have exactly 10 tiers");
    }

    @Test
    void testTierLevelsAreSequential() {
        FurnaceTier[] tiers = FurnaceTier.values();
        for (int i = 0; i < tiers.length; i++) {
            assertEquals(i + 1, tiers[i].getLevel(),
                "Tier " + tiers[i].name() + " should have level " + (i + 1));
        }
    }

    @Test
    void testSpeedMultipliers() {
        assertEquals(2, FurnaceTier.TIER_1.getSpeedMultiplier());
        assertEquals(12, FurnaceTier.TIER_5.getSpeedMultiplier());
        assertEquals(18, FurnaceTier.TIER_6.getSpeedMultiplier());
        assertEquals(100, FurnaceTier.TIER_10.getSpeedMultiplier());
    }

    @Test
    void testSpeedIncreasesEveryTier() {
        FurnaceTier[] tiers = FurnaceTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            assertTrue(tiers[i + 1].getSpeedMultiplier() > tiers[i].getSpeedMultiplier(),
                tiers[i].name() + " -> " + tiers[i + 1].name() + " should speed up");
        }
    }

    @Test
    void testTier6BuffIsSteeperThanTier5() {
        int jumpT4T5 = FurnaceTier.TIER_5.getSpeedMultiplier() - FurnaceTier.TIER_4.getSpeedMultiplier();
        int jumpT5T6 = FurnaceTier.TIER_6.getSpeedMultiplier() - FurnaceTier.TIER_5.getSpeedMultiplier();
        assertTrue(jumpT5T6 > jumpT4T5, "Tier 6+ should be buffed more than earlier tiers");
    }

    @Test
    void testUpgradeMaterialsDistinct() {
        FurnaceTier[] tiers = FurnaceTier.values();
        for (FurnaceTier tier : tiers) {
            assertNotNull(tier.getUpgradeMaterial(), tier.name() + " should have an upgrade material");
        }
        assertEquals(Material.SMOOTH_STONE, FurnaceTier.TIER_2.getUpgradeMaterial());
        assertEquals(Material.IRON_INGOT, FurnaceTier.TIER_3.getUpgradeMaterial());
        assertEquals(Material.IRON_BLOCK, FurnaceTier.TIER_4.getUpgradeMaterial());
        assertEquals(Material.GOLD_BLOCK, FurnaceTier.TIER_5.getUpgradeMaterial());
        assertEquals(Material.DIAMOND_BLOCK, FurnaceTier.TIER_6.getUpgradeMaterial());
        assertEquals(Material.TURTLE_HELMET, FurnaceTier.TIER_7.getUpgradeMaterial());
        assertEquals(Material.NETHERITE_INGOT, FurnaceTier.TIER_8.getUpgradeMaterial());
        assertEquals(Material.NETHERITE_BLOCK, FurnaceTier.TIER_9.getUpgradeMaterial());
        assertEquals(Material.NETHER_STAR, FurnaceTier.TIER_10.getUpgradeMaterial());
    }

    @Test
    void testGetByLevel() {
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, FurnaceTier.getByLevel(i).getLevel());
        }
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.getByLevel(0));
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.getByLevel(99));
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.getByLevel(-1));
    }

    @Test
    void testGetByName() {
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.getByName("TIER_1"));
        assertEquals(FurnaceTier.TIER_10, FurnaceTier.getByName("TIER_10"));
        assertEquals(FurnaceTier.TIER_5, FurnaceTier.getByName("tier_5"));
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.getByName("INVALID"));
    }

    @Test
    void testGetNextTier() {
        assertEquals(FurnaceTier.TIER_2, FurnaceTier.TIER_1.getNextTier());
        assertEquals(FurnaceTier.TIER_10, FurnaceTier.TIER_9.getNextTier());
        assertNull(FurnaceTier.TIER_10.getNextTier());
    }

    @Test
    void testGetPreviousTier() {
        assertNull(FurnaceTier.TIER_1.getPreviousTier());
        assertEquals(FurnaceTier.TIER_1, FurnaceTier.TIER_2.getPreviousTier());
        assertEquals(FurnaceTier.TIER_9, FurnaceTier.TIER_10.getPreviousTier());
    }

    @Test
    void testDisplayColors() {
        assertEquals(NamedTextColor.GRAY, FurnaceTier.TIER_1.getColor());
        assertEquals(NamedTextColor.AQUA, FurnaceTier.TIER_4.getColor());
        assertEquals(NamedTextColor.YELLOW, FurnaceTier.TIER_10.getColor());
    }
}
