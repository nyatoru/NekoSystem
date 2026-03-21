package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * Represents a detected tree structure during tree felling.
 * <p>
 * Contains information about all log and leaf blocks that belong to the tree,
 * the origin block where detection started, and the detected tree type.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public class TreeStructure {

    /**
     * List of all log block positions in the tree.
     */
    private final List<BlockPos> logs;

    /**
     * List of all leaf block positions in the tree.
     */
    private final List<BlockPos> leaves;

    /**
     * The origin block position where tree detection started.
     */
    private final BlockPos origin;

    /**
     * The detected tree type, or null if not matched to a specific type.
     */
    private final TreeType treeType;

    /**
     * Creates a new tree structure.
     *
     * @param logs list of log block positions
     * @param leaves list of leaf block positions
     * @param origin the origin block position
     * @param treeType the detected tree type
     */
    public TreeStructure(List<BlockPos> logs, List<BlockPos> leaves,
                         BlockPos origin, TreeType treeType) {
        this.logs = logs != null ? Collections.unmodifiableList(logs) : Collections.emptyList();
        this.leaves = leaves != null ? Collections.unmodifiableList(leaves) : Collections.emptyList();
        this.origin = origin;
        this.treeType = treeType;
    }

    /**
     * Gets the list of log block positions in the tree.
     *
     * @return an unmodifiable list of log positions
     */
    public List<BlockPos> getLogs() {
        return logs;
    }

    /**
     * Gets the list of leaf block positions in the tree.
     *
     * @return an unmodifiable list of leaf positions
     */
    public List<BlockPos> getLeaves() {
        return leaves;
    }

    /**
     * Gets the origin block position where detection started.
     *
     * @return the origin position
     */
    public BlockPos getOrigin() {
        return origin;
    }

    /**
     * Gets the detected tree type.
     *
     * @return the tree type, or null if not matched
     */
    public TreeType getTreeType() {
        return treeType;
    }

    /**
     * Gets the total number of log blocks in the tree.
     *
     * @return the log count
     */
    public int getLogCount() {
        return logs.size();
    }

    /**
     * Gets the total number of leaf blocks in the tree.
     *
     * @return the leaf count
     */
    public int getLeafCount() {
        return leaves.size();
    }

    /**
     * Gets the total number of blocks in the tree (logs + leaves).
     *
     * @return the total block count
     */
    public int getTotalBlocks() {
        return logs.size() + leaves.size();
    }

    /**
     * Converts the origin position to a Bukkit Location.
     *
     * @param world the world for the location
     * @return the origin location, or null if world is null
     */
    public Location getOriginLocation(org.bukkit.World world) {
        if (world == null || origin == null) {
            return null;
        }
        return origin.toLocation(world);
    }

    @Override
    public String toString() {
        return "TreeStructure{" +
                "logCount=" + logs.size() +
                ", leafCount=" + leaves.size() +
                ", origin=" + origin +
                ", treeType=" + (treeType != null ? treeType.getName() : "null") +
                '}';
    }
}
