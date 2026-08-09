package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Drawer tier system and validation logic.
 * These tests avoid direct Material enum usage to prevent Bukkit initialization requirements.
 */
class DrawerTest {

    @Test
    void testTierUpgradeLogic() {
        // Test that higher tiers have more capacity
        assertTrue(DrawerTier.TIER_2.getMaxItems() > DrawerTier.TIER_1.getMaxItems());
        assertTrue(DrawerTier.TIER_5.getMaxItems() > DrawerTier.TIER_4.getMaxItems());
        assertTrue(DrawerTier.TIER_10.getMaxItems() > DrawerTier.TIER_9.getMaxItems());
    }

    @Test
    void testTierLevelSequence() {
        DrawerTier[] tiers = DrawerTier.values();
        for (int i = 0; i < tiers.length; i++) {
            assertEquals(i + 1, tiers[i].getLevel());
        }
    }

    @Test
    void testTierGetByLevelValid() {
        for (int i = 1; i <= 10; i++) {
            DrawerTier tier = DrawerTier.getByLevel(i);
            assertEquals(i, tier.getLevel());
        }
    }

    @Test
    void testTierGetByLevelInvalid() {
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(0));
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(99));
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByLevel(-1));
    }

    @Test
    void testTierGetByNameValid() {
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByName("TIER_1"));
        assertEquals(DrawerTier.TIER_5, DrawerTier.getByName("TIER_5"));
        assertEquals(DrawerTier.TIER_10, DrawerTier.getByName("TIER_10"));
    }

    @Test
    void testTierGetByNameCaseInsensitive() {
        assertEquals(DrawerTier.TIER_3, DrawerTier.getByName("tier_3"));
        assertEquals(DrawerTier.TIER_7, DrawerTier.getByName("Tier_7"));
    }

    @Test
    void testTierGetByNameInvalid() {
        assertEquals(DrawerTier.TIER_1, DrawerTier.getByName("INVALID"));
    }

    @Test
    void testTierNextTier() {
        assertEquals(DrawerTier.TIER_2, DrawerTier.TIER_1.getNextTier());
        assertEquals(DrawerTier.TIER_3, DrawerTier.TIER_2.getNextTier());
        assertEquals(DrawerTier.TIER_10, DrawerTier.TIER_9.getNextTier());
        assertNull(DrawerTier.TIER_10.getNextTier());
    }

    @Test
    void testCapacityProgression() {
        // Verify the capacity progression is correct
        assertEquals(256, DrawerTier.TIER_1.getStackCapacity());
        assertEquals(512, DrawerTier.TIER_2.getStackCapacity());
        assertEquals(1024, DrawerTier.TIER_3.getStackCapacity());
        assertEquals(2048, DrawerTier.TIER_4.getStackCapacity());
        assertEquals(4096, DrawerTier.TIER_5.getStackCapacity());
        assertEquals(8192, DrawerTier.TIER_6.getStackCapacity());
        assertEquals(16384, DrawerTier.TIER_7.getStackCapacity());
        assertEquals(32768, DrawerTier.TIER_8.getStackCapacity());
        assertEquals(65536, DrawerTier.TIER_9.getStackCapacity());
        assertEquals(-1, DrawerTier.TIER_10.getStackCapacity());
    }

    @Test
    void testDisplayNames() {
        assertEquals("Tier 1", DrawerTier.TIER_1.getDisplayName());
        assertEquals("Tier 5", DrawerTier.TIER_5.getDisplayName());
        assertEquals("Tier 9", DrawerTier.TIER_9.getDisplayName());
        assertEquals("Unlimited", DrawerTier.TIER_10.getDisplayName());
    }

    @Test
    void testTierColors() {
        // Verify all tiers have colors assigned
        DrawerTier[] tiers = DrawerTier.values();
        for (DrawerTier tier : tiers) {
            assertNotNull(tier.getColor(), "Tier " + tier.name() + " should have a color");
        }
    }

    @Test
    void testSerializationStructure() {
        // Test that serialization produces expected structure
        assertNotNull(DrawerTier.TIER_1.name());
        assertNotNull(DrawerTier.TIER_1.getDisplayName());
    }

    @Test
    void testGetMaxCapacity() {
        // Capacity is determined by tier
        assertEquals(256 * 64, DrawerTier.TIER_1.getMaxItems());
        assertEquals(4096 * 64, DrawerTier.TIER_5.getMaxItems());
        assertEquals(Integer.MAX_VALUE, DrawerTier.TIER_10.getMaxItems());
    }

    @Test
    void testSettingsExposeSafeDrawerControlsOnly() {
        DrawerFeature feature = new DrawerFeature();
        SettingRegistry registry = new SettingRegistry();
        AdminState state = new AdminState();
        feature.registerSettings(registry, state);

        assertEquals(4, registry.get("drawer").size());
        assertTrue(registry.get("drawer").stream().noneMatch(setting ->
                setting.key().contains("tier") || setting.key().contains("capacity")));
    }

    @Test
    void testBlockedCategoriesExist() {
        // Verify that blocked categories are defined (actual values tested in integration tests)
        // This test just ensures the Drawer class loads properly
        assertNotNull(Drawer.class);
        assertFalse(Drawer.defaultBlockedCategories().isEmpty());
    }

    @Test
    void testAllTiersHaveValidData() {
        DrawerTier[] tiers = DrawerTier.values();
        for (DrawerTier tier : tiers) {
            assertNotNull(tier.getDisplayName(), "Tier " + tier.name() + " should have display name");
            assertNotNull(tier.getColor(), "Tier " + tier.name() + " should have color");
            assertTrue(tier.getLevel() > 0, "Tier " + tier.name() + " should have positive level");
            
            // Capacity should be positive or -1 for unlimited
            int capacity = tier.getStackCapacity();
            assertTrue(capacity > 0 || capacity == -1, 
                "Tier " + tier.name() + " should have positive capacity or -1 for unlimited");
        }
    }
}
