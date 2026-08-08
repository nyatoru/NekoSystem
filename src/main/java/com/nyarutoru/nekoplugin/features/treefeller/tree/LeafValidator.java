package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Validates that a detected tree structure has sufficient leaves to be considered a valid tree.
 * <p>
 * Leaf validation prevents tree felling of player-built structures that lack proper leaf coverage.
 * The validator searches for leaf blocks within a configured range of the trunk blocks.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class LeafValidator {

    /**
     * The tree type to validate against.
     */
    private final TreeType treeType;

    /**
     * Creates a new LeafValidator for the specified tree type.
     *
     * @param treeType the tree type to validate
     */
    public LeafValidator(TreeType treeType) {
        this.treeType = treeType;
    }

    /**
     * Validates that the detected tree structure has sufficient leaves.
     * <p>
     * Checks if the number of leaf blocks within the detection range
     * meets or exceeds the minimum required leaf count for the tree type.
     *
     * @param world the world containing the tree
     * @param logs the list of log block positions
     * @param leaves the list of detected leaf block positions
     * @return true if the tree has sufficient leaves, false otherwise
     */
    public boolean validate(World world, List<BlockPos> logs, List<BlockPos> leaves) {
        // If leaves are not required, validation always passes
        if (!TreeFellerConfig.REQUIRE_LEAVES) {
            return true;
        }

        // Count leaves within the detection range
        int validLeafCount = countValidLeaves(world, logs, leaves);

        // Check if leaf count meets the minimum requirement
        return validLeafCount >= treeType.getRequiredLeaves();
    }

    /**
     * Counts the number of valid leaf blocks within the detection range.
     *
     * @param world the world containing the tree
     * @param logs the list of log block positions
     * @param leaves the list of detected leaf block positions
     * @return the count of valid leaves
     */
    private int countValidLeaves(World world, List<BlockPos> logs, List<BlockPos> leaves) {
        int count = 0;
        int detectRange = TreeFellerConfig.LEAF_DETECT_RANGE;
        int detectRangeSquared = detectRange * detectRange;

        for (BlockPos leafPos : leaves) {
            // Check if leaf is within range of any log block
            if (isWithinRangeOfLogs(world, leafPos, logs, detectRangeSquared)) {
                // Verify the block is actually a leaf block
                Block block = leafPos.getBlock(world);
                if (block != null && treeType.isLeafBlock(block.getType())) {
                    // Check leaf data if not ignoring
                    if (!TreeFellerConfig.IGNORE_LEAF_DATA) {
                        // Skip leaves that are persistently placed by players
                        // Note: Leaf persistent data checking would go here if needed
                    }
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Checks if a leaf position is within range of any log block.
     *
     * @param world the world (unused for distance calculation)
     * @param leafPos the leaf position to check
     * @param logs the list of log positions
     * @param rangeSquared the squared range for comparison (avoids sqrt)
     * @return true if the leaf is within range of any log, false otherwise
     */
    private boolean isWithinRangeOfLogs(World world, BlockPos leafPos, List<BlockPos> logs, int rangeSquared) {
        for (BlockPos logPos : logs) {
            // Calculate squared distance to avoid expensive sqrt operation
            int dx = leafPos.x() - logPos.x();
            int dy = leafPos.y() - logPos.y();
            int dz = leafPos.z() - logPos.z();
            int distanceSquared = dx * dx + dy * dy + dz * dz;

            if (distanceSquared <= rangeSquared) {
                return true;
            }
        }

        return false;
    }
}
