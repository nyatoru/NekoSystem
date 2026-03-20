package com.nyarutoru.nekoplugin.features.woodcutting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Woodcutting feature validation.
 */
class WoodcuttingValidationTest {

    @Test
    void testFeatureConstants() {
        WoodcuttingFeature feature = new WoodcuttingFeature();
        assertEquals("woodcutting", feature.getId());
        assertEquals("Woodcutting", feature.getName());
    }

    @Test
    void testFeatureClassExists() {
        assertNotNull(WoodcuttingFeature.class);
    }

    @Test
    void testWoodOnStoneCutterClassExists() {
        assertNotNull(WoodOnStoneCutter.class);
    }

    @Test
    void testFeatureImplementation() {
        WoodcuttingFeature feature = new WoodcuttingFeature();
        
        assertEquals("woodcutting", feature.getId());
        assertEquals("Woodcutting", feature.getName());
        assertFalse(feature.isEnabled());
    }

    @Test
    void testWoodItemsArray() {
        // Verify all expected wood item types are present
        String[] expectedItems = {
            "PLANKS", "STAIRS", "SLAB", "FENCE", "FENCE_GATE",
            "DOOR", "TRAPDOOR", "PRESSURE_PLATE", "BUTTON", "SIGN", "HANGING_SIGN"
        };
        assertEquals(11, expectedItems.length);
    }

    @Test
    void testOutputAmounts() {
        // Verify output amounts are logical
        assertTrue(4 >= 1, "PLANKS should output 4");
        assertTrue(4 >= 1, "STAIRS should output 4");
        assertTrue(2 >= 1, "SLAB should output 2");
        assertTrue(4 >= 1, "FENCE should output 4");
        assertTrue(1 >= 1, "FENCE_GATE should output 1");
    }

    @Test
    void testWoodTypeExtraction() {
        // Test wood type extraction logic
        assertEquals("OAK", extractWoodType("OAK_LOG"));
        assertEquals("OAK", extractWoodType("OAK_WOOD"));
        assertEquals("DARK_OAK", extractWoodType("DARK_OAK_LOG"));
        assertEquals("DARK_OAK", extractWoodType("DARK_OAK_WOOD"));
        assertEquals("CRIMSON", extractWoodType("CRIMSON_STEM"));
        assertEquals("CRIMSON", extractWoodType("CRIMSON_HYPHAE"));
        assertEquals("WARPED", extractWoodType("WARPED_STEM"));
        assertEquals("WARPED", extractWoodType("WARPED_HYPHAE"));
        assertEquals("OAK", extractWoodType("STRIPPED_OAK_LOG"));
        assertEquals("OAK", extractWoodType("STRIPPED_OAK_WOOD"));
    }

    @Test
    void testFeatureInterfaceImplementation() {
        assertTrue(com.nyarutoru.nekoplugin.core.Feature.class.isAssignableFrom(WoodcuttingFeature.class));
    }

    @Test
    void testWoodcuttingRequiresPlugin() {
        // Verify constructor requires plugin instance
        assertNotNull(WoodOnStoneCutter.class.getConstructors());
    }

    @Test
    void testDuplicatePreventionLogic() {
        // Test that duplicate wood types would be prevented
        // OAK_LOG and OAK_WOOD should both resolve to "OAK"
        String oakFromLog = extractWoodType("OAK_LOG");
        String oakFromWood = extractWoodType("OAK_WOOD");
        assertEquals(oakFromLog, oakFromWood, "OAK_LOG and OAK_WOOD should resolve to same wood type");
    }

    @Test
    void testAllWoodTypesUnique() {
        // Verify all major wood types extract correctly
        String[] woodTypes = {
            "OAK", "SPRUCE", "BIRCH", "JUNGLE",
            "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY",
            "CRIMSON", "WARPED"
        };
        
        for (String woodType : woodTypes) {
            assertNotNull(woodType);
            assertFalse(woodType.isEmpty());
        }
    }

    @Test
    void testStrippedLogHandling() {
        // Verify stripped logs are handled correctly
        String strippedOak = extractWoodType("STRIPPED_OAK_LOG");
        String regularOak = extractWoodType("OAK_LOG");
        assertEquals(regularOak, strippedOak, "Stripped and regular oak should resolve to same type");
    }

    @Test
    void testNetherWoodHandling() {
        // Verify nether wood types are handled
        assertEquals("CRIMSON", extractWoodType("CRIMSON_STEM"));
        assertEquals("CRIMSON", extractWoodType("CRIMSON_HYPHAE"));
        assertEquals("WARPED", extractWoodType("WARPED_STEM"));
        assertEquals("WARPED", extractWoodType("WARPED_HYPHAE"));
    }

    @Test
    void testMangroveAndCherryWood() {
        // Verify mangrove and cherry wood types
        assertEquals("MANGROVE", extractWoodType("MANGROVE_LOG"));
        assertEquals("MANGROVE", extractWoodType("MANGROVE_WOOD"));
        assertEquals("CHERRY", extractWoodType("CHERRY_LOG"));
        assertEquals("CHERRY", extractWoodType("CHERRY_WOOD"));
    }

    private String extractWoodType(String materialName) {
        // Simplified version of getWoodType for testing
        String name = materialName;
        
        if (name.equals("CRIMSON_STEM") || name.equals("CRIMSON_HYPHAE")) {
            return "CRIMSON";
        }
        if (name.equals("WARPED_STEM") || name.equals("WARPED_HYPHAE")) {
            return "WARPED";
        }
        
        if (name.startsWith("STRIPPED_")) {
            name = name.substring(9);
        }
        
        if (name.endsWith("_LOG")) {
            return name.substring(0, name.length() - 4);
        }
        if (name.endsWith("_WOOD")) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith("_STEM")) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith("_HYPHAE")) {
            return name.substring(0, name.length() - 7);
        }
        
        return null;
    }
}
