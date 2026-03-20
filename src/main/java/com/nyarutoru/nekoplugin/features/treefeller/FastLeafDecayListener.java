package com.nyarutoru.nekoplugin.features.treefeller;

import com.google.common.collect.Lists;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast leaf decay listener that accelerates natural leaf decay.
 * Listens to vanilla LeavesDecayEvent and recursively triggers decay on
 * adjacent leaves.
 * <p>
 * Uses chunk-based tracking to prevent memory leaks and properly clean up
 * scheduled decays when chunks unload.
 */
public class FastLeafDecayListener implements Listener {

    private static final List<BlockFace> FACES = Lists.newArrayList(
            Arrays.stream(BlockFace.values())
                    .filter(BlockFace::isCartesian)
                    .toList());

    /**
     * Tracks scheduled leaf decays by chunk key to prevent memory leaks.
     * Key format: "worldName:chunkX:chunkZ"
     */
    private static final Map<String, Set<Block>> SCHEDULED_BY_CHUNK = new ConcurrentHashMap<>();

    @EventHandler
    public void onDecay(LeavesDecayEvent event) {
        doDecay(event.getBlock());
    }

    /**
     * Handles chunk unload events to clean up scheduled decays.
     * Prevents memory leaks when chunks are unloaded before decay completes.
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        cleanupChunk(event.getChunk());
    }

    /**
     * Initiates decay for the given block and recursively schedules adjacent leaves.
     *
     * @param block the block to decay
     */
    public void doDecay(final Block block) {
        if (block == null) {
            return;
        }

        Collections.shuffle(FACES);

        for (final BlockFace face : FACES) {
            final Block b = block.getRelative(face);
            if (b == null) {
                continue;
            }

            if (isScheduled(b)) {
                continue;
            }

            if (!(b.getBlockData() instanceof final Leaves leaves) || leaves.isPersistent()
                    || leaves.getDistance() < 7) {
                continue;
            }

            scheduleDecay(b);

            long delay = ThreadLocalRandom.current().nextLong(2, 10);
            SchedulerUtils.runAtLocationLater(b.getLocation(), () -> {
                // Verify block still exists and is still a leaf
                if (!b.getType().name().contains("LEAVES")) {
                    removeScheduled(b);
                    return;
                }

                final LeavesDecayEvent decayEvent = new LeavesDecayEvent(b);
                Bukkit.getPluginManager().callEvent(decayEvent);
                if (decayEvent.isCancelled()) {
                    removeScheduled(b);
                    return;
                }

                b.breakNaturally();
                removeScheduled(b);
            }, delay);
        }
    }

    /**
     * Checks if a block is already scheduled for decay.
     *
     * @param block the block to check
     * @return true if scheduled, false otherwise
     */
    private boolean isScheduled(Block block) {
        String chunkKey = getChunkKey(block.getChunk());
        Set<Block> chunkBlocks = SCHEDULED_BY_CHUNK.get(chunkKey);
        return chunkBlocks != null && chunkBlocks.contains(block);
    }

    /**
     * Schedules a block for decay tracking.
     *
     * @param block the block to schedule
     */
    private void scheduleDecay(Block block) {
        String chunkKey = getChunkKey(block.getChunk());
        SCHEDULED_BY_CHUNK.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(block);
    }

    /**
     * Removes a block from scheduled decay tracking.
     *
     * @param block the block to remove
     */
    private void removeScheduled(Block block) {
        String chunkKey = getChunkKey(block.getChunk());
        Set<Block> chunkBlocks = SCHEDULED_BY_CHUNK.get(chunkKey);
        if (chunkBlocks != null) {
            chunkBlocks.remove(block);
            // Clean up empty sets to prevent memory buildup
            if (chunkBlocks.isEmpty()) {
                SCHEDULED_BY_CHUNK.remove(chunkKey);
            }
        }
    }

    /**
     * Cleans up all scheduled decays for a chunk when it unloads.
     *
     * @param chunk the chunk being unloaded
     */
    private void cleanupChunk(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        Set<Block> removed = SCHEDULED_BY_CHUNK.remove(chunkKey);
        if (removed != null) {
            removed.clear();
        }
    }

    /**
     * Generates a unique key for a chunk.
     *
     * @param chunk the chunk
     * @return unique chunk key
     */
    private String getChunkKey(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return "unknown:" + System.nanoTime();
        }
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
