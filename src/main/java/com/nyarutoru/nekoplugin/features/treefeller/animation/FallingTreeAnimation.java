package com.nyarutoru.nekoplugin.features.treefeller.animation;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
     *
     * @param world the world containing the tree
     * @param logs the list of log positions to break
     * @param effects the effects handler for playing particles/sounds
     */
    public void playAnimation(World world, List<BlockPos> logs, TreeFellerEffects effects) {
        if (!TreeFellerConfig.ANIMATION_ENABLED) {
            // Instant breaking - break all blocks immediately
            breakAllBlocksInstantly(world, logs, effects);
            return;
        }

        // Sort blocks based on animation direction
        List<BlockPos> sortedLogs = sortBlocks(logs);

        // Schedule sequential block breaking
        SchedulerUtils.runAsyncTimer(new Runnable() {
            private int index = 0;

            @Override
            public void run() {
                if (index >= sortedLogs.size()) {
                    return; // Animation complete
                }

                BlockPos pos = sortedLogs.get(index);
                Block block = pos.getBlock(world);

                if (block != null && block.getType() != Material.AIR) {
                    block.breakNaturally();
                    effects.playEffects(block);
                }

                index++;
            }
        }, 0L, TreeFellerConfig.ANIMATION_DELAY_TICKS);
    }

    /**
     * Breaks all blocks instantly without animation.
     *
     * @param world the world containing the blocks
     * @param logs the list of log positions to break
     * @param effects the effects handler
     */
    private void breakAllBlocksInstantly(World world, List<BlockPos> logs, TreeFellerEffects effects) {
        for (BlockPos pos : logs) {
            Block block = pos.getBlock(world);
            if (block != null && block.getType() != Material.AIR) {
                block.breakNaturally();
                effects.playEffects(block);
            }
        }
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
