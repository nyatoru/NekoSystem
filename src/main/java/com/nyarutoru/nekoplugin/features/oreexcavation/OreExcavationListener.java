package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
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
    private static final int DEFAULT_RADIUS = 8;
    private static final int DEFAULT_MAX_BLOCKS = 250;

    private volatile int radius = DEFAULT_RADIUS;
    private volatile int maxBlocks = DEFAULT_MAX_BLOCKS;

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
    private volatile Set<Material> targetMaterials = Set.copyOf(ORES);

    private final Predicate<Player> toolPredicate = this::isHoldingValidPickaxe;

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> max = SettingDescriptor.integer(
                "max-blocks", "Maximum blocks", DEFAULT_MAX_BLOCKS, 1, 1000,
                ApplySemantics.IMMEDIATE, this::setMaxBlocks);
        SettingDescriptor<Integer> radiusSetting = SettingDescriptor.integer(
                "radius", "Search radius", DEFAULT_RADIUS, 1, 64,
                ApplySemantics.IMMEDIATE, this::setRadius);
        SettingDescriptor<List<Material>> materials = SettingDescriptor.materials(
                "allowed-materials", "Allowed ore materials", List.copyOf(ORES),
                ApplySemantics.IMMEDIATE, this::setTargetMaterials);
        registry.register("ore_excavation", max);
        registry.register("ore_excavation", radiusSetting);
        registry.register("ore_excavation", materials);
        applyStored(state, max);
        applyStored(state, radiusSetting);
        applyStored(state, materials);
    }

    public int getConfiguredMaxBlocks() { return maxBlocks; }

    public void setMaxBlocks(int value) { maxBlocks = value; }

    public int getConfiguredRadius() { return radius; }

    public void setRadius(int value) { radius = value; }

    public void setTargetMaterials(List<Material> materials) {
        if (materials.isEmpty()) throw new IllegalArgumentException("At least one material is required");
        targetMaterials = Set.copyOf(materials);
    }

    private <T> void applyStored(AdminState state, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue("ore_excavation", descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    @Override
    protected String getToolName() {
        return TOOL_NAME;
    }

    @Override
    protected int getMaxBlocks() {
        return maxBlocks;
    }

    @Override
    protected int[][] getSearchOffsets() {
        return FULL_OFFSETS; // Use 26-directional for ores
    }

    @Override
    protected Set<Material> getTargetMaterials() {
        return targetMaterials;
    }

    @Override
    protected Predicate<Player> getToolPredicate() {
        return toolPredicate;
    }

    @Override
    protected int getRadiusSquared() {
        return radius * radius;
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
