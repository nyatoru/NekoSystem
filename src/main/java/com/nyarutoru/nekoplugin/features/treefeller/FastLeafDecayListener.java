package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies fast leaf decay to ALL trees when logs are broken.
 * Uses recursive decay checking with proper vanilla mechanics.
 */
public class FastLeafDecayListener implements Listener {

    private static final List<BlockFace> FACES = Arrays.asList(
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);

    private static final Set<Block> SCHEDULED = new HashSet<>();

    private static final Set<Material> LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!LOGS.contains(block.getType())) {
            return;
        }

        // Trigger recursive decay check for all adjacent blocks
        for (BlockFace face : FACES) {
            Block adjacent = block.getRelative(face);
            doDecay(adjacent);
        }
    }

    public void doDecay(final Block block) {
        Collections.shuffle(FACES);

        for (final BlockFace face : FACES) {
            final Block b = block.getRelative(face);
            if (SCHEDULED.contains(b))
                continue;
            if (!(b.getBlockData() instanceof final Leaves leaves) || leaves.isPersistent()
                    || leaves.getDistance() < 7) {
                continue;
            }
            SCHEDULED.add(b);
            SchedulerUtils.runAtLocationLater(b.getLocation(), () -> {
                final LeavesDecayEvent decayEvent = new LeavesDecayEvent(b);
                Bukkit.getPluginManager().callEvent(decayEvent);
                if (decayEvent.isCancelled())
                    return;
                b.breakNaturally();
                SCHEDULED.remove(b);
            }, ThreadLocalRandom.current().nextLong(2, 10));
        }
    }
}
