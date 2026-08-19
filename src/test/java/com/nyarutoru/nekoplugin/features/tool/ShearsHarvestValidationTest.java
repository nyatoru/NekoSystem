package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShearsHarvest feature validation.
 */
class ShearsHarvestValidationTest {

    @Test
    void testFeatureConstants() {
        ToolFeature feature = new ToolFeature();
        assertEquals("tool", feature.getId());
        assertEquals("Tool", feature.getName());
    }

    @Test
    void testToolName() {
        assertEquals("Shears Harvest", ShearsHarvestListener.TOOL_NAME);
    }

    @Test
    void testMaxBlocksConstant() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        assertEquals(ShearsHarvestListener.DEFAULT_MAX_BLOCKS, listener.getMaxBlocks());
        assertEquals(255, ShearsHarvestListener.DEFAULT_MAX_BLOCKS);
    }

    @Test
    void testMaxBlocksConfigurable() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        listener.setMaxBlocks(100);
        assertEquals(100, listener.getMaxBlocks());
    }

    @Test
    void testSearchOffsets() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        int[][] offsets = listener.getSearchOffsets();
        assertNotNull(offsets);
        assertEquals(6, offsets.length, "Should use CARDINAL_OFFSETS (6 directions)");
    }

    @Test
    void testTargetMaterialsNotNull() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        var materials = listener.getTargetMaterials();
        assertNotNull(materials);
        assertFalse(materials.isEmpty());
    }

    @Test
    void testMaterialCount() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        assertEquals(11, listener.getTargetMaterials().size(), "Should include all 11 leaf types");
    }

    @Test
    void testListenerExtendsAbstractVeinMiner() {
        assertTrue(AbstractVeinMiner.class.isAssignableFrom(ShearsHarvestListener.class));
    }

    @Test
    void testToolPredicateNotNull() {
        ShearsHarvestListener listener = new ShearsHarvestListener();
        assertNotNull(listener.getToolPredicate());
    }

    @Test
    void testFeatureImplementation() {
        ToolFeature feature = new ToolFeature();
        assertEquals("tool", feature.getId());
        assertEquals("Tool", feature.getName());
        assertFalse(feature.isEnabled());
    }
}
