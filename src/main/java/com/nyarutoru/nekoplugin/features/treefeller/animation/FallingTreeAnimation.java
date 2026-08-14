package com.nyarutoru.nekoplugin.features.treefeller.animation;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Handles optional falling tree animation.
 * <p>
 * When enabled, breaks blocks sequentially from bottom to top (or top to bottom)
 * with a configurable delay between each block, creating a falling tree effect.
 * <p>
 * If animation is disabled in configuration, blocks are broken instantly.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class FallingTreeAnimation {

    /**
     * Plays the falling tree animation for the specified log blocks.
     * <p>
     * Blocks are broken sequentially with the configured delay.
     * The animation runs asynchronously using the scheduler.
     * Folia-compatible: Uses region-based scheduling for block operations.
     *
     * @param world the world containing the tree
     * @param logs the list of log positions to break
     * @param effects the effects handler for playing particles/sounds
     */
    public void playAnimation(World world, List<BlockPos> logs, ItemStack tool, TreeFellerEffects effects,
                              BooleanSupplier isCurrent, Consumer<SchedulerUtils.TaskHandle> taskOwner) {
        List<BlockPos> sortedLogs = sortBlocks(logs);
        if (!TreeFellerConfig.ANIMATION_ENABLED) {
            for (BlockPos pos : sortedLogs) {
                scheduleBlockBreak(world, pos, tool, effects, 0L, isCurrent, taskOwner);
            }
            return;
        }

        for (int index = 0; index < sortedLogs.size(); index++) {
            long delay = (long) index * TreeFellerConfig.ANIMATION_DELAY_TICKS;
            scheduleBlockBreak(world, sortedLogs.get(index), tool, effects, delay, isCurrent, taskOwner);
        }
    }

    private void scheduleBlockBreak(World world, BlockPos pos, ItemStack tool,
                                    TreeFellerEffects effects, long delay, BooleanSupplier isCurrent,
                                    Consumer<SchedulerUtils.TaskHandle> taskOwner) {
        Runnable breakBlock = () -> {
            if (!isCurrent.getAsBoolean()) {
                return;
            }
            Block block;
            try {
                block = pos.getBlock(world);
            } catch (Throwable ex) {
                return;
            }
            if (block == null) return;
            Material type;
            try {
                type = block.getType();
            } catch (Throwable ex) {
                return;
            }
            if (type == Material.AIR) return;
            // Only break logs; avoids breaking if block was replaced
            if (!type.name().endsWith("_LOG") && !type.name().endsWith("_WOOD") && type != Material.MANGROVE_ROOTS) {
                // strict check could miss stripped logs; fallback to not AIR only
                // keep break for any non-air to handle stripped variants, but skip if not log
                // Use TreeFeller LOGS set would require import; broad check: if not log-like, skip
                if (!type.name().contains("LOG") && !type.name().contains("WOOD") && type != Material.MANGROVE_ROOTS) return;
            }
            effects.playEffects(block);
            try {
                block.breakNaturally(tool);
            } catch (Throwable ignored) {}
        };
        SchedulerUtils.TaskHandle task = delay <= 0
                ? SchedulerUtils.runAtLocationTask(pos.toLocation(world), breakBlock)
                : SchedulerUtils.runAtLocationLaterTask(pos.toLocation(world), breakBlock, delay);
        taskOwner.accept(task);
    }
    /**
     * Sorts blocks based on the configured animation direction.
     * <p>
     * If ANIMATION_BOTTOM_UP is true, sorts from lowest Y to highest Y.
     * Otherwise, sorts from highest Y to lowest Y.
     *
     * @param logs the list of block positions to sort
     * @return a new sorted list of positions
     */
    private List<BlockPos> sortBlocks(List<BlockPos> logs) {
        List<BlockPos> sorted = new ArrayList<>(logs);

        if (TreeFellerConfig.ANIMATION_BOTTOM_UP) {
            // Sort from bottom to top (ascending Y)
            sorted.sort(Comparator.comparingInt(BlockPos::y));
        } else {
            // Sort from top to bottom (descending Y)
            sorted.sort(Comparator.comparingInt(BlockPos::y).reversed());
        }

        return sorted;
    }
}
