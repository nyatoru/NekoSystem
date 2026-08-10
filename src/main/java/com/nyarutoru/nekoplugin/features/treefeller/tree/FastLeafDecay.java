package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Accelerates natural decay for leaves exposed by TreeFeller.
 */
public final class FastLeafDecay {

    private static final int DECAY_DISTANCE = 7;

    /**
     * Schedules natural decay checks for the detected leaves of a felled tree.
     *
     * @param world the world containing the leaves
     * @param leaves the detected leaf positions
     * @param initialDelayTicks delay before decay checks can begin
     */
    public void schedule(World world, List<BlockPos> leaves, long initialDelayTicks,
                         BooleanSupplier isCurrent, Consumer<SchedulerUtils.TaskHandle> taskOwner) {
        if (!TreeFellerConfig.FAST_LEAF_DECAY_ENABLED) {
            return;
        }

        int minimumDelay = TreeFellerConfig.FAST_LEAF_DECAY_MIN_DELAY_TICKS;
        int maximumDelay = TreeFellerConfig.FAST_LEAF_DECAY_MAX_DELAY_TICKS;
        if (minimumDelay > maximumDelay) {
            maximumDelay = minimumDelay;
        }

        for (BlockPos leafPos : leaves) {
            long delay = initialDelayTicks + ThreadLocalRandom.current().nextLong(
                    minimumDelay,
                    maximumDelay + 1L);
            scheduleWithRetry(world, leafPos, delay, isCurrent, taskOwner, 0);
        }
    }

    private void scheduleWithRetry(World world, BlockPos leafPos, long delay,
                                   BooleanSupplier isCurrent, Consumer<SchedulerUtils.TaskHandle> taskOwner, int attempt) {
        taskOwner.accept(SchedulerUtils.runAtLocationLaterTask(leafPos.toLocation(world), () -> {
            if (!isCurrent.getAsBoolean()) {
                return;
            }
            Block block = leafPos.getBlock(world);
            if (block == null || !(block.getBlockData() instanceof Leaves leaves)) {
                return;
            }
            if (shouldDecay(leaves.isPersistent(), leaves.getDistance())) {
                Material type = block.getType();
                if (type.name().endsWith("_LEAVES")) {
                    block.breakNaturally();
                }
                return;
            }
            // ponytail: spacing <3 dense canopies keep vanilla distance at 1-2 for several ticks after logs gone, single check would leave them floating -> retry 3 times then force-break non-persistent leaves
            if (attempt < 3 && !leaves.isPersistent()) {
                scheduleWithRetry(world, leafPos, 20L, isCurrent, taskOwner, attempt + 1);
            } else if (!leaves.isPersistent()) {
                // final fallback: force decay for non-persistent leaves even if distance hasn't reached 7 yet (spacing too short case)
                Material type = block.getType();
                if (type.name().endsWith("_LEAVES")) {
                    block.breakNaturally();
                }
            }
        }, delay));
    }

    private void decayIfUnsupported(World world, BlockPos leafPos) {
        Block block = leafPos.getBlock(world);
        if (block == null || !(block.getBlockData() instanceof Leaves leaves)) {
            return;
        }
        if (!shouldDecay(leaves.isPersistent(), leaves.getDistance())) {
            return;
        }

        Material type = block.getType();
        if (!type.name().endsWith("_LEAVES")) {
            return;
        }
        block.breakNaturally();
    }

    static boolean shouldDecay(boolean persistent, int distance) {
        return !persistent && distance >= DECAY_DISTANCE;
    }
}
