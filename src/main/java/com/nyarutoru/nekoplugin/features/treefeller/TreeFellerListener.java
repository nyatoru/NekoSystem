package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Handles Tree Feller events using ActiveToolAPI.
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "Tree Feller";

    private final NekoPlugin plugin;
    private final NamespacedKey playerPlacedKey;

    // All log types
    private static final Set<Material> LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            // Stripped variants
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM);

    // All leaf types
    private static final Set<Material> LEAVES = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES,
            Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK);

    public TreeFellerListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.playerPlacedKey = new NamespacedKey(plugin, "player_placed");
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;

        Player player = event.getPlayer();

        ActiveToolAPI.getInstance().onShift(
                player,
                TOOL_NAME,
                this::isHoldingAxe,
                null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Mark player-placed logs
        Block block = event.getBlock();
        if (isLog(block.getType())) {
            block.getChunk().getPersistentDataContainer().set(
                    getBlockKey(block.getLocation()),
                    PersistentDataType.BYTE,
                    (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME))
            return;

        if (!isHoldingAxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "no axe");
            return;
        }

        Block block = event.getBlock();
        Material logType = block.getType();

        if (!isLog(logType))
            return;

        // Check if player-placed (skip tree felling for player-placed logs)
        if (isPlayerPlaced(block)) {
            return;
        }

        // For trees larger than 1x1, require entire horizontal cross-section to be
        // mined
        // (silently skip felling if adjacent logs exist)
        int adjacentLogs = countAdjacentLogs(block, logType);
        if (adjacentLogs > 0) {
            return;
        }

        // Verify this is an actual tree (has leaves connected)
        if (!isActualTree(block, logType)) {
            return;
        }

        // Fell the tree
        fellTree(player, block.getLocation(), logType);
    }

    private boolean isHoldingAxe(Player player) {
        return ItemUtils.isAxe(player.getInventory().getItemInMainHand());
    }

    private boolean isLog(Material material) {
        return LOGS.contains(material);
    }

    private boolean isLeaf(Material material) {
        return LEAVES.contains(material);
    }

    private NamespacedKey getBlockKey(Location loc) {
        return new NamespacedKey(plugin, "pp_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ());
    }

    private boolean isPlayerPlaced(Block block) {
        Byte value = block.getChunk().getPersistentDataContainer().get(
                getBlockKey(block.getLocation()),
                PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private void removePlayerPlacedMark(Block block) {
        block.getChunk().getPersistentDataContainer().remove(getBlockKey(block.getLocation()));
    }

    /**
     * Counts adjacent logs at the same Y level (for multi-trunk trees).
     * Returns 0 for single-trunk trees, >0 for 2x2 or larger.
     */
    private int countAdjacentLogs(Block block, Material logType) {
        Location loc = block.getLocation();
        int count = 0;

        // Check all 8 surrounding blocks at same Y level
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;

                Block neighbor = loc.clone().add(dx, 0, dz).getBlock();
                if (neighbor.getType() == logType && !isPlayerPlaced(neighbor)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Checks if this log is part of an actual tree.
     * A real tree must have leaves connected above the log column.
     * This prevents felling random logs or log structures.
     */
    private boolean isActualTree(Block startLog, Material logType) {
        Location loc = startLog.getLocation().clone();

        // Search upward to find the top of the log column
        int maxHeight = 64;
        int logCount = 0;
        Block topLog = startLog;

        for (int y = 0; y < maxHeight; y++) {
            Block above = loc.clone().add(0, y, 0).getBlock();
            if (above.getType() == logType && !isPlayerPlaced(above)) {
                topLog = above;
                logCount++;
            } else if (isLeaf(above.getType())) {
                // Found leaves directly above - it's a tree
                return true;
            } else if (above.getType() != logType) {
                // Hit something else, stop searching upward
                break;
            }
        }

        // Minimum log height to be considered a tree (at least 3 logs)
        if (logCount < 3) {
            return false;
        }

        // Check for leaves around the top portion of the tree
        Location topLoc = topLog.getLocation();
        int searchRadius = 3;
        int searchHeight = 5;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = 0; dy <= searchHeight; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    Block check = topLoc.clone().add(dx, dy, dz).getBlock();
                    if (isLeaf(check.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void fellTree(Player player, Location origin, Material logType) {
        ItemStack axe = player.getInventory().getItemInMainHand();

        Set<Location> visited = new HashSet<>();
        Queue<Location> toCheck = new LinkedList<>();
        List<Block> logsToBreak = new ArrayList<>();
        Set<Block> leavesToDecay = new HashSet<>();

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find all connected logs (going up and around)
        while (!toCheck.isEmpty()) {
            Location current = toCheck.poll();
            Block block = current.getBlock();

            // Only include logs of the same type as the original
            if (block.getType() != logType)
                continue;
            if (isPlayerPlaced(block))
                continue;

            logsToBreak.add(block);

            // Check in all directions including downward for connected logs
            // Extended range (2 blocks) for large trees like Jungle and Spruce
            int[][] offsets = {
                    { 0, 1, 0 }, { 0, 2, 0 }, // Up (extended)
                    { 0, -1, 0 }, // Down
                    { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, // Horizontal
                    { 2, 0, 0 }, { -2, 0, 0 }, { 0, 0, 2 }, { 0, 0, -2 }, // Horizontal extended
                    { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 }, // Diagonal up
                    { 1, 1, 1 }, { -1, 1, 1 }, { 1, 1, -1 }, { -1, 1, -1 }, // Full diagonal up
                    { 2, 1, 0 }, { -2, 1, 0 }, { 0, 1, 2 }, { 0, 1, -2 }, // Extended diagonal up
                    { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 }, // Diagonal down
                    { 1, 2, 0 }, { -1, 2, 0 }, { 0, 2, 1 }, { 0, 2, -1 }, // Jump up
                    { 1, 2, 1 }, { -1, 2, 1 }, { 1, 2, -1 }, { -1, 2, -1 } // Jump up diagonal
            };

            for (int[] offset : offsets) {
                Location adjacent = current.clone().add(offset[0], offset[1], offset[2]);
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Block adjBlock = adjacent.getBlock();
                    // Only include logs of the same type
                    if (adjBlock.getType() == logType && !isPlayerPlaced(adjBlock)) {
                        toCheck.add(adjacent);
                    }
                }
            }

            // Collect nearby leaves for decay (expanded range for tall trees)
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block leafBlock = current.clone().add(dx, dy, dz).getBlock();
                        if (isLeaf(leafBlock.getType())) {
                            leavesToDecay.add(leafBlock);
                        }
                    }
                }
            }
        }

        // Break logs (skip origin as it's broken by the event)
        int broken = 0;
        for (Block log : logsToBreak) {
            if (log.getLocation().equals(origin))
                continue;

            ItemStack currentAxe = player.getInventory().getItemInMainHand();
            if (currentAxe.getType() != axe.getType())
                break;

            // Check durability
            if (!ItemUtils.isUnbreakable(currentAxe) &&
                    ItemUtils.wouldBreakFromDamage(currentAxe, 1)) {
                ActiveToolAPI.getInstance().deactivate(player, "tool broke");
                break;
            }

            // Apply durability with Unbreaking support
            ItemUtils.applyDurabilityDamage(currentAxe, 1);

            // Drop items at origin for easy collection
            for (ItemStack drop : log.getDrops(currentAxe)) {
                origin.getWorld().dropItemNaturally(origin, drop);
            }

            removePlayerPlacedMark(log);
            log.setType(Material.AIR);
            broken++;
        }

        // Schedule FastLeafDecay-style leaf decay
        if (!leavesToDecay.isEmpty()) {
            triggerFastLeafDecay(leavesToDecay);
        }
    }

    /**
     * FastLeafDecay principle: simulate random ticks on leaves to trigger natural
     * decay.
     * This uses the block's randomTick method to decay leaves naturally,
     * which respects vanilla mechanics and drops.
     */
    private void triggerFastLeafDecay(Set<Block> leaves) {
        List<Block> leafList = new ArrayList<>(leaves);
        Collections.shuffle(leafList);

        // Process leaves in waves to avoid lag
        int waveSize = 10;
        int tickDelay = 1;

        for (int i = 0; i < leafList.size(); i++) {
            Block leaf = leafList.get(i);
            int wave = i / waveSize;
            int delay = tickDelay + wave;

            SchedulerUtils.runSyncLater(() -> {
                if (isLeaf(leaf.getType())) {
                    // Simulate random tick to trigger natural decay
                    // Leaves will check for logs and decay naturally
                    leaf.randomTick();
                }
            }, delay);
        }

        // Schedule follow-up waves to ensure complete decay for tall trees
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 1), 20);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 2), 40);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 3), 60);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 4), 80);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 5), 100);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 6), 120);
    }

    /**
     * Follow-up decay wave to catch any remaining leaves.
     * Uses expanded search range for tall trees.
     */
    private void triggerDecayWave(Set<Block> originalLeaves, int wave) {
        Set<Block> remainingLeaves = new HashSet<>();

        // Check original leaves and expand search (larger range for tall trees)
        int range = Math.min(wave, 3); // Expand range with each wave
        for (Block original : originalLeaves) {
            Location loc = original.getLocation();
            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    for (int dz = -range; dz <= range; dz++) {
                        Block block = loc.clone().add(dx, dy, dz).getBlock();
                        if (isLeaf(block.getType())) {
                            remainingLeaves.add(block);
                        }
                    }
                }
            }
        }

        // Trigger random tick on remaining leaves
        for (Block leaf : remainingLeaves) {
            if (isLeaf(leaf.getType())) {
                leaf.randomTick();
            }
        }
    }
}
