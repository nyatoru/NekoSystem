package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Implements BFS-based tree detection algorithm.
 * <p>
 * Starting from a broken log block, traverses all connected log blocks
 * using breadth-first search to identify the complete tree structure.
 * <p>
 * The detector respects configuration settings for:
 * <ul>
 *     <li>Maximum tree size (prevents lag from huge trees)</li>
 *     <li>Diagonal connections (6-directional vs 26-directional)</li>
 *     <li>Player-placed blocks (allow or ignore)</li>
 * </ul>
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class TreeDetector {

    /**
     * Offsets for 6-directional traversal (orthogonal neighbors only).
     */
    private static final BlockPos[] CARDINAL_OFFSETS = {
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1)
    };

    /**
     * Offsets for 26-directional traversal (includes diagonals).
     */
    private static final BlockPos[] ALL_OFFSETS;

    static {
        // Generate all 26 neighbors (3x3x3 cube minus center)
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
        }
        ALL_OFFSETS = offsets.toArray(new BlockPos[0]);
    }

    /**
     * Detects a tree structure starting from the given origin block.
     * <p>
     * Uses BFS to traverse all connected log blocks and identifies
     * the tree type based on the log material.
     *
     * @param world the world containing the tree
     * @param origin the starting block position (the broken log)
     * @return the detected tree structure, or null if no valid tree found
     */
    public TreeStructure detect(World world, BlockPos origin) {
        if (world == null || origin == null) {
            return null;
        }

        // Get the block at origin
        Block originBlock = origin.getBlock(world);
        if (originBlock == null) {
            return null;
        }

        // Find the matching tree type for this log
        TreeType treeType = findTreeType(originBlock.getType());
        if (treeType == null) {
            return null;
        }

        // Perform BFS to find all connected logs
        List<BlockPos> logs = detectLogs(world, origin, treeType);
        if (logs.isEmpty()) {
            return null;
        }

        // Find all leaf blocks associated with this tree
        List<BlockPos> leaves = detectLeaves(world, logs, treeType);

        // Create and return the tree structure
        return new TreeStructure(logs, leaves, origin, treeType);
    }

    /**
     * Performs BFS traversal to detect all connected log blocks.
     *
     * @param world the world containing the tree
     * @param origin the starting position
     * @param treeType the tree type to match
     * @return list of all detected log positions
     */
    private List<BlockPos> detectLogs(World world, BlockPos origin, TreeType treeType) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Start BFS from origin
        queue.add(origin);
        visited.add(origin);

        // Choose offset pattern based on configuration
        BlockPos[] offsets = TreeFellerConfig.DIAGONAL_LOGS ? ALL_OFFSETS : CARDINAL_OFFSETS;
        int maxTreeSize = TreeFellerConfig.MAX_TREE_SIZE;

        while (!queue.isEmpty() && logs.size() < maxTreeSize) {
            BlockPos current = queue.poll();
            logs.add(current);

            // Check all neighboring blocks
            for (BlockPos offset : offsets) {
                BlockPos neighbor = new BlockPos(
                        current.x() + offset.x(),
                        current.y() + offset.y(),
                        current.z() + offset.z()
                );

                // Skip if already visited
                if (visited.contains(neighbor)) {
                    continue;
                }

                // Get the block at this position
                Block block = neighbor.getBlock(world);
                if (block == null) {
                    continue;
                }

                // Check if this block is a matching log
                if (treeType.isLogBlock(block.getType())) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return logs;
    }

    /**
     * Detects all leaf blocks associated with the tree.
     * <p>
     * Searches for leaf blocks within the configured detection range
     * of any log block in the tree.
     * <p>
     * Individual Tree Detection is achieved through log connectivity (BFS).
     * Leaves are associated with the tree if they're within range of the tree's logs.
     *
     * @param world the world containing the tree
     * @param logs the list of log positions
     * @param treeType the tree type to match
     * @return list of all detected leaf positions
     */
    private List<BlockPos> detectLeaves(World world, List<BlockPos> logs, TreeType treeType) {
        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        int detectRange = TreeFellerConfig.LEAF_DETECT_RANGE;
        int detectRangeSquared = detectRange * detectRange;

        // Search around each log for leaves
        for (BlockPos logPos : logs) {
            // Search in a cube around the log
            for (int dx = -detectRange; dx <= detectRange; dx++) {
                for (int dy = -detectRange; dy <= detectRange; dy++) {
                    for (int dz = -detectRange; dz <= detectRange; dz++) {
                        // Skip if outside spherical range
                        int distanceSquared = dx * dx + dy * dy + dz * dz;
                        if (distanceSquared > detectRangeSquared) {
                            continue;
                        }

                        BlockPos checkPos = new BlockPos(logPos.x() + dx, logPos.y() + dy, logPos.z() + dz);

                        // Skip if already visited
                        if (visited.contains(checkPos)) {
                            continue;
                        }

                        Block block = checkPos.getBlock(world);
                        if (block == null) {
                            continue;
                        }

                        // Check if this is a matching leaf block
                        if (treeType.isLeafBlock(block.getType())) {
                            visited.add(checkPos);
                            leaves.add(checkPos);
                        }
                    }
                }
            }
        }

        return leaves;
    }

    /**
     * Finds the tree type that matches the given material.
     *
     * @param material the material to match
     * @return the matching tree type, or null if no match found
     */
    private TreeType findTreeType(org.bukkit.Material material) {
        for (TreeType treeType : TreeFellerConfig.TREE_TYPES) {
            if (treeType.isLogBlock(material)) {
                return treeType;
            }
        }
        return null;
    }
}
