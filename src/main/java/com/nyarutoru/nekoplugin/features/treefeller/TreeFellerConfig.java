package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolConfig;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for the TreeFeller feature.
 * <p>
 * All configuration values are hardcoded based on reference implementation defaults.
 * To modify these values, edit this class directly and recompile.
 * <p>
 * This class provides static accessor methods for all configuration values
 * and initializes default tool and tree configurations.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class TreeFellerConfig {

    // =========================================================================
    // Global Settings
    // =========================================================================

    /**
     * Enable/disable the entire TreeFeller feature.
     */
    public static final boolean ENABLED = true;

    /**
     * Debug mode for troubleshooting. When enabled, detailed information
     * is logged to the console during tree felling operations.
     */
    public static final boolean DEBUG = false;

    /**
     * Maximum number of blocks that can be felled in a single operation.
     * This prevents lag from extremely large trees.
     */
    public static final int MAX_TREE_SIZE = 1500;

    /**
     * Require leaves to be present for tree detection.
     * If true, structures without leaves will not be felled.
     */
    public static final boolean REQUIRE_LEAVES = true;

    /**
     * Minimum number of leaves required for a valid tree.
     */
    public static final int MINIMUM_LEAVES = 10;

    /**
     * How far from the bottom can you cut down a tree?
     * Prevents cutting down trees from the top.
     * 1 = must break bottom block, 5 = can break up to 5 blocks from bottom
     * Matches reference implementation default.
     */
    public static final int MAX_HEIGHT_FROM_BOTTOM = 5;

    /**
     * How many logs should be required for logs to be counted as a tree?
     * Prevents single or small log clusters from being felled.
     * Matches reference implementation default.
     */
    public static final int REQUIRED_LOGS = 4;

    /**
     * What is the minimum ratio of vertical to horizontal logs that a tree may have?
     * Prevents felling of horizontal log structures (bridges, houses, etc.).
     * Ratio: vertical_logs / horizontal_logs
     * 0.5 = must have at least 1 vertical log for every 2 horizontal logs
     * Matches reference implementation default.
     */
    public static final double MIN_VERTICAL_LOG_RATIO = 0.5;

    /**
     * Range (in blocks) to search for leaves from trunk blocks.
     * Matches reference implementation default.
     * Values over 6 are useless for vanilla trees (leaves naturally decay).
     */
    public static final int LEAF_DETECT_RANGE = 6;

    public static final boolean DIAGONAL_LEAVES = false;

    public static final boolean SECONDARY_TREE_VERIFICATION = true;

    public static final boolean IGNORE_PARALLEL_TRUNK_PILLARS = false;

    public static final boolean USE_LEAF_DISTANCE = true;

    /**
     * Accelerate natural decay after TreeFeller removes a tree's logs.
     */
    public static final boolean FAST_LEAF_DECAY_ENABLED = true;

    /**
     * Minimum delay before an unsupported leaf is checked for decay.
     */
    public static final int FAST_LEAF_DECAY_MIN_DELAY_TICKS = 20;

    /**
     * Maximum delay before an unsupported leaf is checked for decay.
     */
    public static final int FAST_LEAF_DECAY_MAX_DELAY_TICKS = 100;

    /**
     * Ignore leaf block data (persistent flag).
     * If true, leaves placed by players (with persistent data) will still count.
     */
    public static final boolean IGNORE_LEAF_DATA = false;

    /**
     * Allow player-placed logs to be felled.
     * If false, only naturally generated trees can be felled.
     */
    public static final boolean ALLOW_PLAYER_PLACED = false;

    /**
     * Replant saplings after felling a tree.
     * If true, a sapling will be planted at the base of the tree.
     */
    public static final boolean REPLANT_SAPLINGS = false;

    /**
     * Chance (0.0 to 1.0) for sapling replanting to occur.
     * Only applicable if REPLANT_SAPLINGS is true.
     */
    public static final double REPLANT_CHANCE = 1.0;

    /**
     * If enabled, connected trees will also be felled resulting in a cascade 
     * that could fell an entire forest.
     * DISABLED by default (matches reference implementation default).
     */
    public static final boolean CASCADE = false;

    /**
     * Should trees be able to be partially cut down if the tree is too large?
     * If true, trees exceeding MAX_TREE_SIZE will be partially felled up to the limit.
     * If false, trees exceeding MAX_TREE_SIZE will not be felled at all.
     * Matches reference implementation default (allow-partial: false).
     */
    public static final boolean ALLOW_PARTIAL = true;

    /**
     * How far away from roots should the plugin search for connected trunks?
     * Used for mangrove-style root detection.
     * Matches reference implementation default (root-distance: 6).
     */
    public static final int ROOT_DISTANCE = 6;

    // =========================================================================
    // Animation Settings
    // =========================================================================

    /**
     * Enable falling tree animation.
     * If true, blocks break sequentially from bottom to top.
     * If false, all blocks break instantly.
     */
    public static final boolean ANIMATION_ENABLED = false;

    /**
     * Delay between blocks breaking during animation (in ticks).
     * Only applicable if ANIMATION_ENABLED is true.
     */
    public static final int ANIMATION_DELAY_TICKS = 2;

    /**
     * Break blocks from bottom to top during animation.
     * If false, blocks break from top to bottom.
     */
    public static final boolean ANIMATION_BOTTOM_UP = true;

    // =========================================================================
    // Sound Effects Settings
    // =========================================================================

    /**
     * Enable sound effects when trees are felled.
     */
    public static final boolean SOUNDS_ENABLED = true;

    /**
     * Sound played when a tree is felled.
     */
    public static final org.bukkit.Sound FELL_SOUND = org.bukkit.Sound.BLOCK_WOOD_BREAK;

    /**
     * Volume of the felling sound (0.0 to 1.0).
     */
    public static final float SOUND_VOLUME = 1.0f;

    /**
     * Pitch of the felling sound (0.5 to 2.0).
     */
    public static final float SOUND_PITCH = 1.0f;

    // =========================================================================
    // Tool Configurations
    // =========================================================================

    /**
     * List of all configured tools that can activate tree felling.
     * Initialized with default axe types.
     */
    public static final List<ToolConfig> TOOLS;

    static {
        TOOLS = new ArrayList<>();

        // Wooden Axe
        TOOLS.add(new ToolConfig(
                "Wooden Axe",
                Material.WOODEN_AXE,
                1,
                null,
                null
        ));

        // Stone Axe
        TOOLS.add(new ToolConfig(
                "Stone Axe",
                Material.STONE_AXE,
                1,
                null,
                null
        ));

        // Copper Axe (Minecraft 1.21+)
        TOOLS.add(new ToolConfig(
                "Copper Axe",
                Material.COPPER_AXE,
                1,
                null,
                null
        ));

        // Iron Axe
        TOOLS.add(new ToolConfig(
                "Iron Axe",
                Material.IRON_AXE,
                1,
                null,
                null
        ));

        // Golden Axe
        TOOLS.add(new ToolConfig(
                "Golden Axe",
                Material.GOLDEN_AXE,
                1,
                null,
                null
        ));

        // Diamond Axe
        TOOLS.add(new ToolConfig(
                "Diamond Axe",
                Material.DIAMOND_AXE,
                2,
                null,
                null
        ));

        // Netherite Axe
        TOOLS.add(new ToolConfig(
                "Netherite Axe",
                Material.NETHERITE_AXE,
                3,
                null,
                null
        ));
    }

    // =========================================================================
    // Tree Type Configurations
    // =========================================================================

    /**
     * List of all configured tree types that can be detected and felled.
     * Initialized with all vanilla wood types.
     */
    public static final List<TreeType> TREE_TYPES;

    static {
        TREE_TYPES = new ArrayList<>();

        // Oak Tree
        TREE_TYPES.add(new TreeType(
                "oak",
                Material.OAK_LOG,
                Material.OAK_LEAVES,
                50,
                10
        ));

        // Spruce Tree
        TREE_TYPES.add(new TreeType(
                "spruce",
                Material.SPRUCE_LOG,
                Material.SPRUCE_LEAVES,
                80,
                15
        ));

        // Birch Tree
        TREE_TYPES.add(new TreeType(
                "birch",
                Material.BIRCH_LOG,
                Material.BIRCH_LEAVES,
                50,
                10
        ));

        // Jungle Tree
        TREE_TYPES.add(new TreeType(
                "jungle",
                Material.JUNGLE_LOG,
                Material.JUNGLE_LEAVES,
                100,
                20
        ));

        // Acacia Tree
        TREE_TYPES.add(new TreeType(
                "acacia",
                Material.ACACIA_LOG,
                Material.ACACIA_LEAVES,
                50,
                10
        ));

        // Dark Oak Tree
        TREE_TYPES.add(new TreeType(
                "dark_oak",
                Material.DARK_OAK_LOG,
                Material.DARK_OAK_LEAVES,
                50,
                15
        ));

        // Mangrove Tree
        TREE_TYPES.add(new TreeType(
                "mangrove",
                Material.MANGROVE_LOG,
                Material.MANGROVE_LEAVES,
                60,
                15
        ));

        // Cherry Tree
        TREE_TYPES.add(new TreeType(
                "cherry",
                Material.CHERRY_LOG,
                Material.CHERRY_LEAVES,
                50,
                10
        ));

        // Pale Oak Tree
        TREE_TYPES.add(new TreeType(
                "pale_oak",
                Material.PALE_OAK_LOG,
                Material.PALE_OAK_LEAVES,
                50,
                15
        ));
    }

    /**
     * Check if debug mode is enabled for a specific player.
     * This is a placeholder - actual debug state is managed by TreeFellerState.
     *
     * @param player the player to check
     * @return true if debug mode is enabled, false otherwise
     */
    public static boolean isDebug(Player player) {
        return DEBUG;
    }

    /**
     * Private constructor to prevent instantiation.
     * This class only contains static members.
     */
    private TreeFellerConfig() {
        throw new UnsupportedOperationException("TreeFellerConfig is a utility class and cannot be instantiated");
    }
}
