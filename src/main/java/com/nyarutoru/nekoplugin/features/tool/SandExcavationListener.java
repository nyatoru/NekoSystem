package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Handles Sand Excavation events using ActiveToolAPI.
 * Extends AbstractVeinMiner for optimized BFS mining.
 * Allows mass mining of sand and gravel with shovels.
 */
public class SandExcavationListener extends AbstractVeinMiner {

    public static final String TOOL_NAME = "Sand Excavation";
    private static final int MAX_BLOCKS = 250;

    // Excavatable materials
    private static final Set<Material> EXCAVATABLE = Set.of(
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL);

    private final Predicate<Player> toolPredicate = this::isHoldingShovel;

    @Override
    protected String getToolName() {
        return TOOL_NAME;
    }

    @Override
    protected int getMaxBlocks() {
        return MAX_BLOCKS;
    }

    @Override
    protected int[][] getSearchOffsets() {
        return CARDINAL_OFFSETS; // Use 6-directional for sand/gravel
    }

    @Override
    protected Set<Material> getTargetMaterials() {
        return EXCAVATABLE;
    }

    @Override
    protected Predicate<Player> getToolPredicate() {
        return toolPredicate;
    }

    private boolean isHoldingShovel(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return ItemUtils.isShovel(item);
    }
}
