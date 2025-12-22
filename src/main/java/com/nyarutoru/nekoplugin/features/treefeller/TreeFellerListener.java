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

import java.util.*;

/**
 * Handles Tree Feller events using ActiveToolAPI.
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "Tree Feller";

    // Tree validation constants
    private static final int MIN_LOGS_FOR_TREE = 4;
    private static final int MIN_LEAVES_FOR_TREE = 20;
    private static final int TALL_TREE_HEIGHT_THRESHOLD = 10;
    private static final int MAX_TREE_HEIGHT_SEARCH = 64;

    // Leaf search ranges
    private static final int LEAF_SEARCH_MIN_X = -3;
    private static final int LEAF_SEARCH_MAX_X = 3;
    private static final int LEAF_SEARCH_MIN_Y = -2;
    private static final int LEAF_SEARCH_MAX_Y = 4;
    private static final int LEAF_SEARCH_MIN_Z = -3;
    private static final int LEAF_SEARCH_MAX_Z = 3;

    // Leaf decay timing constants
    private static final int LEAF_DECAY_WAVE_SIZE = 10;
    private static final int LEAF_DECAY_INITIAL_DELAY = 1;
    private static final int LEAF_DECAY_WAVE_1_DELAY = 20;
    private static final int LEAF_DECAY_WAVE_2_DELAY = 40;
    private static final int LEAF_DECAY_WAVE_3_DELAY = 60;
    private static final int LEAF_DECAY_WAVE_4_DELAY = 80;
    private static final int LEAF_DECAY_WAVE_5_DELAY = 100;
    private static final int LEAF_DECAY_WAVE_6_DELAY = 120;
    private static final int MAX_DECAY_WAVE_RANGE = 3;

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
    private final NekoPlugin plugin;
    private final NamespacedKey playerPlacedKey;

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
     * Checks if this log is part of an actual tree.
     * Uses BFS to find all connected logs and leaves from any position.
     * Requires minimum logs and leaves to be considered a real tree.
     */
    private boolean isActualTree(Block startLog, Material logType) {
        final int MIN_LOGS = MIN_LOGS_FOR_TREE;
        final int MIN_LEAVES = MIN_LEAVES_FOR_TREE;

        Set<Location> visitedLogs = new HashSet<>();
        Set<Location> visitedLeaves = new HashSet<>();
        Deque<Location> toCheck = new ArrayDeque<>();

        toCheck.add(startLog.getLocation());
        visitedLogs.add(startLog.getLocation());

        // BFS to find all connected logs and count nearby leaves
        while (!toCheck.isEmpty()) {
            Location current = toCheck.poll();
            Block block = current.getBlock();

            // Search for connected logs in all directions
            int[][] logOffsets = {
                    // Vertical
                    {0, 1, 0}, {0, 2, 0}, {0, 3, 0},
                    {0, -1, 0}, {0, -2, 0},
                    // Horizontal
                    {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                    {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
                    // Diagonal
                    {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                    {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
            };

            for (int[] offset : logOffsets) {
                Location adjacent = current.clone().add(offset[0], offset[1], offset[2]);
                if (!visitedLogs.contains(adjacent)) {
                    Block adjBlock = adjacent.getBlock();
                    if (adjBlock.getType() == logType && !isPlayerPlaced(adjBlock)) {
                        visitedLogs.add(adjacent);
                        toCheck.add(adjacent);
                    }
                }
            }

            // Count leaves around this log
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Location leafLoc = current.clone().add(dx, dy, dz);
                        if (!visitedLeaves.contains(leafLoc)) {
                            Block leafBlock = leafLoc.getBlock();
                            if (isLeaf(leafBlock.getType())) {
                                visitedLeaves.add(leafLoc);
                            }
                        }
                    }
                }
            }

            // Early exit if we've found enough
            if (visitedLogs.size() >= MIN_LOGS && visitedLeaves.size() >= MIN_LEAVES) {
                return true;
            }
        }

        // Check if minimum requirements are met
        return visitedLogs.size() >= MIN_LOGS && visitedLeaves.size() >= MIN_LEAVES;
    }

    private void fellTree(Player player, Location origin, Material logType) {
        ItemStack axe = player.getInventory().getItemInMainHand();

        Set<Location> visited = new HashSet<>();
        Deque<Location> toCheck = new ArrayDeque<>();
        List<Block> logsToBreak = new ArrayList<>();
        Set<Block> leavesToDecay = new HashSet<>();

        // Measure tree height first to determine search distance
        int treeHeight = measureTreeHeight(origin, logType);
        boolean isTallTree = treeHeight > TALL_TREE_HEIGHT_THRESHOLD; // Jungle, Spruce, tall oaks

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

            // Use extended offsets for tall trees only
            int[][] offsets = isTallTree ? getTallTreeOffsets() : getCompactOffsets();

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
            for (int dx = LEAF_SEARCH_MIN_X; dx <= LEAF_SEARCH_MAX_X; dx++) {
                for (int dy = LEAF_SEARCH_MIN_Y; dy <= LEAF_SEARCH_MAX_Y; dy++) {
                    for (int dz = LEAF_SEARCH_MIN_Z; dz <= LEAF_SEARCH_MAX_Z; dz++) {
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

            // Check and consume durability
            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentAxe, 1, TOOL_NAME)) {
                break;
            }

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
     * Measures the height of a tree by counting logs upward.
     */
    private int measureTreeHeight(Location origin, Material logType) {
        int height = 0;
        Location check = origin.clone();

        for (int y = 0; y < MAX_TREE_HEIGHT_SEARCH; y++) {
            Block block = check.clone().add(0, y, 0).getBlock();
            if (block.getType() == logType && !isPlayerPlaced(block)) {
                height++;
            } else if (isLeaf(block.getType())) {
                // Reached leaves, stop counting
                break;
            } else if (block.getType() != logType) {
                break;
            }
        }

        return height;
    }

    /**
     * Compact offsets for short trees (Oak, Birch, Cherry, Acacia).
     * Max 1 block horizontal to prevent spreading.
     */
    private int[][] getCompactOffsets() {
        return new int[][]{
                // Vertical
                {0, 1, 0}, {0, 2, 0},
                {0, -1, 0},

                // Horizontal (1 block only)
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},

                // Diagonal up (+1 Y)
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 1, 1}, {-1, 1, 1}, {1, 1, -1}, {-1, 1, -1},

                // Diagonal down
                {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
        };
    }

    /**
     * Extended offsets for tall trees (Jungle, Spruce, Dark Oak).
     * Max 2 blocks horizontal for wider canopies.
     */
    private int[][] getTallTreeOffsets() {
        return new int[][]{
                // Vertical
                {0, 1, 0}, {0, 2, 0}, {0, 3, 0},
                {0, -1, 0}, {0, -2, 0},

                // Horizontal cardinal (1-2 blocks)
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2},

                // Horizontal diagonal
                {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},

                // Diagonal up (+1 Y)
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                {1, 1, 1}, {-1, 1, 1}, {1, 1, -1}, {-1, 1, -1},
                {2, 1, 0}, {-2, 1, 0}, {0, 1, 2}, {0, 1, -2},

                // Diagonal up (+2 Y)
                {1, 2, 0}, {-1, 2, 0}, {0, 2, 1}, {0, 2, -1},
                {1, 2, 1}, {-1, 2, 1}, {1, 2, -1}, {-1, 2, -1},
                {2, 2, 0}, {-2, 2, 0}, {0, 2, 2}, {0, 2, -2},

                // Diagonal down
                {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
                {1, -1, 1}, {-1, -1, 1}, {1, -1, -1}, {-1, -1, -1},
        };
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
        int waveSize = LEAF_DECAY_WAVE_SIZE;
        int tickDelay = LEAF_DECAY_INITIAL_DELAY;

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
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 1), LEAF_DECAY_WAVE_1_DELAY);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 2), LEAF_DECAY_WAVE_2_DELAY);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 3), LEAF_DECAY_WAVE_3_DELAY);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 4), LEAF_DECAY_WAVE_4_DELAY);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 5), LEAF_DECAY_WAVE_5_DELAY);
        SchedulerUtils.runSyncLater(() -> triggerDecayWave(leaves, 6), LEAF_DECAY_WAVE_6_DELAY);
    }

    /**
     * Follow-up decay wave to catch any remaining leaves.
     * Uses expanded search range for tall trees.
     */
    private void triggerDecayWave(Set<Block> originalLeaves, int wave) {
        Set<Block> remainingLeaves = new HashSet<>();

        // Check original leaves and expand search (larger range for tall trees)
        int range = Math.min(wave, MAX_DECAY_WAVE_RANGE); // Expand range with each wave
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
