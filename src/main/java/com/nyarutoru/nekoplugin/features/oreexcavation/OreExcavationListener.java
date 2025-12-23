package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.features.hammer.HammerRecipes;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles ore excavation events using ActiveToolAPI.
 * Optimized with BlockPos for reduced memory allocation.
 */
public class OreExcavationListener implements Listener {

    public static final String TOOL_NAME = "Ore Excavation";
    private static final int RADIUS = 8;
    private static final int RADIUS_SQUARED = RADIUS * RADIUS;
    private static final int MAX_BLOCKS = 250;

    // Static offset array for 3D diagonal neighbors
    private static final int[][] OFFSETS = new int[][] {
            { -1, -1, -1 }, { -1, -1, 0 }, { -1, -1, 1 },
            { -1, 0, -1 }, { -1, 0, 0 }, { -1, 0, 1 },
            { -1, 1, -1 }, { -1, 1, 0 }, { -1, 1, 1 },
            { 0, -1, -1 }, { 0, -1, 0 }, { 0, -1, 1 },
            { 0, 0, -1 }, /* center */ { 0, 0, 1 },
            { 0, 1, -1 }, { 0, 1, 0 }, { 0, 1, 1 },
            { 1, -1, -1 }, { 1, -1, 0 }, { 1, -1, 1 },
            { 1, 0, -1 }, { 1, 0, 0 }, { 1, 0, 1 },
            { 1, 1, -1 }, { 1, 1, 0 }, { 1, 1, 1 }
    };

    // Ores that can be vein-mined
    private static final Set<Material> ORES = Set.of(
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

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;

        Player player = event.getPlayer();

        ActiveToolAPI.getInstance().onShift(
                player,
                TOOL_NAME,
                this::isHoldingValidPickaxe,
                null);
    }

    /**
     * Checks if player is holding a valid pickaxe (not a hammer).
     */
    private boolean isHoldingValidPickaxe(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return ItemUtils.isPickaxe(item) && !HammerRecipes.isHammer(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME))
            return;

        if (!isHoldingValidPickaxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "no pickaxe");
            return;
        }

        Block block = event.getBlock();
        Material oreType = block.getType();

        if (!isOre(oreType))
            return;

        veinMine(player, block, oreType);
    }

    private boolean isOre(Material material) {
        return ORES.contains(material);
    }

    private void veinMine(Player player, Block originBlock, Material oreType) {
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        boolean hasSilkTouch = pickaxe.containsEnchantment(Enchantment.SILK_TOUCH);

        World world = originBlock.getWorld();
        BlockPos origin = BlockPos.from(originBlock.getLocation());

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toCheck = new ArrayDeque<>();
        List<BlockPos> toBreak = new ArrayList<>();

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find connected ores within radius
        while (!toCheck.isEmpty() && toBreak.size() < MAX_BLOCKS) {
            BlockPos current = toCheck.poll();

            if (current.distanceSquared(origin) > RADIUS_SQUARED)
                continue;

            Block block = current.getBlock(world);
            if (block.getType() != oreType)
                continue;

            toBreak.add(current);

            // Check all 26 adjacent blocks
            for (int[] offset : OFFSETS) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);

                if (!visited.contains(adjacent) && adjacent.distanceSquared(origin) <= RADIUS_SQUARED) {
                    visited.add(adjacent);
                    if (adjacent.getBlock(world).getType() == oreType) {
                        toCheck.add(adjacent);
                    }
                }
            }
        }

        // Break all found ores (skip origin since it's already broken by the event)
        int broken = 0;
        for (BlockPos pos : toBreak) {
            if (pos.equals(origin))
                continue;

            ItemStack currentPickaxe = player.getInventory().getItemInMainHand();
            if (currentPickaxe.getType() != pickaxe.getType()) {
                break;
            }

            // Check and consume durability
            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentPickaxe, 1, TOOL_NAME)) {
                break;
            }

            Block oreBlock = pos.getBlock(world);

            // Break block with proper drops at origin location
            if (hasSilkTouch) {
                world.dropItemNaturally(origin.toLocation(world), new ItemStack(oreType));
                oreBlock.setType(Material.AIR);
            } else {
                for (ItemStack drop : oreBlock.getDrops(currentPickaxe)) {
                    world.dropItemNaturally(origin.toLocation(world), drop);
                }
                oreBlock.setType(Material.AIR);
            }

            broken++;
        }
    }
}
