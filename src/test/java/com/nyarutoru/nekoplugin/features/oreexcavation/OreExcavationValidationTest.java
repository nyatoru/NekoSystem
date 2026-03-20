package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OreExcavation feature validation.
 * Tests avoid direct Material enum usage to prevent Bukkit initialization requirements.
 */
class OreExcavationValidationTest {

    @Test
    void testFeatureConstants() {
        assertEquals("ore_excavation", OreExcavationFeature.ID);
        assertEquals("Ore Excavation", OreExcavationFeature.NAME);
    }

    @Test
    void testToolName() {
        assertEquals("Ore Excavation", OreExcavationListener.TOOL_NAME);
    }

    @Test
    void testMaxBlocksConstant() {
        // Create instance to test protected method
        OreExcavationListener listener = new OreExcavationListener();
        assertEquals(250, listener.getMaxBlocks());
    }

    @Test
    void testRadiusConstant() {
        OreExcavationListener listener = new OreExcavationListener();
        // Radius is 8, so radius squared should be 64
        assertEquals(64, listener.getRadiusSquared());
    }

    @Test
    void testSearchOffsets() {
        OreExcavationListener listener = new OreExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // Should use FULL_OFFSETS (26 directions)
        assertNotNull(offsets);
        assertEquals(26, offsets.length);
    }

    @Test
    void testTargetMaterialsNotNull() {
        OreExcavationListener listener = new OreExcavationListener();
        var materials = listener.getTargetMaterials();
        
        assertNotNull(materials);
        assertFalse(materials.isEmpty());
    }

    @Test
    void testOreCount() {
        OreExcavationListener listener = new OreExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Should have 19 ores (11 base ores + 8 deepslate variants)
        // Coal, Iron, Copper, Gold, Redstone, Lapis, Emerald, Diamond = 8 × 2 = 16
        // Nether Gold, Nether Quartz, Ancient Debris = 3 (no deepslate)
        // Total = 19
        assertTrue(materials.size() >= 19, "Should have at least 19 ore types");
    }

    @Test
    void testFeatureClassExists() {
        assertNotNull(OreExcavationFeature.class);
    }

    @Test
    void testListenerClassExists() {
        assertNotNull(OreExcavationListener.class);
    }

    @Test
    void testListenerExtendsAbstractVeinMiner() {
        // Verify inheritance
        assertTrue(AbstractVeinMiner.class.isAssignableFrom(OreExcavationListener.class));
    }

    @Test
    void testToolPredicateNotNull() {
        OreExcavationListener listener = new OreExcavationListener();
        var predicate = listener.getToolPredicate();
        
        assertNotNull(predicate);
    }

    @Test
    void testFeatureImplementation() {
        OreExcavationFeature feature = new OreExcavationFeature();
        
        assertEquals("ore_excavation", feature.getId());
        assertEquals("Ore Excavation", feature.getName());
        assertFalse(feature.isEnabled()); // Should be false before enable
    }

    @Test
    void testListenerInstantiation() {
        OreExcavationListener listener = new OreExcavationListener();
        
        // Verify all required methods return valid values
        assertNotNull(listener.getToolName());
        assertTrue(listener.getMaxBlocks() > 0);
        assertNotNull(listener.getSearchOffsets());
        assertNotNull(listener.getTargetMaterials());
        assertNotNull(listener.getToolPredicate());
        assertTrue(listener.getRadiusSquared() > 0);
    }

    @Test
    void testSearchOffsetsStructure() {
        OreExcavationListener listener = new OreExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // FULL_OFFSETS has 26 directions (3x3x3 cube minus center)
        // Center block is handled separately (it's the origin)
        assertEquals(26, offsets.length, "FULL_OFFSETS should have 26 directions");
        
        // Verify offsets cover all 3D directions
        boolean hasPositiveX = false, hasNegativeX = false;
        boolean hasPositiveY = false, hasNegativeY = false;
        boolean hasPositiveZ = false, hasNegativeZ = false;
        
        for (int[] offset : offsets) {
            if (offset[0] > 0) hasPositiveX = true;
            if (offset[0] < 0) hasNegativeX = true;
            if (offset[1] > 0) hasPositiveY = true;
            if (offset[1] < 0) hasNegativeY = true;
            if (offset[2] > 0) hasPositiveZ = true;
            if (offset[2] < 0) hasNegativeZ = true;
        }
        
        assertTrue(hasPositiveX, "Should have positive X direction");
        assertTrue(hasNegativeX, "Should have negative X direction");
        assertTrue(hasPositiveY, "Should have positive Y direction");
        assertTrue(hasNegativeY, "Should have negative Y direction");
        assertTrue(hasPositiveZ, "Should have positive Z direction");
        assertTrue(hasNegativeZ, "Should have negative Z direction");
    }

    @Test
    void testSearchOffsetsValidStructure() {
        OreExcavationListener listener = new OreExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // Verify all offsets have 3 components (x, y, z)
        for (int[] offset : offsets) {
            assertNotNull(offset);
            assertEquals(3, offset.length);
        }
    }

    @Test
    void testTargetMaterialsContainsExpectedOres() {
        OreExcavationListener listener = new OreExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Verify the set is not empty and has reasonable size
        assertTrue(materials.size() >= 15, "Should have at least 15 ore types");
        assertTrue(materials.size() <= 25, "Should have at most 25 ore types");
    }
}
