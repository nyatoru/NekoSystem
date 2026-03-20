package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import com.nyarutoru.nekoplugin.features.hammer.HammerRecipes;
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
 * Handles ore excavation events using ActiveToolAPI.
 * Extends AbstractVeinMiner for optimized BFS mining.
 */
public class OreExcavationListener extends AbstractVeinMiner {

    public static final String TOOL_NAME = "Ore Excavation";
    private static final int RADIUS = 8;
    private static final int RADIUS_SQUARED = RADIUS * RADIUS;
    private static final int MAX_BLOCKS = 250;

    // Ores that can be vein-mined
    // Using EnumSet for optimal performance (100x faster than HashSet for Material lookups)
    private static final Set<Material> ORES = EnumSet.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS);

    private final Predicate<Player> toolPredicate = this::isHoldingValidPickaxe;

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
        return FULL_OFFSETS; // Use 26-directional for ores
    }

    @Override
    protected Set<Material> getTargetMaterials() {
        return ORES;
    }

    @Override
    protected Predicate<Player> getToolPredicate() {
        return toolPredicate;
    }

    @Override
    protected int getRadiusSquared() {
        return RADIUS_SQUARED;
    }

    /**
     * Checks if player is holding a valid pickaxe (not a hammer).
     */
    private boolean isHoldingValidPickaxe(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return ItemUtils.isPickaxe(item) && !HammerRecipes.isHammer(item);
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
            
            Material oreType = block.getType();

            // Handle silk touch for ore blocks - drop at block location to prevent stacking lag
            if (hasSilkTouch) {
                // Drop silk touch ore at the block's location for natural collection
                world.dropItemNaturally(pos.toLocation(world), new ItemStack(oreType));
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
