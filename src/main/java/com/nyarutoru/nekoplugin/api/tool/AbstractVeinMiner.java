package com.nyarutoru.nekoplugin.api.tool;

import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/**
 * Abstract base class for BFS-based vein mining tools.
 * Reduces code duplication between OreExcavation, SandExcavation, and similar
 * features.
 * Provides optimized BFS traversal with configurable parameters.
 * Folia-safe: BFS is best-effort on Folia (cross-region reads may be skipped),
 * and block breaks are scheduled per-location on Folia.
 */
public abstract class AbstractVeinMiner implements Listener {

    // 6 cardinal directions (for connected blocks)
    protected static final int[][] CARDINAL_OFFSETS = {
            { 0, 1, 0 }, { 0, -1, 0 }, // up, down
            { 1, 0, 0 }, { -1, 0, 0 }, // east, west
            { 0, 0, 1 }, { 0, 0, -1 } // south, north
    };

    // 26 diagonal directions (for ores and similar)
    protected static final int[][] FULL_OFFSETS = {
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

    /**
     * @return The tool name used for ActiveToolAPI
     */
    protected abstract String getToolName();

    /**
     * @return Maximum blocks that can be mined in one operation
     */
    protected abstract int getMaxBlocks();

    /**
     * @return The search offsets to use (CARDINAL_OFFSETS or FULL_OFFSETS)
     */
    protected abstract int[][] getSearchOffsets();

    /**
     * @return Set of materials that can be vein-mined by this tool
     */
    protected abstract Set<Material> getTargetMaterials();

    /**
     * @return Predicate to check if player is holding a valid tool
     */
    protected abstract Predicate<Player> getToolPredicate();

    /**
     * @return Optional radius limit (squared), or -1 for no limit
     */
    protected int getRadiusSquared() {
        return -1;
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;

        Player player = event.getPlayer();
        ActiveToolAPI.getInstance().onShift(
                player,
                getToolName(),
                getToolPredicate(),
                null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!ActiveToolAPI.getInstance().isActive(player, getToolName()))
            return;

        if (!getToolPredicate().test(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "wrong tool");
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!getTargetMaterials().contains(blockType))
            return;

        performVeinMine(player, block, blockType);
    }

    /**
     * Performs BFS vein mining from the origin block.
     */
    protected void performVeinMine(Player player, Block originBlock, Material targetType) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        World world = originBlock.getWorld();
        BlockPos origin = BlockPos.from(originBlock);

        int maxBlocks = getMaxBlocks();
        int radiusSquared = getRadiusSquared();
        int[][] offsets = getSearchOffsets();

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> toCheck = new ArrayDeque<>();
        List<BlockPos> blocksToBreak = new ArrayList<>();

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find all connected blocks of the same type
        while (!toCheck.isEmpty() && blocksToBreak.size() < maxBlocks) {
            BlockPos current = toCheck.poll();

            // Check radius limit if set
            if (radiusSquared > 0 && !current.isWithinRadius(origin, radiusSquared)) {
                continue;
            }

            Block block;
            try {
                block = current.getBlock(world);
            } catch (Throwable ex) {
                // Folia cross-region read may fail; skip this block
                continue;
            }
            Material type;
            try {
                type = block.getType();
            } catch (Throwable ex) {
                continue;
            }
            if (type != targetType)
                continue;

            blocksToBreak.add(current);

            // Check adjacent blocks
            for (int[] offset : offsets) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Material adjType;
                    try {
                        Block adjBlock = adjacent.getBlock(world);
                        adjType = adjBlock.getType();
                    } catch (Throwable ex) {
                        continue;
                    }
                    if (adjType == targetType) {
                        // Early radius check for efficiency
                        if (radiusSquared <= 0 || adjacent.isWithinRadius(origin, radiusSquared)) {
                            toCheck.add(adjacent);
                        }
                    }
                }
            }
        }

        // Break blocks (skip origin as it's broken by the event)
        breakBlocks(player, tool, world, origin, blocksToBreak);
    }

    /**
     * Breaks the collected blocks and handles drops.
     * Can be overridden for custom behavior (e.g., silk touch handling).
     * Folia-safe: on Folia schedules each block break on its region thread and handles durability
     * on the player thread before scheduling.
     */
    protected void breakBlocks(Player player, ItemStack tool, World world,
            BlockPos origin, List<BlockPos> blocksToBreak) {
        if (world == null || player == null || tool == null) return;
        Material originalToolType = tool.getType();

        if (SchedulerUtils.isFolia()) {
            // Folia: durability and tool checks on player thread, then schedule per-block break
            for (BlockPos pos : blocksToBreak) {
                if (pos.equals(origin))
                    continue;

                ItemStack currentTool;
                try {
                    currentTool = player.getInventory().getItemInMainHand();
                } catch (Throwable ex) {
                    break;
                }
                if (currentTool == null || currentTool.getType() != originalToolType)
                    break;

                // Check and consume durability on player thread before scheduling
                try {
                    if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentTool, 1, getToolName())) {
                        break;
                    }
                } catch (Throwable ex) {
                    break;
                }

                // Schedule block break on its region thread
                BlockPos targetPos = pos;
                // Capture drop handling for Folia: use same logic as Paper but on region thread
                SchedulerUtils.runAtLocation(targetPos.toLocation(world), () -> {
                    Block block;
                    try {
                        block = targetPos.getBlock(world);
                    } catch (Throwable ex) {
                        return;
                    }
                    Material t;
                    try {
                        t = block.getType();
                    } catch (Throwable ex) {
                        return;
                    }
                    if (t == Material.AIR || !getTargetMaterials().contains(t)) return;
                    // Drop items at origin for easy collection (origin is nearby, but drop must be on block's region)
                    // For Folia, drop at block location instead of origin to stay on same region
                    try {
                        for (ItemStack drop : block.getDrops(currentTool)) {
                            world.dropItemNaturally(targetPos.toLocation(world), drop);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        block.setType(Material.AIR);
                    } catch (Throwable ignored) {}
                });
            }
            return;
        }

        // Paper path: direct loop
        for (BlockPos pos : blocksToBreak) {
            if (pos.equals(origin))
                continue;

            ItemStack currentTool = player.getInventory().getItemInMainHand();
            if (currentTool == null || currentTool.getType() != originalToolType)
                break;

            // Check and consume durability
            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentTool, 1, getToolName())) {
                break;
            }

            Block block = pos.getBlock(world);

            // Drop items at origin for easy collection
            for (ItemStack drop : block.getDrops(currentTool)) {
                world.dropItemNaturally(origin.toLocation(world), drop);
            }

            block.setType(Material.AIR);
        }
    }
}
