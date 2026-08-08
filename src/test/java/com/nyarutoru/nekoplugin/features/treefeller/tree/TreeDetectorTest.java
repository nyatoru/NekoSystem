package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeDetectorTest {

    private final TreeDetector detector = new TreeDetector();

    @Test
    void detectsDiagonalBranchesAndWoodVariants() {
        TestBlocks blocks = new TestBlocks();
        blocks.log(0, 0, 0, Material.OAK_LOG);
        blocks.log(0, 1, 0, Material.OAK_WOOD);
        blocks.log(1, 2, 0, Material.STRIPPED_OAK_LOG);
        blocks.log(2, 2, 0, Material.STRIPPED_OAK_WOOD);
        blocks.leaf(2, 3, 0, Material.OAK_LEAVES, -1);
        blocks.leaf(2, 4, 0, Material.OAK_LEAVES, -1);

        TreeStructure tree = detector.detect(blocks, new BlockPos(0, 0, 0), true);

        assertNotNull(tree);
        assertEquals(4, tree.getLogCount());
        assertEquals(2, tree.getLeafCount());
        assertFalse(tree.isOverflow());
    }

    @Test
    void followsOnlyConnectedMangroveRootsToTrunk() {
        TestBlocks blocks = new TestBlocks();
        blocks.material(0, 0, 0, Material.MANGROVE_ROOTS);
        blocks.material(1, 0, 0, Material.MANGROVE_ROOTS);
        blocks.log(2, 0, 0, Material.MANGROVE_LOG);
        blocks.log(2, 1, 0, Material.MANGROVE_LOG);
        blocks.material(0, 1, 0, Material.STONE);
        blocks.log(0, 2, 0, Material.OAK_LOG);

        TreeStructure tree = detector.detect(blocks, new BlockPos(0, 0, 0), true);

        assertNotNull(tree);
        assertEquals("mangrove", tree.getTreeType().getName());
        assertEquals(new BlockPos(2, 0, 0), tree.getOrigin());
        assertEquals(2, tree.getLogCount());
    }

    @Test
    void ignoresOtherTrunksWhileFollowingMangroveRoots() {
        TestBlocks blocks = new TestBlocks();
        blocks.material(0, 0, 0, Material.MANGROVE_ROOTS);
        blocks.material(1, 0, 0, Material.MANGROVE_ROOTS);
        blocks.log(0, 1, 0, Material.OAK_LOG);
        blocks.log(2, 0, 0, Material.MANGROVE_LOG);

        TreeStructure tree = detector.detect(blocks, new BlockPos(0, 0, 0), true);

        assertNotNull(tree);
        assertEquals("mangrove", tree.getTreeType().getName());
        assertEquals(new BlockPos(2, 0, 0), tree.getOrigin());
    }

    @Test
    void followsIncreasingLeafDistance() {
        TestBlocks blocks = new TestBlocks();
        blocks.log(0, 0, 0, Material.OAK_LOG);
        blocks.leaf(0, 1, 0, Material.OAK_LEAVES, 1);
        blocks.leaf(0, 2, 0, Material.OAK_LEAVES, 2);
        blocks.leaf(0, 3, 0, Material.OAK_LEAVES, 1);

        TreeStructure tree = detector.detect(blocks, new BlockPos(0, 0, 0), true);

        assertNotNull(tree);
        assertTrue(tree.getLeaves().contains(new BlockPos(0, 2, 0)));
        assertFalse(tree.getLeaves().contains(new BlockPos(0, 3, 0)));
    }

    @Test
    void assignsSharedLeavesToCloserTree() {
        TestBlocks blocks = new TestBlocks();
        blocks.log(0, 0, 0, Material.OAK_LOG);
        blocks.log(0, 1, 0, Material.OAK_LOG);
        blocks.log(0, 2, 0, Material.OAK_LOG);
        blocks.log(6, 0, 0, Material.OAK_LOG);
        blocks.log(6, 1, 0, Material.OAK_LOG);
        blocks.log(6, 2, 0, Material.OAK_LOG);
        for (int x = 0; x <= 6; x++) {
            blocks.leaf(x, 3, 0, Material.OAK_LEAVES, -1);
        }

        TreeStructure tree = detector.detect(blocks, new BlockPos(0, 0, 0), true);

        assertNotNull(tree);
        assertTrue(tree.getLeaves().contains(new BlockPos(2, 3, 0)));
        assertFalse(tree.getLeaves().contains(new BlockPos(4, 3, 0)));
    }

    private static final class TestBlocks implements TreeDetector.BlockLookup {
        private final Map<BlockPos, Material> materials = new HashMap<>();
        private final Map<BlockPos, TreeDetector.Axis> axes = new HashMap<>();
        private final Map<BlockPos, Integer> leafDistances = new HashMap<>();

        void material(int x, int y, int z, Material material) {
            materials.put(new BlockPos(x, y, z), material);
        }

        void log(int x, int y, int z, Material material) {
            BlockPos pos = new BlockPos(x, y, z);
            materials.put(pos, material);
            axes.put(pos, TreeDetector.Axis.Y);
        }

        void leaf(int x, int y, int z, Material material, int distance) {
            BlockPos pos = new BlockPos(x, y, z);
            materials.put(pos, material);
            leafDistances.put(pos, distance);
        }

        @Override
        public Material getMaterial(BlockPos pos) {
            return materials.getOrDefault(pos, Material.AIR);
        }

        @Override
        public TreeDetector.Axis getAxis(BlockPos pos) {
            return axes.get(pos);
        }

        @Override
        public int getLeafDistance(BlockPos pos) {
            return leafDistances.getOrDefault(pos, -1);
        }
    }
}
