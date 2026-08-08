package com.nyarutoru.nekoplugin.features.treefeller.tree;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents a configured tree type for detection and validation.
 * <p>
 * Tree types define which log and leaf blocks belong to a specific tree,
 * along with validation parameters like maximum height and required leaf count.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public class TreeType {

    /**
     * The unique identifier name for this tree type.
     */
    private final String name;

    /**
     * The material type for log blocks of this tree.
     */
    private final Material logBlock;

    private final Set<Material> logBlocks;

    /**
     * The material type for leaf blocks of this tree.
     */
    private final Material leafBlock;

    /**
     * The maximum height (number of log blocks) for this tree type.
     */
    private final int maxHeight;

    /**
     * The minimum number of leaves required for a valid tree.
     */
    private final int requiredLeaves;

    /**
     * Creates a new tree type configuration.
     *
     * @param name the unique identifier name for this tree type
     * @param logBlock the material type for log blocks
     * @param leafBlock the material type for leaf blocks
     * @param maxHeight the maximum height (log count) for this tree
     * @param requiredLeaves the minimum number of leaves required
     */
    public TreeType(String name, Material logBlock, Material leafBlock,
                    int maxHeight, int requiredLeaves) {
        this.name = name;
        this.logBlock = logBlock;
        this.logBlocks = Collections.unmodifiableSet(createLogBlocks(logBlock));
        this.leafBlock = leafBlock;
        this.maxHeight = maxHeight;
        this.requiredLeaves = requiredLeaves;
    }

    private static EnumSet<Material> createLogBlocks(Material logBlock) {
        String species = logBlock.name().substring(0, logBlock.name().length() - "_LOG".length());
        EnumSet<Material> materials = EnumSet.of(logBlock);
        addMaterial(materials, species + "_WOOD");
        addMaterial(materials, "STRIPPED_" + species + "_LOG");
        addMaterial(materials, "STRIPPED_" + species + "_WOOD");
        return materials;
    }

    private static void addMaterial(Set<Material> materials, String name) {
        Material material = Material.matchMaterial(name);
        if (material != null) {
            materials.add(material);
        }
    }

    /**
     * Gets the unique identifier name for this tree type.
     *
     * @return the tree type name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the material type for log blocks of this tree.
     *
     * @return the log block material
     */
    public Material getLogBlock() {
        return logBlock;
    }

    public Set<Material> getLogBlocks() {
        return logBlocks;
    }

    /**
     * Gets the material type for leaf blocks of this tree.
     *
     * @return the leaf block material
     */
    public Material getLeafBlock() {
        return leafBlock;
    }

    /**
     * Gets the maximum height (number of log blocks) for this tree type.
     *
     * @return the maximum height
     */
    public int getMaxHeight() {
        return maxHeight;
    }

    /**
     * Gets the minimum number of leaves required for a valid tree.
     *
     * @return the required leaf count
     */
    public int getRequiredLeaves() {
        return requiredLeaves;
    }

    /**
     * Checks if the given material is a log block for this tree type.
     *
     * @param material the material to check
     * @return true if the material is a log block for this tree, false otherwise
     */
    public boolean isLogBlock(Material material) {
        return logBlocks.contains(material);
    }

    /**
     * Checks if the given material is a leaf block for this tree type.
     *
     * @param material the material to check
     * @return true if the material is a leaf block for this tree, false otherwise
     */
    public boolean isLeafBlock(Material material) {
        return leafBlock == material;
    }

    @Override
    public String toString() {
        return "TreeType{" +
                "name='" + name + '\'' +
                ", logBlock=" + logBlock +
                ", leafBlock=" + leafBlock +
                ", maxHeight=" + maxHeight +
                ", requiredLeaves=" + requiredLeaves +
                '}';
    }
}
