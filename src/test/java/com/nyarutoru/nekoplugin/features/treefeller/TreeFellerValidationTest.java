package com.nyarutoru.nekoplugin.features.treefeller;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TreeFeller validation logic and constants.
 * Tests material sets, configuration constants, and offset arrays.
 */
@DisplayName("TreeFeller Validation Tests")
class TreeFellerValidationTest {

    @Nested
    @DisplayName("Log Material Set Tests")
    class LogMaterialTests {

        @Test
        @DisplayName("Should contain all oak variants")
        void shouldContainOakLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.OAK_LOG));
            assertTrue(logs.contains(Material.STRIPPED_OAK_LOG));
        }

        @Test
        @DisplayName("Should contain all spruce variants")
        void shouldContainSpruceLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.SPRUCE_LOG));
            assertTrue(logs.contains(Material.STRIPPED_SPRUCE_LOG));
        }

        @Test
        @DisplayName("Should contain all birch variants")
        void shouldContainBirchLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.BIRCH_LOG));
            assertTrue(logs.contains(Material.STRIPPED_BIRCH_LOG));
        }

        @Test
        @DisplayName("Should contain all jungle variants")
        void shouldContainJungleLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.JUNGLE_LOG));
            assertTrue(logs.contains(Material.STRIPPED_JUNGLE_LOG));
        }

        @Test
        @DisplayName("Should contain all acacia variants")
        void shouldContainAcaciaLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.ACACIA_LOG));
            assertTrue(logs.contains(Material.STRIPPED_ACACIA_LOG));
        }

        @Test
        @DisplayName("Should contain all dark oak variants")
        void shouldContainDarkOakLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.DARK_OAK_LOG));
            assertTrue(logs.contains(Material.STRIPPED_DARK_OAK_LOG));
        }

        @Test
        @DisplayName("Should contain all mangrove variants")
        void shouldContainMangroveLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.MANGROVE_LOG));
            assertTrue(logs.contains(Material.STRIPPED_MANGROVE_LOG));
        }

        @Test
        @DisplayName("Should contain all cherry variants")
        void shouldContainCherryLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.CHERRY_LOG));
            assertTrue(logs.contains(Material.STRIPPED_CHERRY_LOG));
        }

        @Test
        @DisplayName("Should contain crimson and warped stems")
        void shouldContainNetherStems() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertTrue(logs.contains(Material.CRIMSON_STEM));
            assertTrue(logs.contains(Material.WARPED_STEM));
            assertTrue(logs.contains(Material.STRIPPED_CRIMSON_STEM));
            assertTrue(logs.contains(Material.STRIPPED_WARPED_STEM));
        }

        @Test
        @DisplayName("Should not contain non-log materials")
        void shouldNotContainNonLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> logs = getLogSet();

            assertFalse(logs.contains(Material.OAK_PLANKS));
            assertFalse(logs.contains(Material.OAK_LEAVES));
            assertFalse(logs.contains(Material.DIRT));
            assertFalse(logs.contains(Material.STONE));
        }
    }

    @Nested
    @DisplayName("Leaf Material Set Tests")
    class LeafMaterialTests {

        @Test
        @DisplayName("Should contain all standard leaf types")
        void shouldContainStandardLeaves() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> leaves = getLeavesSet();

            assertTrue(leaves.contains(Material.OAK_LEAVES));
            assertTrue(leaves.contains(Material.SPRUCE_LEAVES));
            assertTrue(leaves.contains(Material.BIRCH_LEAVES));
            assertTrue(leaves.contains(Material.JUNGLE_LEAVES));
            assertTrue(leaves.contains(Material.ACACIA_LEAVES));
            assertTrue(leaves.contains(Material.DARK_OAK_LEAVES));
        }

        @Test
        @DisplayName("Should contain mangrove and cherry leaves")
        void shouldContainSpecialLeaves() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> leaves = getLeavesSet();

            assertTrue(leaves.contains(Material.MANGROVE_LEAVES));
            assertTrue(leaves.contains(Material.CHERRY_LEAVES));
        }

        @Test
        @DisplayName("Should contain azalea leaves")
        void shouldContainAzaleaLeaves() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> leaves = getLeavesSet();

            assertTrue(leaves.contains(Material.AZALEA_LEAVES));
            assertTrue(leaves.contains(Material.FLOWERING_AZALEA_LEAVES));
        }

        @Test
        @DisplayName("Should contain nether wart blocks")
        void shouldContainNetherWartBlocks() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> leaves = getLeavesSet();

            assertTrue(leaves.contains(Material.NETHER_WART_BLOCK));
            assertTrue(leaves.contains(Material.WARPED_WART_BLOCK));
        }

        @Test
        @DisplayName("Should not contain non-leaf materials")
        void shouldNotContainNonLeaves() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> leaves = getLeavesSet();

            assertFalse(leaves.contains(Material.OAK_LOG));
            assertFalse(leaves.contains(Material.DIRT));
            assertFalse(leaves.contains(Material.GLASS));
        }
    }

    @Nested
    @DisplayName("Structure Block Set Tests")
    class StructureBlockTests {

        @Test
        @DisplayName("Should contain fence types")
        void shouldContainFences() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertTrue(structureBlocks.contains(Material.OAK_FENCE));
            assertTrue(structureBlocks.contains(Material.SPRUCE_FENCE));
            assertTrue(structureBlocks.contains(Material.NETHER_BRICK_FENCE));
        }

        @Test
        @DisplayName("Should contain stair types")
        void shouldContainStairs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertTrue(structureBlocks.contains(Material.OAK_STAIRS));
            assertTrue(structureBlocks.contains(Material.STONE_STAIRS));
            assertTrue(structureBlocks.contains(Material.BRICK_STAIRS));
        }

        @Test
        @DisplayName("Should contain slab types")
        void shouldContainSlabs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertTrue(structureBlocks.contains(Material.OAK_SLAB));
            assertTrue(structureBlocks.contains(Material.STONE_SLAB));
            assertTrue(structureBlocks.contains(Material.BRICK_SLAB));
        }

        @Test
        @DisplayName("Should contain door and trapdoor types")
        void shouldContainDoors() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertTrue(structureBlocks.contains(Material.OAK_DOOR));
            assertTrue(structureBlocks.contains(Material.IRON_DOOR));
            assertTrue(structureBlocks.contains(Material.OAK_TRAPDOOR));
            assertTrue(structureBlocks.contains(Material.IRON_TRAPDOOR));
        }

        @Test
        @DisplayName("Should contain utility blocks")
        void shouldContainUtilityBlocks() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertTrue(structureBlocks.contains(Material.CHEST));
            assertTrue(structureBlocks.contains(Material.FURNACE));
            assertTrue(structureBlocks.contains(Material.CRAFTING_TABLE));
            assertTrue(structureBlocks.contains(Material.ENCHANTING_TABLE));
        }

        @Test
        @DisplayName("Should not contain natural blocks")
        void shouldNotContainNaturalBlocks() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> structureBlocks = getStructureBlocksSet();

            assertFalse(structureBlocks.contains(Material.OAK_LOG));
            assertFalse(structureBlocks.contains(Material.DIRT));
            assertFalse(structureBlocks.contains(Material.GRASS_BLOCK));
            assertFalse(structureBlocks.contains(Material.STONE));
        }
    }

    @Nested
    @DisplayName("Configuration Constants Tests")
    class ConfigurationConstantsTests {

        @Test
        @DisplayName("Should have reasonable minimum logs threshold")
        void shouldHaveReasonableMinLogs() throws NoSuchFieldException, IllegalAccessException {
            int minLogs = getConstant("MIN_LOGS_FOR_TREE");
            assertTrue(minLogs >= 1, "MIN_LOGS_FOR_TREE should be at least 1");
            assertTrue(minLogs <= 10, "MIN_LOGS_FOR_TREE should not exceed 10");
        }

        @Test
        @DisplayName("Should have reasonable minimum leaves threshold")
        void shouldHaveReasonableMinLeaves() throws NoSuchFieldException, IllegalAccessException {
            int minLeaves = getConstant("MIN_LEAVES_FOR_TREE");
            assertTrue(minLeaves >= 4, "MIN_LEAVES_FOR_TREE should be at least 4");
            assertTrue(minLeaves <= 50, "MIN_LEAVES_FOR_TREE should not exceed 50");
        }

        @Test
        @DisplayName("Should have reasonable structure check radius")
        void shouldHaveReasonableStructureRadius() throws NoSuchFieldException, IllegalAccessException {
            int radius = getConstant("STRUCTURE_CHECK_RADIUS");
            assertTrue(radius >= 1, "STRUCTURE_CHECK_RADIUS should be at least 1");
            assertTrue(radius <= 5, "STRUCTURE_CHECK_RADIUS should not exceed 5");
        }

        @Test
        @DisplayName("Should have reasonable max structure blocks allowed")
        void shouldHaveReasonableMaxStructureBlocks() throws NoSuchFieldException, IllegalAccessException {
            int maxBlocks = getConstant("MAX_STRUCTURE_BLOCKS_ALLOWED");
            assertTrue(maxBlocks >= 0, "MAX_STRUCTURE_BLOCKS_ALLOWED should be non-negative");
            assertTrue(maxBlocks <= 10, "MAX_STRUCTURE_BLOCKS_ALLOWED should not exceed 10");
        }
    }

    @Nested
    @DisplayName("Offset Array Tests")
    class OffsetArrayTests {

        @Test
        @DisplayName("Compact offsets should include vertical directions")
        void compactOffsetsShouldIncludeVertical() throws NoSuchFieldException, IllegalAccessException {
            int[][] offsets = getOffsetArray("COMPACT_OFFSETS");

            boolean hasUp = false;
            boolean hasDown = false;

            for (int[] offset : offsets) {
                if (offset[0] == 0 && offset[1] > 0 && offset[2] == 0) hasUp = true;
                if (offset[0] == 0 && offset[1] < 0 && offset[2] == 0) hasDown = true;
            }

            assertTrue(hasUp, "Should have upward offset");
            assertTrue(hasDown, "Should have downward offset");
        }

        @Test
        @DisplayName("Compact offsets should include horizontal directions")
        void compactOffsetsShouldIncludeHorizontal() throws NoSuchFieldException, IllegalAccessException {
            int[][] offsets = getOffsetArray("COMPACT_OFFSETS");

            boolean hasNorth = false;
            boolean hasSouth = false;
            boolean hasEast = false;
            boolean hasWest = false;

            for (int[] offset : offsets) {
                if (offset[0] < 0 && offset[1] == 0 && offset[2] == 0) hasNorth = true;
                if (offset[0] > 0 && offset[1] == 0 && offset[2] == 0) hasSouth = true;
                if (offset[0] == 0 && offset[1] == 0 && offset[2] < 0) hasEast = true;
                if (offset[0] == 0 && offset[1] == 0 && offset[2] > 0) hasWest = true;
            }

            assertTrue(hasNorth, "Should have north offset");
            assertTrue(hasSouth, "Should have south offset");
            assertTrue(hasEast, "Should have east offset");
            assertTrue(hasWest, "Should have west offset");
        }

        @Test
        @DisplayName("Tall tree offsets should have extended vertical range")
        void tallTreeOffsetsShouldHaveExtendedVertical() throws NoSuchFieldException, IllegalAccessException {
            int[][] offsets = getOffsetArray("TALL_TREE_OFFSETS");

            int maxY = 0;
            int minY = 0;

            for (int[] offset : offsets) {
                maxY = Math.max(maxY, offset[1]);
                minY = Math.min(minY, offset[1]);
            }

            assertTrue(maxY >= 2, "Tall tree offsets should reach at least Y+2");
            assertTrue(minY <= -2, "Tall tree offsets should reach at least Y-2");
        }

        @Test
        @DisplayName("Validation offsets should be smaller than detection offsets")
        void validationOffsetsShouldBeSmaller() throws NoSuchFieldException, IllegalAccessException {
            int[][] validationOffsets = getOffsetArray("VALIDATION_LOG_OFFSETS");
            int[][] tallTreeOffsets = getOffsetArray("TALL_TREE_OFFSETS");

            // Validation should be more focused than full detection
            assertTrue(validationOffsets.length <= tallTreeOffsets.length,
                    "Validation offsets should be subset of detection offsets");
        }
    }

    @Nested
    @DisplayName("Mangrove Root Tests")
    class MangroveRootTests {

        @Test
        @DisplayName("Should contain mangrove roots")
        void shouldContainMangroveRoots() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> roots = getMangroveRootsSet();

            assertTrue(roots.contains(Material.MANGROVE_ROOTS));
        }

        @Test
        @DisplayName("Should contain muddy mangrove roots")
        void shouldContainMuddyMangroveRoots() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> roots = getMangroveRootsSet();

            assertTrue(roots.contains(Material.MUDDY_MANGROVE_ROOTS));
        }

        @Test
        @DisplayName("Should not contain regular logs")
        void shouldNotContainLogs() throws NoSuchFieldException, IllegalAccessException {
            Set<Material> roots = getMangroveRootsSet();

            assertFalse(roots.contains(Material.MANGROVE_LOG));
            assertFalse(roots.contains(Material.OAK_LOG));
        }
    }

    // Helper methods for accessing private static fields via reflection

    @SuppressWarnings("unchecked")
    private Set<Material> getLogSet() throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField("LOGS");
        field.setAccessible(true);
        return (Set<Material>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private Set<Material> getLeavesSet() throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField("LEAVES");
        field.setAccessible(true);
        return (Set<Material>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private Set<Material> getStructureBlocksSet() throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField("STRUCTURE_BLOCKS");
        field.setAccessible(true);
        return (Set<Material>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private Set<Material> getMangroveRootsSet() throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField("MANGROVE_ROOTS");
        field.setAccessible(true);
        return (Set<Material>) field.get(null);
    }

    private int getConstant(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private int[][] getOffsetArray(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = TreeFellerListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int[][]) field.get(null);
    }
}
