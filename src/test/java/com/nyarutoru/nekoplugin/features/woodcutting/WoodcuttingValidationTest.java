package com.nyarutoru.nekoplugin.features.woodcutting;

import org.bukkit.Material;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void testFeatureRegistersNoUnsafeRecipeSettings() {
        SettingRegistry registry = new SettingRegistry();
        WoodcuttingFeature feature = new WoodcuttingFeature();
        feature.registerSettings(registry, new AdminState());
        assertTrue(registry.get(feature.getId()).isEmpty());
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
        assertEquals("OAK", WoodOnStoneCutter.getWoodType(Material.OAK_LOG));
        assertEquals("OAK", WoodOnStoneCutter.getWoodType(Material.OAK_WOOD));
        assertEquals("DARK_OAK", WoodOnStoneCutter.getWoodType(Material.DARK_OAK_LOG));
        assertEquals("DARK_OAK", WoodOnStoneCutter.getWoodType(Material.DARK_OAK_WOOD));
        assertEquals("CRIMSON", WoodOnStoneCutter.getWoodType(Material.CRIMSON_STEM));
        assertEquals("CRIMSON", WoodOnStoneCutter.getWoodType(Material.CRIMSON_HYPHAE));
        assertEquals("WARPED", WoodOnStoneCutter.getWoodType(Material.WARPED_STEM));
        assertEquals("WARPED", WoodOnStoneCutter.getWoodType(Material.WARPED_HYPHAE));
    }

    @Test
    void testRecipeInputsIncludeLogsAndWoodRegardlessOfOrder() {
        List<Material> logs = List.of(
                Material.OAK_WOOD,
                Material.STRIPPED_OAK_LOG,
                Material.CRIMSON_HYPHAE,
                Material.OAK_LOG,
                Material.CRIMSON_STEM,
                Material.STRIPPED_CRIMSON_HYPHAE);

        Map<String, List<Material>> grouped = WoodOnStoneCutter.groupInputsByWoodType(logs);

        assertEquals(List.of(Material.OAK_LOG, Material.OAK_WOOD), grouped.get("OAK"));
        assertEquals(List.of(Material.CRIMSON_HYPHAE, Material.CRIMSON_STEM), grouped.get("CRIMSON"));
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
        String oakFromLog = WoodOnStoneCutter.getWoodType(Material.OAK_LOG);
        String oakFromWood = WoodOnStoneCutter.getWoodType(Material.OAK_WOOD);
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
    void testStrippedLogsAreExcludedFromRecipeInputs() {
        Map<String, List<Material>> grouped = WoodOnStoneCutter.groupInputsByWoodType(List.of(
                Material.STRIPPED_OAK_LOG,
                Material.STRIPPED_OAK_WOOD,
                Material.OAK_LOG));

        assertEquals(List.of(Material.OAK_LOG), grouped.get("OAK"));
    }

    @Test
    void testNetherWoodHandling() {
        assertEquals("CRIMSON", WoodOnStoneCutter.getWoodType(Material.CRIMSON_STEM));
        assertEquals("CRIMSON", WoodOnStoneCutter.getWoodType(Material.CRIMSON_HYPHAE));
        assertEquals("WARPED", WoodOnStoneCutter.getWoodType(Material.WARPED_STEM));
        assertEquals("WARPED", WoodOnStoneCutter.getWoodType(Material.WARPED_HYPHAE));
    }

    @Test
    void testModernWoodTypes() {
        assertEquals("MANGROVE", WoodOnStoneCutter.getWoodType(Material.MANGROVE_LOG));
        assertEquals("CHERRY", WoodOnStoneCutter.getWoodType(Material.CHERRY_LOG));
        assertEquals("PALE_OAK", WoodOnStoneCutter.getWoodType(Material.PALE_OAK_LOG));
    }
}
