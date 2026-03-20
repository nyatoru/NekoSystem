package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrawerTier enum.
 */
class DrawerTierTest {

    @Test
    void testAllTiersExist() {
        DrawerTier[] tiers = DrawerTier.values();
        assertEquals(10, tiers.length, "Should have exactly 10 tiers");
    }

    @Test
    void testTierLevelsAreSequential() {
        DrawerTier[] tiers = DrawerTier.values();
        for (int i = 0; i < tiers.length; i++) {
            assertEquals(i + 1, tiers[i].getLevel(), 
                "Tier " + tiers[i].name() + " should have level " + (i + 1));
        }
    }

    @Test
    void testTier1Capacity() {
        assertEquals(256, DrawerTier.TIER_1.getStackCapacity());
        assertEquals(256 * 64, DrawerTier.TIER_1.getMaxItems());
        assertEquals(Material.CHEST, DrawerTier.TIER_1.getUpgradeMaterial());
    }

    @Test
    void testTier10UnlimitedCapacity() {
        assertEquals(-1, DrawerTier.TIER_10.getStackCapacity());
        assertEquals(Integer.MAX_VALUE, DrawerTier.TIER_10.getMaxItems());
        assertEquals(Material.NETHER_STAR, DrawerTier.TIER_10.getUpgradeMaterial());
    }

    @Test
    void testCapacityDoublesEachTier() {
        DrawerTier[] tiers = DrawerTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            int currentCapacity = tiers[i].getStackCapacity();
            int nextCapacity = tiers[i + 1].getStackCapacity();
            assertEquals(currentCapacity * 2, nextCapacity,
                "Tier " + (i + 2) + " should have double the capacity of Tier " + (i + 1));
        }
    }

    @Test
    void testGetByLevel() {
        for (int i = 1; i <= 10; i++) {
            DrawerTier tier = DrawerTier.getByLevel(i);
            assertEquals(i, tier.getLevel());
        }
        
        // Invalid level should return TIER_1
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(0));
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(99));
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(-1));
    }

    @Test
    void testGetByName() {
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByName("TIER_1"));
        assertEquals(DrawerTier.TIER_5, DrawerTier.getByName("TIER_5"));
        assertEquals(DrawerTier.TIER_10, DrawerTier.getByName("TIER_10"));
        
        // Case insensitive
        assertEquals(DrawerTier.TIER_3, DrawerTier.getByName("tier_3"));
        assertEquals(DrawerTier.TIER_7, DrawerTier.getByName("Tier_7"));
        
        // Invalid name should return TIER_1
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByName("INVALID"));
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByName(null));
    }

    @Test
    void testGetDisplayNameComponent() {
        Component component = DrawerTier.TIER_1.getDisplayNameComponent();
        assertNotNull(component);
        
        // Verify color is applied
        assertEquals(NamedTextColor.GRAY, DrawerTier.TIER_1.getColor());
        assertEquals(NamedTextColor.AQUA, DrawerTier.TIER_6.getColor());
        assertEquals(NamedTextColor.RED, DrawerTier.TIER_10.getColor());
    }

    @Test
    void testGetNextTier() {
        assertEquals(DrawerTier.TIER_2, DrawerTier.TIER_1.getNextTier());
        assertEquals(DrawerTier.TIER_3, DrawerTier.TIER_2.getNextTier());
        assertEquals(DrawerTier.TIER_10, DrawerTier.TIER_9.getNextTier());
        
        // Last tier should return null
        assertNull(DrawerTier.TIER_10.getNextTier());
    }

    @Test
    void testTierMaterialsAreValid() {
        DrawerTier[] tiers = DrawerTier.values();
        for (DrawerTier tier : tiers) {
            Material material = tier.getUpgradeMaterial();
            assertNotNull(material, "Tier " + tier.name() + " should have a valid material");
            assertTrue(material.isItem(), "Tier " + tier.name() + " material should be an item");
        }
    }

    @Test
    void testTierDisplayNames() {
        assertEquals("Tier 1", DrawerTier.TIER_1.getDisplayName());
        assertEquals("Tier 2", DrawerTier.TIER_2.getDisplayName());
        assertEquals("Tier 10", DrawerTier.TIER_10.getDisplayName());
        assertEquals("Unlimited", DrawerTier.TIER_10.getDisplayName());
    }
}
