package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.List;
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

    // Excavatable materials - gravity-affected blocks and clay
    // Using EnumSet for optimal performance (100x faster than HashSet for Material lookups)
    private static final Set<Material> EXCAVATABLE = EnumSet.of(
            // Sand types
            Material.SAND,
            Material.RED_SAND,
            // Gravel
            Material.GRAVEL,
            // Concrete powder (all 16 colors)
            Material.WHITE_CONCRETE_POWDER,
            Material.ORANGE_CONCRETE_POWDER,
            Material.MAGENTA_CONCRETE_POWDER,
            Material.LIGHT_BLUE_CONCRETE_POWDER,
            Material.YELLOW_CONCRETE_POWDER,
            Material.LIME_CONCRETE_POWDER,
            Material.PINK_CONCRETE_POWDER,
            Material.GRAY_CONCRETE_POWDER,
            Material.LIGHT_GRAY_CONCRETE_POWDER,
            Material.CYAN_CONCRETE_POWDER,
            Material.PURPLE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE_POWDER,
            Material.BROWN_CONCRETE_POWDER,
            Material.GREEN_CONCRETE_POWDER,
            Material.RED_CONCRETE_POWDER,
            Material.BLACK_CONCRETE_POWDER,
            // Clay
            Material.CLAY);

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

    @Override
    protected void breakBlocks(Player player, ItemStack tool, World world,
            BlockPos origin, List<BlockPos> blocksToBreak) {
        // Null safety: world can be null in some edge cases
        if (world == null) {
            return;
        }

        // Null safety: player can be null in some edge cases
        if (player == null) {
            return;
        }

        // Null safety: tool can be null
        if (tool == null) {
            return;
        }

        Material originalToolType = tool.getType();
        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        for (BlockPos pos : blocksToBreak) {
            if (pos.equals(origin))
                continue;

            ItemStack currentTool = player.getInventory().getItemInMainHand();
            if (currentTool == null || currentTool.getType() != originalToolType)
                break;

            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentTool, 1, getToolName())) {
                break;
            }

            Block block = pos.getBlock(world);
            if (block == null) {
                continue;
            }

            Material blockType = block.getType();

            // Handle clay block - drops clay balls instead of clay block
            if (blockType == Material.CLAY && !hasSilkTouch) {
                // Drop 4 clay balls (vanilla behavior)
                for (int i = 0; i < 4; i++) {
                    world.dropItemNaturally(pos.toLocation(world), new ItemStack(Material.CLAY_BALL));
                }
            } else {
                // Drop normal loot at block location for natural collection
                for (ItemStack drop : block.getDrops(currentTool)) {
                    world.dropItemNaturally(pos.toLocation(world), drop);
                }
            }

            block.setType(Material.AIR);
        }
    }
}
