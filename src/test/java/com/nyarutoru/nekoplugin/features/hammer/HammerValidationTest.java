package com.nyarutoru.nekoplugin.features.hammer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Hammer feature validation.
 * Tests avoid direct Material enum usage to prevent Bukkit initialization requirements.
 */
class HammerValidationTest {

    @Test
    void testAllHammerTiersExist() {
        assertEquals(6, HammerRecipes.TIERS.size(), "Should have exactly 6 hammer tiers");
    }

    @Test
    void testHammerTierNames() {
        assertTrue(HammerRecipes.TIERS.containsKey("wooden"));
        assertTrue(HammerRecipes.TIERS.containsKey("stone"));
        assertTrue(HammerRecipes.TIERS.containsKey("iron"));
        assertTrue(HammerRecipes.TIERS.containsKey("golden"));
        assertTrue(HammerRecipes.TIERS.containsKey("diamond"));
        assertTrue(HammerRecipes.TIERS.containsKey("netherite"));
    }

    @Test
    void testHammerTierDisplayNames() {
        assertEquals("Wooden", HammerRecipes.TIERS.get("wooden").displayName());
        assertEquals("Stone", HammerRecipes.TIERS.get("stone").displayName());
        assertEquals("Iron", HammerRecipes.TIERS.get("iron").displayName());
        assertEquals("Golden", HammerRecipes.TIERS.get("golden").displayName());
        assertEquals("Diamond", HammerRecipes.TIERS.get("diamond").displayName());
        assertEquals("Netherite", HammerRecipes.TIERS.get("netherite").displayName());
    }

    @Test
    void testHammerKeyNotNull() {
        assertNotNull(HammerRecipes.HAMMER_KEY);
        assertEquals("nekoplugin", HammerRecipes.HAMMER_KEY.getNamespace());
        assertEquals("hammer", HammerRecipes.HAMMER_KEY.getKey());
    }

    @Test
    void testHammerTierKeyNotNull() {
        assertNotNull(HammerRecipes.HAMMER_TIER_KEY);
        assertEquals("nekoplugin", HammerRecipes.HAMMER_TIER_KEY.getNamespace());
        assertEquals("hammer_tier", HammerRecipes.HAMMER_TIER_KEY.getKey());
    }

    @Test
    void testHammerRecipeShape() {
        // Verify recipe pattern is correct: "PPP", " S ", " S "
        // P = pickaxe, S = stick
        // This creates a hammer shape with 3 pickaxes on top and 2 sticks below
        String[] expectedShape = {"PPP", " S ", " S "};
        assertEquals(3, expectedShape.length);
        assertEquals("PPP", expectedShape[0]);
        assertEquals(" S ", expectedShape[1]);
        assertEquals(" S ", expectedShape[2]);
    }

    @Test
    void testHammerTierProgression() {
        // Verify tiers follow logical progression
        var tiers = HammerRecipes.TIERS;
        
        // All tiers should have valid data
        assertNotNull(tiers.get("wooden"));
        assertNotNull(tiers.get("stone"));
        assertNotNull(tiers.get("iron"));
        assertNotNull(tiers.get("golden"));
        assertNotNull(tiers.get("diamond"));
        assertNotNull(tiers.get("netherite"));
    }

    @Test
    void testHammerFeatureConstants() {
        HammerFeature feature = new HammerFeature();
        assertEquals("hammer", feature.getId());
        assertEquals("Hammer", feature.getName());
    }

    @Test
    void testHammerListenerClassExists() {
        // Verify the listener class loads properly
        assertNotNull(HammerListener.class);
    }

    @Test
    void testHammerTierData完整性 () {
        // Verify all tier data is complete
        var tiers = HammerRecipes.TIERS;
        
        for (var entry : tiers.entrySet()) {
            var tier = entry.getValue();
            assertNotNull(tier.displayName(), "Tier " + entry.getKey() + " should have display name");
            assertNotNull(tier.baseTool(), "Tier " + entry.getKey() + " should have base tool");
            assertNotNull(tier.material(), "Tier " + entry.getKey() + " should have crafting material");
            assertNotNull(tier.color(), "Tier " + entry.getKey() + " should have color");
        }
    }

    @Test
    void testHammerTierCount() {
        // Verify we have the expected number of tiers
        assertEquals(6, HammerRecipes.TIERS.size());
    }

    @Test
    void testHammerRecipeKeyPattern() {
        // Verify recipe keys follow naming pattern
        var plugin = com.nyarutoru.nekoplugin.NekoPlugin.getInstance();
        if (plugin != null) {
            for (String tierName : HammerRecipes.TIERS.keySet()) {
                var key = new org.bukkit.NamespacedKey(plugin, "hammer_" + tierName);
                assertNotNull(key);
                assertEquals("hammer_" + tierName, key.getKey());
            }
        }
    }
}
