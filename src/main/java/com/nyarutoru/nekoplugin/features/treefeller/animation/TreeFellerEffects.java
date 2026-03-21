package com.nyarutoru.nekoplugin.features.treefeller.animation;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Handles particle and sound effects for tree felling.
 * <p>
 * Plays configurable visual and audio feedback when blocks are broken
 * during tree felling operations.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class TreeFellerEffects {

    /**
     * Plays effects at the specified block position.
     * <p>
     * Plays sounds based on configuration settings.
     *
     * @param block the block where effects should be played
     */
    public void playEffects(Block block) {
        if (block == null) {
            return;
        }

        playSoundEffects(block.getLocation());
    }

    /**
     * Plays effects at all specified block positions.
     * <p>
     * Efficiently batches effect playback for multiple blocks.
     *
     * @param world the world containing the blocks
     * @param positions the list of block positions
     */
    public void playEffects(World world, List<BlockPos> positions) {
        if (world == null || positions == null || positions.isEmpty()) {
            return;
        }

        for (BlockPos pos : positions) {
            Location loc = pos.toLocation(world);
            playSoundEffects(loc);
        }
    }

    /**
     * Plays sound effects at the specified location.
     *
     * @param location the location for effect playback
     */
    private void playSoundEffects(Location location) {
        if (!TreeFellerConfig.SOUNDS_ENABLED) {
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Play sound to all players near the location
        world.playSound(
                location,
                TreeFellerConfig.FELL_SOUND,
                TreeFellerConfig.SOUND_VOLUME,
                TreeFellerConfig.SOUND_PITCH
        );
    }
}
