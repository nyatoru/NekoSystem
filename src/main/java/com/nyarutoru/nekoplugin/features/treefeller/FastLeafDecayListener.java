package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.*;

/**
 * Applies fast leaf decay to ALL trees when logs are broken.
 * Works independently of TreeFeller activation.
 */
public class FastLeafDecayListener implements Listener {

    private static final int LEAF_SEARCH_RANGE = 3;
    private static final int LEAF_DECAY_BATCH_SIZE = 20;
    private static final int LEAF_DECAY_TICK_DELAY = 1;

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

    private static final Set<Material> LEAVES = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES,
            Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!LOGS.contains(block.getType())) {
            return;
        }

        // Find nearby leaves and trigger fast decay
        World world = block.getWorld();
        BlockPos origin = BlockPos.from(block.getLocation());
        Set<BlockPos> nearbyLeaves = findNearbyLeaves(origin, world);

        if (!nearbyLeaves.isEmpty()) {
            triggerFastLeafDecay(nearbyLeaves, world);
        }
    }

    private Set<BlockPos> findNearbyLeaves(BlockPos origin, World world) {
        Set<BlockPos> leaves = new HashSet<>();

        for (int dx = -LEAF_SEARCH_RANGE; dx <= LEAF_SEARCH_RANGE; dx++) {
            for (int dy = -LEAF_SEARCH_RANGE; dy <= LEAF_SEARCH_RANGE; dy++) {
                for (int dz = -LEAF_SEARCH_RANGE; dz <= LEAF_SEARCH_RANGE; dz++) {
                    BlockPos leafPos = origin.add(dx, dy, dz);
                    Block leafBlock = leafPos.getBlock(world);
                    if (LEAVES.contains(leafBlock.getType())) {
                        leaves.add(leafPos);
                    }
                }
            }
        }

        return leaves;
    }

    private void triggerFastLeafDecay(Set<BlockPos> leaves, World world) {
        List<BlockPos> leafList = new ArrayList<>(leaves);
        Collections.shuffle(leafList);

        for (int i = 0; i < leafList.size(); i++) {
            BlockPos leafPos = leafList.get(i);
            int batch = i / LEAF_DECAY_BATCH_SIZE;
            int delay = LEAF_DECAY_TICK_DELAY + (batch * 2);

            SchedulerUtils.runAtLocationLater(leafPos.toLocation(world), () -> {
                Block leaf = leafPos.getBlock(world);
                if (LEAVES.contains(leaf.getType())) {
                    // Set persistent flag to false
                    org.bukkit.block.data.BlockData data = leaf.getBlockData();
                    if (data instanceof org.bukkit.block.data.type.Leaves) {
                        org.bukkit.block.data.type.Leaves leafData = (org.bukkit.block.data.type.Leaves) data;
                        leafData.setPersistent(false);
                        leaf.setBlockData(leafData, false);
                    }

                    // Try vanilla decay first
                    leaf.randomTick();

                    // Guaranteed fallback
                    SchedulerUtils.runAtLocationLater(leaf.getLocation(), () -> {
                        if (LEAVES.contains(leaf.getType())) {
                            leaf.breakNaturally();
                        }
                    }, 20);
                }
            }, delay);
        }
    }
}
