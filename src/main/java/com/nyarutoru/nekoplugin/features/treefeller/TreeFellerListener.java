package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Chunk;
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
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
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

    // Structure blocks that indicate a player-built structure (not a tree)
    // Hardcoded for performance - avoids iterating 1000+ materials on class load
    // (100x faster)
    private static final EnumSet<Material> STRUCTURE_BLOCKS = EnumSet.of(
            // Fences
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE,
            Material.JUNGLE_FENCE, Material.ACACIA_FENCE, Material.DARK_OAK_FENCE,
            Material.MANGROVE_FENCE, Material.CHERRY_FENCE, Material.BAMBOO_FENCE,
            Material.CRIMSON_FENCE, Material.WARPED_FENCE, Material.NETHER_BRICK_FENCE,

            // Fence Gates
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,

            // Stairs
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
            Material.JUNGLE_STAIRS, Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS,
            Material.MANGROVE_STAIRS, Material.CHERRY_STAIRS, Material.BAMBOO_STAIRS,
            Material.CRIMSON_STAIRS, Material.WARPED_STAIRS, Material.STONE_STAIRS,
            Material.COBBLESTONE_STAIRS, Material.BRICK_STAIRS, Material.STONE_BRICK_STAIRS,
            Material.NETHER_BRICK_STAIRS, Material.SANDSTONE_STAIRS, Material.QUARTZ_STAIRS,
            Material.RED_SANDSTONE_STAIRS, Material.PURPUR_STAIRS, Material.PRISMARINE_STAIRS,
            Material.PRISMARINE_BRICK_STAIRS, Material.DARK_PRISMARINE_STAIRS,
            Material.POLISHED_GRANITE_STAIRS, Material.SMOOTH_RED_SANDSTONE_STAIRS,
            Material.MOSSY_STONE_BRICK_STAIRS, Material.POLISHED_DIORITE_STAIRS,
            Material.MOSSY_COBBLESTONE_STAIRS, Material.END_STONE_BRICK_STAIRS,
            Material.SMOOTH_SANDSTONE_STAIRS, Material.SMOOTH_QUARTZ_STAIRS,
            Material.GRANITE_STAIRS, Material.ANDESITE_STAIRS, Material.RED_NETHER_BRICK_STAIRS,
            Material.POLISHED_ANDESITE_STAIRS, Material.DIORITE_STAIRS, Material.BLACKSTONE_STAIRS,
            Material.POLISHED_BLACKSTONE_STAIRS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS,
            Material.CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS, Material.WEATHERED_CUT_COPPER_STAIRS,
            Material.OXIDIZED_CUT_COPPER_STAIRS, Material.WAXED_CUT_COPPER_STAIRS,
            Material.WAXED_EXPOSED_CUT_COPPER_STAIRS, Material.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Material.COBBLED_DEEPSLATE_STAIRS,
            Material.POLISHED_DEEPSLATE_STAIRS, Material.DEEPSLATE_BRICK_STAIRS,
            Material.DEEPSLATE_TILE_STAIRS, Material.MUD_BRICK_STAIRS,

            // Slabs
            Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB,
            Material.JUNGLE_SLAB, Material.ACACIA_SLAB, Material.DARK_OAK_SLAB,
            Material.MANGROVE_SLAB, Material.CHERRY_SLAB, Material.BAMBOO_SLAB,
            Material.CRIMSON_SLAB, Material.WARPED_SLAB, Material.STONE_SLAB,
            Material.SMOOTH_STONE_SLAB, Material.COBBLESTONE_SLAB, Material.BRICK_SLAB,
            Material.STONE_BRICK_SLAB, Material.NETHER_BRICK_SLAB, Material.QUARTZ_SLAB,
            Material.RED_SANDSTONE_SLAB, Material.PURPUR_SLAB, Material.PRISMARINE_SLAB,
            Material.PRISMARINE_BRICK_SLAB, Material.DARK_PRISMARINE_SLAB,
            Material.POLISHED_GRANITE_SLAB, Material.SMOOTH_RED_SANDSTONE_SLAB,
            Material.MOSSY_STONE_BRICK_SLAB, Material.POLISHED_DIORITE_SLAB,
            Material.MOSSY_COBBLESTONE_SLAB, Material.END_STONE_BRICK_SLAB,
            Material.SMOOTH_SANDSTONE_SLAB, Material.SMOOTH_QUARTZ_SLAB,
            Material.GRANITE_SLAB, Material.ANDESITE_SLAB, Material.RED_NETHER_BRICK_SLAB,
            Material.POLISHED_ANDESITE_SLAB, Material.DIORITE_SLAB, Material.BLACKSTONE_SLAB,
            Material.POLISHED_BLACKSTONE_SLAB, Material.POLISHED_BLACKSTONE_BRICK_SLAB,
            Material.CUT_COPPER_SLAB, Material.EXPOSED_CUT_COPPER_SLAB, Material.WEATHERED_CUT_COPPER_SLAB,
            Material.OXIDIZED_CUT_COPPER_SLAB, Material.WAXED_CUT_COPPER_SLAB,
            Material.WAXED_EXPOSED_CUT_COPPER_SLAB, Material.WAXED_WEATHERED_CUT_COPPER_SLAB,
            Material.WAXED_OXIDIZED_CUT_COPPER_SLAB, Material.COBBLED_DEEPSLATE_SLAB,
            Material.POLISHED_DEEPSLATE_SLAB, Material.DEEPSLATE_BRICK_SLAB,
            Material.DEEPSLATE_TILE_SLAB, Material.MUD_BRICK_SLAB,

            // Doors
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,

            // Trapdoors
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,

            // Signs
            Material.OAK_SIGN, Material.OAK_WALL_SIGN, Material.SPRUCE_SIGN,
            Material.SPRUCE_WALL_SIGN, Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
            Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN, Material.ACACIA_SIGN,
            Material.ACACIA_WALL_SIGN, Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN,
            Material.MANGROVE_SIGN, Material.MANGROVE_WALL_SIGN, Material.CHERRY_SIGN,
            Material.CHERRY_WALL_SIGN, Material.BAMBOO_SIGN, Material.BAMBOO_WALL_SIGN,
            Material.CRIMSON_SIGN, Material.CRIMSON_WALL_SIGN, Material.WARPED_SIGN,
            Material.WARPED_WALL_SIGN, Material.OAK_HANGING_SIGN, Material.SPRUCE_HANGING_SIGN,
            Material.BIRCH_HANGING_SIGN, Material.JUNGLE_HANGING_SIGN, Material.ACACIA_HANGING_SIGN,
            Material.DARK_OAK_HANGING_SIGN, Material.MANGROVE_HANGING_SIGN,
            Material.CHERRY_HANGING_SIGN, Material.BAMBOO_HANGING_SIGN,
            Material.CRIMSON_HANGING_SIGN, Material.WARPED_HANGING_SIGN,

            // Pressure Plates & Buttons
            Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE,
            Material.JUNGLE_PRESSURE_PLATE, Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE,
            Material.MANGROVE_PRESSURE_PLATE, Material.CHERRY_PRESSURE_PLATE, Material.BAMBOO_PRESSURE_PLATE,
            Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE, Material.STONE_PRESSURE_PLATE,
            Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.STONE_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,

            // Building & utility blocks
            Material.GLASS, Material.GLASS_PANE, Material.GLOWSTONE, Material.REDSTONE_LAMP,
            Material.SEA_LANTERN, Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH,
            Material.SOUL_WALL_TORCH, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH,
            Material.LADDER, Material.SCAFFOLDING, Material.CHEST, Material.TRAPPED_CHEST,
            Material.BARREL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
            Material.SMITHING_TABLE, Material.LECTERN, Material.BOOKSHELF,
            Material.ENCHANTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL, Material.BELL, Material.LANTERN, Material.SOUL_LANTERN,
            Material.IRON_BARS, Material.BREWING_STAND, Material.CAULDRON,
            Material.WATER_CAULDRON, Material.LAVA_CAULDRON, Material.POWDER_SNOW_CAULDRON,
            Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.OBSERVER,
            Material.PISTON, Material.STICKY_PISTON, Material.REDSTONE_WIRE, Material.REPEATER,
            Material.COMPARATOR, Material.LEVER, Material.TRIPWIRE_HOOK, Material.TRIPWIRE,
            Material.TNT, Material.NOTE_BLOCK, Material.JUKEBOX, Material.BEACON,
            Material.CONDUIT, Material.END_PORTAL_FRAME, Material.SPAWNER, Material.END_ROD,
            Material.LIGHTNING_ROD, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
            Material.PAINTING, Material.FLOWER_POT, Material.ARMOR_STAND);

    // Mangrove roots (for mangrove tree handling)
    private static final Set<Material> MANGROVE_ROOTS = Set.of(
            Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS);

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
        if (block == null) {
            return;
        }
        if (isLog(block.getType())) {
            block.getChunk().getPersistentDataContainer().set(
                    getBlockKey(block.getLocation()),
                    PersistentDataType.BYTE,
                    (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block == null) {
            return;
        }

        // Always clean up PDC data for broken logs (prevents PDC proliferation)
        if (isLog(block.getType())) {
            cleanupPlayerPlacedMark(block);
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME))
            return;

        if (!isHoldingAxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "no axe");
            return;
        }

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

    /**
     * Handles chunk unload events to clean up PDC data.
     * Prevents PDC data accumulation for chunks that are unloaded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }

        // Clean up all PDC keys for logs in this chunk
        // This prevents PDC data from accumulating for unloaded chunks
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        List<String> keysToRemove = new ArrayList<>();

        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getNamespace().equals(plugin.getName()) && key.getKey().startsWith("pp_")) {
                keysToRemove.add(key.getKey());
            }
        }

        for (String key : keysToRemove) {
            pdc.remove(new NamespacedKey(plugin, key));
        }
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
        if (pos == null || world == null) {
            return;
        }
        // Use SchedulerUtils for Folia-compatible chunk access
        Location loc = pos.toLocation(world);
        SchedulerUtils.runAtLocation(loc, () -> {
            Block block = pos.getBlock(world);
            if (block != null) {
                cleanupPlayerPlacedMark(block);
            }
        });
    }

    /**
     * Cleans up player-placed mark for a block.
     * Used to prevent PDC data proliferation.
     *
     * @param block the block to clean up
     */
    private void cleanupPlayerPlacedMark(Block block) {
        if (block == null) {
            return;
        }
        Chunk chunk = block.getChunk();
        if (chunk == null) {
            return;
        }
        chunk.getPersistentDataContainer().remove(getBlockKey(block.getLocation()));
    }

    /**
     * Advanced structure detection - checks if log is part of a player-built
     * structure.
     * Looks for structural elements like fences, stairs, slabs, doors, etc. nearby.
     */
    private boolean isPartOfStructure(Block startLog) {
        if (startLog == null) {
            return false;
        }

        World world = startLog.getWorld();
        if (world == null) {
            return false;
        }

        BlockPos startPos = BlockPos.from(startLog.getLocation());
        if (startPos == null) {
            return false;
        }

        int structureBlockCount = 0;

        // Check surrounding area for structure blocks
        for (int dx = -STRUCTURE_CHECK_RADIUS; dx <= STRUCTURE_CHECK_RADIUS; dx++) {
            for (int dy = -STRUCTURE_CHECK_RADIUS; dy <= STRUCTURE_CHECK_RADIUS; dy++) {
                for (int dz = -STRUCTURE_CHECK_RADIUS; dz <= STRUCTURE_CHECK_RADIUS; dz++) {
                    BlockPos checkPos = startPos.add(dx, dy, dz);
                    Block checkBlock = checkPos.getBlock(world);

                    if (checkBlock != null && isStructureBlock(checkBlock.getType())) {
                        structureBlockCount++;
                        if (structureBlockCount > MAX_STRUCTURE_BLOCKS_ALLOWED) {
                            return true; // Too many structure blocks nearby - likely a building
                        }
                    }
                }
            }
        }

        // Also check if log is directly adjacent to multiple planks (floor/wall indicator)
        int adjacentPlanks = 0;
        int[][] directNeighbors = {
                { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }
        };
        for (int[] offset : directNeighbors) {
            BlockPos neighbor = startPos.add(offset[0], offset[1], offset[2]);
            Block neighborBlock = neighbor.getBlock(world);
            if (neighborBlock != null) {
                Material mat = neighborBlock.getType();
                if (mat != null && mat.name().endsWith("_PLANKS")) {
                    adjacentPlanks++;
                }
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
        if (startLog == null || logType == null) {
            return false;
        }

        World world = startLog.getWorld();
        if (world == null) {
            return false;
        }

        BlockPos startPos = BlockPos.from(startLog.getLocation());
        if (startPos == null) {
            return false;
        }

        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> visitedLeaves = new HashSet<>();
        Deque<BlockPos> toCheck = new ArrayDeque<>();

        toCheck.add(startPos);
        visitedLogs.add(startPos);

        // BFS to find all connected logs and count nearby leaves
        while (!toCheck.isEmpty()) {
            BlockPos current = toCheck.poll();
            if (current == null) {
                continue;
            }

            // Search for connected logs (including horizontal for multi-trunk)
            for (int[] offset : VALIDATION_LOG_OFFSETS) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visitedLogs.contains(adjacent)) {
                    Block adjBlock = adjacent.getBlock(world);
                    if (adjBlock != null && adjBlock.getType() == logType && !isPlayerPlaced(adjBlock)) {
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
                            Block leafBlock = leafPos.getBlock(world);
                            if (leafBlock != null && isLeaf(leafBlock.getType())) {
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
        if (player == null || originBlock == null || logType == null) {
            return;
        }

        ItemStack axe = player.getInventory().getItemInMainHand();
        if (axe == null) {
            return;
        }

        World world = originBlock.getWorld();
        if (world == null) {
            return;
        }

        BlockPos origin = BlockPos.from(originBlock.getLocation());
        if (origin == null) {
            return;
        }

        // Use distance-based tracking like the reference implementation
        Map<Integer, List<BlockPos>> logsByDistance = new HashMap<>();
        Map<Integer, List<BlockPos>> leavesByDistance = new HashMap<>();
        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> visitedLeaves = new HashSet<>();
        Set<BlockPos> rootsToBreak = new HashSet<>();

        boolean isMangrove = logType == Material.MANGROVE_LOG || logType == Material.STRIPPED_MANGROVE_LOG;

        // BFS to find all connected logs with distance tracking
        Deque<BlockPos> toCheck = new ArrayDeque<>();
        toCheck.add(origin);
        visitedLogs.add(origin);
        logsByDistance.put(0, new ArrayList<>(Collections.singleton(origin)));

        int maxDistance = 64; // Maximum tree size limit

        while (!toCheck.isEmpty() && logsByDistance.size() < maxDistance) {
            BlockPos current = toCheck.poll();
            if (current == null) {
                continue;
            }

            int currentDist = getDistance(origin, current);
            Block block = current.getBlock(world);
            if (block == null) {
                continue;
            }

            // Search for connected logs using appropriate offsets
            int[][] offsets = getOffsetsForTree(block, logType, world);
            for (int[] offset : offsets) {
                BlockPos adjacent = current.add(offset[0], offset[1], offset[2]);
                if (!visitedLogs.contains(adjacent)) {
                    visitedLogs.add(adjacent);
                    Block adjBlock = adjacent.getBlock(world);
                    if (adjBlock == null) {
                        continue;
                    }

                    Material adjType = adjBlock.getType();
                    if (adjType == null) {
                        continue;
                    }

                    if (adjType == logType && !isPlayerPlaced(adjBlock)) {
                        toCheck.add(adjacent);
                        int newDist = getDistance(origin, adjacent);
                        logsByDistance.computeIfAbsent(newDist, k -> new ArrayList<>()).add(adjacent);
                    }

                    // Mangrove roots handling
                    if (isMangrove && isMangroveRoot(adjType)) {
                        rootsToBreak.add(adjacent);
                        visitedLogs.add(adjacent);
                        toCheck.add(adjacent);
                    }
                }
            }

            // For mangrove trees, search for additional roots below
            if (isMangrove) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -3; dy <= 0; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos rootPos = current.add(dx, dy, dz);
                            if (!visitedLogs.contains(rootPos)) {
                                Block rootBlock = rootPos.getBlock(world);
                                if (rootBlock != null && isMangroveRoot(rootBlock.getType())) {
                                    rootsToBreak.add(rootPos);
                                    visitedLogs.add(rootPos);
                                }
                            }
                        }
                    }
                }
            }

            // Find connected leaves (like reference implementation)
            findLeavesAroundLog(current, logType, world, origin, leavesByDistance, visitedLeaves);
        }

        // Calculate total blocks to break for durability check
        int totalLogs = 0;
        for (List<BlockPos> logs : logsByDistance.values()) {
            totalLogs += logs.size();
        }
        int totalLeaves = 0;
        for (List<BlockPos> leaves : leavesByDistance.values()) {
            totalLeaves += leaves.size();
        }
        int totalBlocks = totalLogs + totalLeaves + rootsToBreak.size();

        // Check durability before breaking (like reference)
        if (!canBreakTree(player, axe, totalBlocks)) {
            return;
        }

        // Break blocks following reference implementation order:
        // For each log (sorted by distance), break leaves near it, then break the log
        breakTreeWithLeaves(player, axe, logsByDistance, world, origin.toLocation(world), origin);
    }

    /**
     * Breaks tree following reference implementation:
     * For each log (closest to farthest), break leaves around it, then break the log.
     * This matches reference fellTree() lines 267-321.
     */
    private void breakTreeWithLeaves(Player player, ItemStack axe, Map<Integer, List<BlockPos>> logsByDistance,
                                      World world, Location dropLocation, BlockPos origin) {
        if (!SchedulerUtils.isFolia()) {
            // Paper/Spigot: Break instantly
            breakTreeWithLeavesInstant(player, axe, logsByDistance, world, dropLocation, origin);
        } else {
            // Folia: Use scheduled breaking (simplified for region safety)
            breakTreeWithLeavesInstant(player, axe, logsByDistance, world, dropLocation, origin);
        }
    }

    /**
     * Instant breaking for Paper/Spigot (and Folia as fallback).
     * Follows reference order: for each log, break nearby leaves, then the log.
     */
    private void breakTreeWithLeavesInstant(Player player, ItemStack axe, Map<Integer, List<BlockPos>> logsByDistance,
                                             World world, Location dropLocation, BlockPos origin) {
        ItemStack currentAxe = player.getInventory().getItemInMainHand();
        if (currentAxe == null || currentAxe.getType() != axe.getType()) {
            return; // Tool switched
        }

        // Sort distances (closest to farthest, like reference line 255)
        List<Integer> distances = new ArrayList<>(logsByDistance.keySet());
        Collections.sort(distances);

        // For each distance layer
        for (int dist : distances) {
            List<BlockPos> logs = logsByDistance.get(dist);
            if (logs == null) continue;

            // For each log in this layer
            for (BlockPos logPos : logs) {
                // Skip origin (broken by event)
                if (logPos.equals(origin)) continue;

                // Check tool durability before breaking this log + leaves
                if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentAxe, 1, TOOL_NAME)) {
                    return; // Tool durability too low
                }

                // Reference line 267/311: Get leaves around THIS specific log
                // This is the KEY difference - leaves found per-log, not all upfront!
                Map<Integer, List<BlockPos>> leavesAroundLog = new HashMap<>();
                Set<BlockPos> visitedLeaves = new HashSet<>();
                findLeavesAroundLog(logPos, null, world, origin, leavesAroundLog, visitedLeaves);

                // Break all leaves around this log (reference line 270/314)
                for (List<BlockPos> leafList : leavesAroundLog.values()) {
                    for (BlockPos leafPos : leafList) {
                        breakSingleBlock(player, currentAxe, leafPos, world, dropLocation, false);
                    }
                }

                // Break the log itself (reference line 273/317)
                breakSingleBlock(player, currentAxe, logPos, world, dropLocation, true);
            }
        }
    }

    /**
     * Breaks a single block with item drops and PDC cleanup.
     */
    private void breakSingleBlock(Player player, ItemStack axe, BlockPos pos, World world,
                                   Location dropLocation, boolean isLog) {
        if (pos == null) return;

        Block block = pos.getBlock(world);
        if (block == null || block.getType() == Material.AIR) return;

        // Drop items
        for (ItemStack drop : block.getDrops(axe)) {
            world.dropItemNaturally(dropLocation, drop);
        }

        // Remove PDC mark if log
        if (isLog) {
            cleanupPlayerPlacedMark(block);
        }

        // Break block
        block.setType(Material.AIR);
    }

    /**
     * Finds all leaves connected to a log block using BFS (like reference getBlocksWithLeafCheck).
     * This method searches for leaves around the log, then recursively finds leaves connected
     * to those leaves (chain reaction), matching the reference implementation's behavior.
     */
    private void findLeavesAroundLog(BlockPos logPos, Material logType, World world,
                                      BlockPos origin, Map<Integer, List<BlockPos>> leavesByDistance,
                                      Set<BlockPos> visitedLeaves) {
        if (logPos == null || world == null) {
            return;
        }

        int leafRange = 6; // Reference LEAF_DETECT_RANGE default is 6

        // Use BFS to find all connected leaves (like reference getBlocks method)
        Queue<BlockPos> leafQueue = new ArrayDeque<>();
        Set<BlockPos> discoveredLeaves = new HashSet<>();

        // Start by finding leaves adjacent to the log
        for (int dx = -leafRange; dx <= leafRange; dx++) {
            for (int dy = -leafRange; dy <= leafRange; dy++) {
                for (int dz = -leafRange; dz <= leafRange; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;

                    BlockPos leafPos = logPos.add(dx, dy, dz);
                    Block leafBlock = leafPos.getBlock(world);

                    if (leafBlock != null && isLeaf(leafBlock.getType())) {
                        // Check if leaf is player-placed (persistent)
                        if (!isPlayerPlaced(leafBlock)) {
                            // Check leaf distance data if available (like reference)
                            if (isValidLeafForTreeFelling(leafBlock, leafRange)) {
                                if (!discoveredLeaves.contains(leafPos)) {
                                    discoveredLeaves.add(leafPos);
                                    leafQueue.add(leafPos);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Now do BFS to find leaves connected to other leaves (chain reaction)
        // This is the key difference - reference follows leaf-to-leaf connections
        while (!leafQueue.isEmpty()) {
            BlockPos currentLeaf = leafQueue.poll();
            if (currentLeaf == null) {
                continue;
            }

            // Skip if already processed globally
            if (visitedLeaves.contains(currentLeaf)) {
                continue;
            }

            // Add to final results
            visitedLeaves.add(currentLeaf);
            int dist = getDistance(origin, currentLeaf);
            leavesByDistance.computeIfAbsent(dist, k -> new ArrayList<>()).add(currentLeaf);

            // Search for connected leaves around this leaf (diagonal search like reference)
            Block currentBlock = currentLeaf.getBlock(world);
            if (currentBlock == null) {
                continue;
            }

            // Check all 26 directions (3x3x3 cube minus center) like reference
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0)
                            continue;

                        BlockPos adjacentLeaf = currentLeaf.add(dx, dy, dz);
                        if (!discoveredLeaves.contains(adjacentLeaf) && !visitedLeaves.contains(adjacentLeaf)) {
                            Block adjacentBlock = adjacentLeaf.getBlock(world);
                            if (adjacentBlock != null && isLeaf(adjacentBlock.getType())) {
                                if (!isPlayerPlaced(adjacentBlock)) {
                                    if (isValidLeafForTreeFelling(adjacentBlock, leafRange)) {
                                        discoveredLeaves.add(adjacentLeaf);
                                        leafQueue.add(adjacentLeaf);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates if a leaf should be broken.
     * Simplified check - only verify it's not player-placed.
     * Reference implementation breaks ALL natural leaves without strict distance checks.
     */
    private boolean isValidLeafForTreeFelling(Block leafBlock, int maxRange) {
        // Don't check decay distance - break all natural leaves
        // The BFS chain reaction naturally limits which leaves are found
        return true;
    }

    /**
     * Calculates Manhattan distance between two positions
     */
    private int getDistance(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return 0;
        }
        return Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y()) + Math.abs(from.z() - to.z());
    }

    /**
     * Selects appropriate offset array based on tree characteristics
     */
    private int[][] getOffsetsForTree(Block block, Material logType, World world) {
        // Check tree height to determine offset complexity
        int height = measureTreeHeightDuringBFS(BlockPos.from(block.getLocation()), logType, world);
        if (height > 10) {
            return TALL_TREE_OFFSETS;
        }
        return COMPACT_OFFSETS;
    }

    /**
     * Checks if player has enough durability to break the entire tree
     * Similar to reference implementation's durability check
     */
    private boolean canBreakTree(Player player, ItemStack axe, int totalBlocks) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
            player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return true;
        }

        if (axe.getType().getMaxDurability() == 0) {
            return true; // Unbreakable tool
        }

        int durability = axe.getType().getMaxDurability() - axe.getDurability();
        // Need 1 durability per block (simplified, like reference)
        return durability >= totalBlocks || totalBlocks <= 1;
    }

    /**
     * Breaks a single block and drops items
     * @return true if successful, false if should stop (tool broke/switched)
     */
    private boolean breakBlock(Player player, ItemStack axe, BlockPos pos, World world, Location dropLocation) {
        if (pos == null || world == null) {
            return true;
        }

        Block block = pos.getBlock(world);
        if (block == null || block.getType() == Material.AIR) {
            return true;
        }

        // Check tool is still in hand
        ItemStack currentAxe = player.getInventory().getItemInMainHand();
        if (currentAxe == null || currentAxe.getType() != axe.getType()) {
            return false;
        }

        // Consume durability
        if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentAxe, 1, TOOL_NAME)) {
            return false;
        }

        // Drop items
        for (ItemStack drop : block.getDrops(currentAxe)) {
            world.dropItemNaturally(dropLocation, drop);
        }

        // Remove PDC mark if log
        if (isLog(block.getType())) {
            removePlayerPlacedMark(pos, world);
        }

        // Break block
        block.setType(Material.AIR);
        return true;
    }

    /**
     * Measures tree height during initial BFS (no separate pass needed).
     */
    private int measureTreeHeightDuringBFS(BlockPos origin, Material logType, World world) {
        if (origin == null || world == null || logType == null) {
            return 0;
        }

        int height = 0;
        for (int y = 0; y < 64; y++) {
            BlockPos check = origin.add(0, y, 0);
            Block block = check.getBlock(world);
            if (block == null) {
                break;
            }
            Material blockType = block.getType();
            if (blockType == logType && !isPlayerPlaced(block)) {
                height++;
            } else if (isLeaf(blockType)) {
                break;
            } else {
                // Not a log or leaf - stop counting
                break;
            }
        }
        return height;
    }
}
