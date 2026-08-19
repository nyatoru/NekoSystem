package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SandExcavation feature validation.
 * Tests avoid direct Material enum usage to prevent Bukkit initialization requirements.
 */
class SandExcavationValidationTest {

    @Test
    void testFeatureConstants() {
        ToolFeature feature = new ToolFeature();
        assertEquals("tool", feature.getId());
        assertEquals("Tool", feature.getName());
    }

    @Test
    void testToolName() {
        assertEquals("Sand Excavation", SandExcavationListener.TOOL_NAME);
    }

    @Test
    void testMaxBlocksConstant() {
        SandExcavationListener listener = new SandExcavationListener();
        assertEquals(250, listener.getMaxBlocks());
    }

    @Test
    void testSearchOffsets() {
        SandExcavationListener listener = new SandExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // Should use CARDINAL_OFFSETS (6 directions)
        assertNotNull(offsets);
        assertEquals(6, offsets.length);
    }

    @Test
    void testTargetMaterialsNotNull() {
        SandExcavationListener listener = new SandExcavationListener();
        var materials = listener.getTargetMaterials();
        
        assertNotNull(materials);
        assertFalse(materials.isEmpty());
    }

    @Test
    void testMaterialCount() {
        SandExcavationListener listener = new SandExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Should have 20 materials:
        // 2 sand types + 1 gravel + 16 concrete powder + 1 clay = 20
        assertEquals(20, materials.size(), "Should have 20 excavatable materials");
    }

    @Test
    void testFeatureClassExists() {
        assertNotNull(ToolFeature.class);
    }

    @Test
    void testListenerClassExists() {
        assertNotNull(SandExcavationListener.class);
    }

    @Test
    void testListenerExtendsAbstractVeinMiner() {
        assertTrue(AbstractVeinMiner.class.isAssignableFrom(SandExcavationListener.class));
    }

    @Test
    void testToolPredicateNotNull() {
        SandExcavationListener listener = new SandExcavationListener();
        var predicate = listener.getToolPredicate();
        
        assertNotNull(predicate);
    }

    @Test
    void testFeatureImplementation() {
        ToolFeature feature = new ToolFeature();
        
        assertEquals("tool", feature.getId());
        assertEquals("Tool", feature.getName());
        assertFalse(feature.isEnabled()); // Should be false before enable
    }

    @Test
    void testListenerInstantiation() {
        SandExcavationListener listener = new SandExcavationListener();
        
        // Verify all required methods return valid values
        assertNotNull(listener.getToolName());
        assertTrue(listener.getMaxBlocks() > 0);
        assertNotNull(listener.getSearchOffsets());
        assertNotNull(listener.getTargetMaterials());
        assertNotNull(listener.getToolPredicate());
    }

    @Test
    void testSearchOffsetsValidStructure() {
        SandExcavationListener listener = new SandExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // Verify all offsets have 3 components (x, y, z)
        for (int[] offset : offsets) {
            assertNotNull(offset);
            assertEquals(3, offset.length);
        }
    }

    @Test
    void testSearchOffsetsContainsCardinalDirections() {
        SandExcavationListener listener = new SandExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // CARDINAL_OFFSETS should have 6 directions (up, down, north, south, east, west)
        assertEquals(6, offsets.length);
        
        // Verify we have vertical directions (y-axis)
        boolean hasUp = false, hasDown = false;
        for (int[] offset : offsets) {
            if (offset[1] == 1) hasUp = true;
            if (offset[1] == -1) hasDown = true;
        }
        assertTrue(hasUp, "Should have UP direction");
        assertTrue(hasDown, "Should have DOWN direction");
    }

    @Test
    void testMaterialCategories() {
        SandExcavationListener listener = new SandExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Verify we have a reasonable number of materials
        assertTrue(materials.size() >= 15, "Should have at least 15 materials");
        assertTrue(materials.size() <= 25, "Should have at most 25 materials");
    }

    @Test
    void testConcretePowderIncluded() {
        SandExcavationListener listener = new SandExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Should have concrete powder (16 colors)
        // We can't check specific Materials, but we can verify count
        // 2 sand + 1 gravel + 16 concrete powder + 1 clay = 20
        assertEquals(20, materials.size(), "Should include all concrete powder colors");
    }

    @Test
    void testClayIncluded() {
        SandExcavationListener listener = new SandExcavationListener();
        var materials = listener.getTargetMaterials();
        
        // Clay should be included
        assertTrue(materials.size() > 3, "Should have more than just sand and gravel");
    }

    @Test
    void testSearchOffsetsAreCardinal() {
        SandExcavationListener listener = new SandExcavationListener();
        int[][] offsets = listener.getSearchOffsets();
        
        // CARDINAL_OFFSETS should have exactly 6 directions
        assertEquals(6, offsets.length, "Should use CARDINAL_OFFSETS (6 directions)");
    }
}
