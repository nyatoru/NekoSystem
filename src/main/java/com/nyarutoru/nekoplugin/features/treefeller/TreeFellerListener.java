package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
 * Optimized with BlockPos for significant performance improvements.
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "Tree Feller";

    // Tree validation constants
    private static final int MIN_LOGS_FOR_TREE = 4;
    private static final int MIN_LEAVES_FOR_TREE = 20;

    // Leaf search ranges
    private static final int LEAF_SEARCH_MIN_X = -3;
    private static final int LEAF_SEARCH_MAX_X = 3;
    private static final int LEAF_SEARCH_MIN_Y = -2;
    private static final int LEAF_SEARCH_MAX_Y = 4;
    private static final int LEAF_SEARCH_MIN_Z = -3;
    private static final int LEAF_SEARCH_MAX_Z = 3;

    // Leaf decay timing
    private static final int LEAF_DECAY_BATCH_SIZE = 20;
    private static final int LEAF_DECAY_TICK_DELAY = 1;

    // Static offset arrays for log searching
    private static final int[][] COMPACT_OFFSETS = {
            // Vertical
            { 0, 1, 0 }, { 0, 2, 0 },
            { 0, -1, 0 },
            // Horizontal (1 block only)
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal up (+1 Y)
            { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
            { 1, 1, 1 }, { -1, 1, 1 }, { 1, 1, -1 }, { -1, 1, -1 },
            // Diagonal down
            { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 }
    };

    private static final int[][] TALL_TREE_OFFSETS = {
            // Vertical
            { 0, 1, 0 }, { 0, 2, 0 }, { 0, 3, 0 },
            { 0, -1, 0 }, { 0, -2, 0 },
            // Horizontal cardinal (1-2 blocks)
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 2, 0, 0 }, { -2, 0, 0 }, { 0, 0, 2 }, { 0, 0, -2 },
            // Horizontal diagonal
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal up (+1 Y)
            { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
            { 1, 1, 1 }, { -1, 1, 1 }, { 1, 1, -1 }, { -1, 1, -1 },
            { 2, 1, 0 }, { -2, 1, 0 }, { 0, 1, 2 }, { 0, 1, -2 },
            // Diagonal up (+2 Y)
            { 1, 2, 0 }, { -1, 2, 0 }, { 0, 2, 1 }, { 0, 2, -1 },
            { 1, 2, 1 }, { -1, 2, 1 }, { 1, 2, -1 }, { -1, 2, -1 },
            { 2, 2, 0 }, { -2, 2, 0 }, { 0, 2, 2 }, { 0, 2, -2 },
            // Diagonal down
            { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 },
            { 1, -1, 1 }, { -1, -1, 1 }, { 1, -1, -1 }, { -1, -1, -1 }
    };

    private static final int[][] VALIDATION_LOG_OFFSETS = {
            // Vertical
            { 0, 1, 0 }, { 0, 2, 0 }, { 0, 3, 0 },
            { 0, -1, 0 }, { 0, -2, 0 },
            // Horizontal
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal
            { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
            { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 }
    };

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

        if (isPlayerPlaced(block)) {
            return;
        }

        if (!isActualTree(block, logType)) {
            return;
        }

        fellTree(player, block, logType);
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

    private void removePlayerPlacedMark(BlockPos pos, World world) {
        Block block = pos.getBlock(world);
        block.getChunk().getPersistentDataContainer().remove(getBlockKey(block.getLocation()));
    }

    /**
     * Checks if this log is part of an actual tree using optimized BlockPos BFS.
     */
    private boolean isActualTree(Block startLog, Material logType) {
        World world = startLog.getWorld();
        BlockPos startPos = BlockPos.from(startLog.getLocation());

        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> visitedLeaves = new HashSet<>();
        Deque<BlockPos> toCheck = new ArrayDeque<>();

        toCheck.add(startPos);
        visitedLogs.add(startPos);

        // BFS to find all connected logs and count nearby leaves
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            Block block = current.getBlock(world);

            // Search for connected logs
            for (int[] offset : VALIDATION_LOG_OFFSETS) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visitedLogs.contains(adjacent)) {
                    Block adjBlock = adjacent.getBlock(world);
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
                        BlockPos leafPos = current.add(dx, dy, dz);
                        if (!visitedLeaves.contains(leafPos)) {
                            if (isLeaf(leafPos.getBlock(world).getType())) {
                                visitedLeaves.add(leafPos);
                            }
                        }
                    }
                }
            }

            // Early exit if we've found enough
            if (visitedLogs.size() >= MIN_LOGS_FOR_TREE && visitedLeaves.size() >= MIN_LEAVES_FOR_TREE) {
                return true;
            }
        }

        return visitedLogs.size() >= MIN_LOGS_FOR_TREE && visitedLeaves.size() >= MIN_LEAVES_FOR_TREE;
    }

    private void fellTree(Player player, Block originBlock, Material logType) {
        ItemStack axe = player.getInventory().getItemInMainHand();
        World world = originBlock.getWorld();
        BlockPos origin = BlockPos.from(originBlock.getLocation());

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> toCheck = new ArrayDeque<>();
        List<BlockPos> logsToBreak = new ArrayList<>();
        Set<BlockPos> leavesToDecay = new HashSet<>();

        // Determine tree height and select appropriate offsets
        int treeHeight = measureTreeHeightDuringBFS(origin, logType, world);
        boolean isTallTree = treeHeight > 10;
        int[][] offsets = isTallTree ? TALL_TREE_OFFSETS : COMPACT_OFFSETS;

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find all connected logs
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            Block block = current.getBlock(world);

            if (block.getType() != logType)
                continue;
            if (isPlayerPlaced(block))
                continue;

            logsToBreak.add(current);

            for (int[] offset : offsets) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Block adjBlock = adjacent.getBlock(world);
                    if (adjBlock.getType() == logType && !isPlayerPlaced(adjBlock)) {
                        toCheck.add(adjacent);
                    }
                }
            }

            // Collect nearby leaves
            for (int dx = LEAF_SEARCH_MIN_X; dx <= LEAF_SEARCH_MAX_X; dx++) {
                for (int dy = LEAF_SEARCH_MIN_Y; dy <= LEAF_SEARCH_MAX_Y; dy++) {
                    for (int dz = LEAF_SEARCH_MIN_Z; dz <= LEAF_SEARCH_MAX_Z; dz++) {
                        BlockPos leafPos = current.add(dx, dy, dz);
                        if (isLeaf(leafPos.getBlock(world).getType())) {
                            leavesToDecay.add(leafPos);
                        }
                    }
                }
            }
        }

        // Break logs (skip origin as it's broken by the event)
        int broken = 0;
        for (BlockPos logPos : logsToBreak) {
            if (logPos.equals(origin))
                continue;

            ItemStack currentAxe = player.getInventory().getItemInMainHand();
            if (currentAxe.getType() != axe.getType())
                break;

            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentAxe, 1, TOOL_NAME)) {
                break;
            }

            Block log = logPos.getBlock(world);
            for (ItemStack drop : log.getDrops(currentAxe)) {
                world.dropItemNaturally(origin.toLocation(world), drop);
            }

            removePlayerPlacedMark(logPos, world);
            log.setType(Material.AIR);
            broken++;
        }

        // Simplified leaf decay - single pass with batched scheduling
        if (!leavesToDecay.isEmpty()) {
            triggerSimplifiedLeafDecay(leavesToDecay, world);
        }
    }

    /**
     * Measures tree height during initial BFS (no separate pass needed).
     */
    private int measureTreeHeightDuringBFS(BlockPos origin, Material logType, World world) {
        int height = 0;
        for (int y = 0; y < 64; y++) {
            BlockPos check = origin.add(0, y, 0);
            Block block = check.getBlock(world);
            if (block.getType() == logType && !isPlayerPlaced(block)) {
                height++;
            } else if (isLeaf(block.getType())) {
                break;
            } else if (block.getType() != logType) {
                break;
            }
        }
        return height;
    }

    /**
     * Simplified leaf decay - single-pass batch processing instead of 6 waves.
     * Reduces scheduled tasks from hundreds to tens.
     */
    private void triggerSimplifiedLeafDecay(Set<BlockPos> leaves, World world) {
        List<BlockPos> leafList = new ArrayList<>(leaves);
        Collections.shuffle(leafList);

        // Process leaves in small batches with minimal delay
        for (int i = 0; i < leafList.size(); i++) {
            BlockPos leafPos = leafList.get(i);
            int batch = i / LEAF_DECAY_BATCH_SIZE;
            int delay = LEAF_DECAY_TICK_DELAY + batch;

            SchedulerUtils.runAtLocationLater(leafPos.toLocation(world), () -> {
                Block leaf = leafPos.getBlock(world);
                if (isLeaf(leaf.getType())) {
                    leaf.randomTick();
                }
            }, delay);
        }
    }
}
