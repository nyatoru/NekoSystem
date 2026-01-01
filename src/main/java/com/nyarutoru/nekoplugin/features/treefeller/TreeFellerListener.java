package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
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
import java.util.EnumSet;

/**
 * Handles Tree Feller events using ActiveToolAPI.
 * Features advanced structure detection to prevent felling player builds.
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "Tree Feller";

    // Tree validation constants
    private static final int MIN_LOGS_FOR_TREE = 4;
    private static final int MIN_LEAVES_FOR_TREE = 20;
    private static final int STRUCTURE_CHECK_RADIUS = 2;
    private static final int MAX_STRUCTURE_BLOCKS_ALLOWED = 2;

    // Static offset arrays for log searching (includes multi-trunk support)
    private static final int[][] COMPACT_OFFSETS = {
            // Vertical
            { 0, 1, 0 }, { 0, 2, 0 },
            { 0, -1, 0 },
            // Horizontal (1 block - for multi-trunk trees)
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal up (+1 Y)
            { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
            { 1, 1, 1 }, { -1, 1, 1 }, { 1, 1, -1 }, { -1, 1, -1 },
            // Diagonal down (for forking trees)
            { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 },
            { 1, -1, 1 }, { -1, -1, 1 }, { 1, -1, -1 }, { -1, -1, -1 }
    };

    private static final int[][] TALL_TREE_OFFSETS = {
            // Vertical (extended range)
            { 0, 1, 0 }, { 0, 2, 0 }, { 0, 3, 0 },
            { 0, -1, 0 }, { 0, -2, 0 },
            // Horizontal cardinal (1-2 blocks for 2x2 trees)
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 2, 0, 0 }, { -2, 0, 0 }, { 0, 0, 2 }, { 0, 0, -2 },
            // Horizontal diagonal (for multi-trunk)
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal up (+1 Y)
            { 1, 1, 0 }, { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 },
            { 1, 1, 1 }, { -1, 1, 1 }, { 1, 1, -1 }, { -1, 1, -1 },
            { 2, 1, 0 }, { -2, 1, 0 }, { 0, 1, 2 }, { 0, 1, -2 },
            // Diagonal up (+2 Y)
            { 1, 2, 0 }, { -1, 2, 0 }, { 0, 2, 1 }, { 0, 2, -1 },
            { 1, 2, 1 }, { -1, 2, 1 }, { 1, 2, -1 }, { -1, 2, -1 },
            { 2, 2, 0 }, { -2, 2, 0 }, { 0, 2, 2 }, { 0, 2, -2 },
            // Diagonal down (for forking)
            { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 },
            { 1, -1, 1 }, { -1, -1, 1 }, { 1, -1, -1 }, { -1, -1, -1 }
    };

    private static final int[][] VALIDATION_LOG_OFFSETS = {
            // Vertical
            { 0, 1, 0 }, { 0, 2, 0 }, { 0, 3, 0 },
            { 0, -1, 0 }, { 0, -2, 0 },
            // Horizontal (multi-trunk support)
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 1, 0, 1 }, { 1, 0, -1 }, { -1, 0, 1 }, { -1, 0, -1 },
            // Diagonal vertical
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

    // Mangrove roots (for mangrove tree handling)
    private static final Set<Material> MANGROVE_ROOTS = Set.of(
            Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS);

    // Structure blocks - indicates player-built structures (not natural trees)
    // Using EnumSet for O(1) lookups
    private static final Set<Material> STRUCTURE_BLOCKS = EnumSet.noneOf(Material.class);

    static {
        // Fences and gates
        for (Material m : Material.values()) {
            String name = m.name();
            if (name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Doors and trapdoors
            if (name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Stairs and slabs
            if (name.endsWith("_STAIRS") || name.endsWith("_SLAB")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Signs
            if (name.contains("SIGN")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Walls
            if (name.endsWith("_WALL")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Pressure plates
            if (name.endsWith("_PRESSURE_PLATE")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Buttons
            if (name.endsWith("_BUTTON")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Carpets
            if (name.endsWith("_CARPET")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Beds
            if (name.endsWith("_BED")) {
                STRUCTURE_BLOCKS.add(m);
            }
            // Banners
            if (name.contains("BANNER")) {
                STRUCTURE_BLOCKS.add(m);
            }
        }
        // Additional structure indicators
        STRUCTURE_BLOCKS.add(Material.CHEST);
        STRUCTURE_BLOCKS.add(Material.TRAPPED_CHEST);
        STRUCTURE_BLOCKS.add(Material.BARREL);
        STRUCTURE_BLOCKS.add(Material.FURNACE);
        STRUCTURE_BLOCKS.add(Material.BLAST_FURNACE);
        STRUCTURE_BLOCKS.add(Material.SMOKER);
        STRUCTURE_BLOCKS.add(Material.CRAFTING_TABLE);
        STRUCTURE_BLOCKS.add(Material.CARTOGRAPHY_TABLE);
        STRUCTURE_BLOCKS.add(Material.SMITHING_TABLE);
        STRUCTURE_BLOCKS.add(Material.FLETCHING_TABLE);
        STRUCTURE_BLOCKS.add(Material.LECTERN);
        STRUCTURE_BLOCKS.add(Material.ENCHANTING_TABLE);
        STRUCTURE_BLOCKS.add(Material.ANVIL);
        STRUCTURE_BLOCKS.add(Material.CHIPPED_ANVIL);
        STRUCTURE_BLOCKS.add(Material.DAMAGED_ANVIL);
        STRUCTURE_BLOCKS.add(Material.BREWING_STAND);
        STRUCTURE_BLOCKS.add(Material.LADDER);
        STRUCTURE_BLOCKS.add(Material.TORCH);
        STRUCTURE_BLOCKS.add(Material.WALL_TORCH);
        STRUCTURE_BLOCKS.add(Material.LANTERN);
        STRUCTURE_BLOCKS.add(Material.SOUL_LANTERN);
        STRUCTURE_BLOCKS.add(Material.ITEM_FRAME);
        STRUCTURE_BLOCKS.add(Material.GLOW_ITEM_FRAME);
        STRUCTURE_BLOCKS.add(Material.PAINTING);
        STRUCTURE_BLOCKS.add(Material.FLOWER_POT);
        STRUCTURE_BLOCKS.add(Material.ARMOR_STAND);
        STRUCTURE_BLOCKS.add(Material.BOOKSHELF);
        STRUCTURE_BLOCKS.add(Material.GLASS);
        STRUCTURE_BLOCKS.add(Material.GLASS_PANE);
    }

    private final NekoPlugin plugin;

    public TreeFellerListener(NekoPlugin plugin) {
        this.plugin = plugin;
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

        // Advanced structure detection
        if (isPartOfStructure(block)) {
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

    private boolean isMangroveRoot(Material material) {
        return MANGROVE_ROOTS.contains(material);
    }

    private boolean isStructureBlock(Material material) {
        return STRUCTURE_BLOCKS.contains(material);
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
     * Advanced structure detection - checks if log is part of a player-built
     * structure.
     * Looks for structural elements like fences, stairs, slabs, doors, etc. nearby.
     */
    private boolean isPartOfStructure(Block startLog) {
        World world = startLog.getWorld();
        BlockPos startPos = BlockPos.from(startLog.getLocation());
        int structureBlockCount = 0;

        // Check surrounding area for structure blocks
        for (int dx = -STRUCTURE_CHECK_RADIUS; dx <= STRUCTURE_CHECK_RADIUS; dx++) {
            for (int dy = -STRUCTURE_CHECK_RADIUS; dy <= STRUCTURE_CHECK_RADIUS; dy++) {
                for (int dz = -STRUCTURE_CHECK_RADIUS; dz <= STRUCTURE_CHECK_RADIUS; dz++) {
                    BlockPos checkPos = startPos.add(dx, dy, dz);
                    Block checkBlock = checkPos.getBlock(world);

                    if (isStructureBlock(checkBlock.getType())) {
                        structureBlockCount++;
                        if (structureBlockCount > MAX_STRUCTURE_BLOCKS_ALLOWED) {
                            return true; // Too many structure blocks nearby - likely a building
                        }
                    }
                }
            }
        }

        // Also check if log is directly adjacent to multiple planks (floor/wall
        // indicator)
        int adjacentPlanks = 0;
        int[][] directNeighbors = {
                { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }
        };
        for (int[] offset : directNeighbors) {
            BlockPos neighbor = startPos.add(offset[0], offset[1], offset[2]);
            Material mat = neighbor.getBlock(world).getType();
            if (mat.name().endsWith("_PLANKS")) {
                adjacentPlanks++;
            }
        }

        // If 3+ sides have planks, this is likely part of a structure
        return adjacentPlanks >= 3;
    }

    /**
     * Checks if this log is part of an actual tree using optimized BlockPos BFS.
     * Includes multi-trunk detection for forked and branching trees.
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

            // Search for connected logs (including horizontal for multi-trunk)
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
        Set<BlockPos> rootsToBreak = new HashSet<>();

        // Determine tree height and select appropriate offsets
        int treeHeight = measureTreeHeightDuringBFS(origin, logType, world);
        boolean isTallTree = treeHeight > 10;
        boolean isMangrove = logType == Material.MANGROVE_LOG || logType == Material.STRIPPED_MANGROVE_LOG;
        int[][] offsets = isTallTree ? TALL_TREE_OFFSETS : COMPACT_OFFSETS;

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find all connected logs
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            Block block = current.getBlock(world);

            Material currentType = block.getType();

            // Handle logs
            if (currentType == logType && !isPlayerPlaced(block)) {
                logsToBreak.add(current);
            }

            for (int[] offset : offsets) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Block adjBlock = adjacent.getBlock(world);
                    Material adjType = adjBlock.getType();

                    if (adjType == logType && !isPlayerPlaced(adjBlock)) {
                        toCheck.add(adjacent);
                    }

                    // Mangrove roots handling
                    if (isMangrove && isMangroveRoot(adjType)) {
                        rootsToBreak.add(adjacent);
                        // Also check for logs connected through roots
                        toCheck.add(adjacent);
                    }
                }
            }

            // For mangrove trees, also search for roots below
            if (isMangrove) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -3; dy <= 0; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos rootPos = current.add(dx, dy, dz);
                            if (!visited.contains(rootPos) && isMangroveRoot(rootPos.getBlock(world).getType())) {
                                rootsToBreak.add(rootPos);
                                visited.add(rootPos);
                            }
                        }
                    }
                }
            }
        }

        // Break logs (skip origin as it's broken by the event)
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
        }

        // Break mangrove roots
        for (BlockPos rootPos : rootsToBreak) {
            ItemStack currentAxe = player.getInventory().getItemInMainHand();
            if (currentAxe.getType() != axe.getType())
                break;

            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentAxe, 1, TOOL_NAME)) {
                break;
            }

            Block root = rootPos.getBlock(world);
            for (ItemStack drop : root.getDrops(currentAxe)) {
                world.dropItemNaturally(origin.toLocation(world), drop);
            }
            root.setType(Material.AIR);
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
}
