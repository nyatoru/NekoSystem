package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.features.treefeller.animation.FallingTreeAnimation;
import com.nyarutoru.nekoplugin.features.treefeller.animation.TreeFellerEffects;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolConfig;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolMatcher;
import com.nyarutoru.nekoplugin.features.treefeller.tree.FastLeafDecay;
import com.nyarutoru.nekoplugin.features.treefeller.tree.LeafValidator;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeDetector;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeStructure;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Event listener for the TreeFeller feature.
 * Reworked: async detection (Paper async + Folia region), individual tree detection,
 * and irregular growth support via TreeDetector flags.
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "TreeFeller";
    private static final int MAX_BLOCKS = 500;
    private static final String PLAYER_PLACED_PREFIX = "pp_";

    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.OAK_WOOD,
            Material.SPRUCE_LOG, Material.SPRUCE_WOOD,
            Material.BIRCH_LOG, Material.BIRCH_WOOD,
            Material.JUNGLE_LOG, Material.JUNGLE_WOOD,
            Material.ACACIA_LOG, Material.ACACIA_WOOD,
            Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD,
            Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.MANGROVE_ROOTS,
            Material.CHERRY_LOG, Material.CHERRY_WOOD,
            Material.PALE_OAK_LOG, Material.PALE_OAK_WOOD,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD,
            Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD,
            Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD,
            Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD,
            Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD,
            Material.STRIPPED_PALE_OAK_LOG, Material.STRIPPED_PALE_OAK_WOOD
    );

    private final TreeDetector treeDetector;
    private final ToolMatcher toolMatcher;
    private final TreeFellerEffects effects;
    private final FallingTreeAnimation animation;
    private final FastLeafDecay fastLeafDecay;
    private final NekoPlugin plugin;
    private final BooleanSupplier isCurrent;
    private final Consumer<SchedulerUtils.TaskHandle> taskOwner;

    public TreeFellerListener(NekoPlugin plugin, BooleanSupplier isCurrent,
                              Consumer<SchedulerUtils.TaskHandle> taskOwner) {
        this.plugin = plugin;
        this.isCurrent = isCurrent;
        this.taskOwner = taskOwner;
        this.treeDetector = new TreeDetector();
        this.toolMatcher = new ToolMatcher();
        this.effects = new TreeFellerEffects();
        this.animation = new FallingTreeAnimation();
        this.fastLeafDecay = new FastLeafDecay();
    }

    private NamespacedKey getBlockKey(Block block) {
        Location loc = block.getLocation();
        String key = PLAYER_PLACED_PREFIX + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        return new NamespacedKey(plugin, key);
    }

    private boolean isPlayerPlaced(Block block) {
        if (block == null) return false;
        Chunk chunk = block.getChunk();
        if (chunk == null) return false;
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        NamespacedKey key = getBlockKey(block);
        Byte value = pdc.get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private void markPlayerPlaced(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        if (chunk == null) return;
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        pdc.set(getBlockKey(block), PersistentDataType.BYTE, (byte) 1);
    }

    private void cleanupPlayerPlaced(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        if (chunk == null) return;
        chunk.getPersistentDataContainer().remove(getBlockKey(block));
    }

    private boolean hasPlayerPlacedLog(World world, List<BlockPos> logs) {
        if (!TreeFellerConfig.ALLOW_PLAYER_PLACED) {
            for (BlockPos logPos : logs) {
                Block logBlock;
                try { logBlock = logPos.getBlock(world); } catch (Throwable ex) { continue; }
                if (logBlock != null) {
                    try { if (isPlayerPlaced(logBlock)) return true; } catch (Throwable ignored) {}
                }
            }
        }
        return false;
    }

    private boolean isHoldingAxe(Player player) {
        try {
            ItemStack item = player.getInventory().getItemInMainHand();
            return ItemUtils.isAxe(item);
        } catch (Throwable ex) {
            return false;
        }
    }

    private void sendDebug(Player player, Component message) {
        if (SchedulerUtils.isFolia()) {
            SchedulerUtils.runAtEntity(player, () -> {
                try { if (player.isOnline()) player.sendMessage(message); } catch (Throwable ignored) {}
            });
        } else {
            player.sendMessage(message);
        }
    }

    private boolean isValidCutHeight(TreeStructure tree, Block brokenBlock) {
        int bottomY = tree.getBottomY();
        int cutY = brokenBlock.getY();
        int heightFromBottom = cutY - bottomY + 1;
        return heightFromBottom <= TreeFellerConfig.MAX_HEIGHT_FROM_BOTTOM;
    }

    private boolean isValidVerticalRatio(TreeStructure tree) {
        if (TreeFellerConfig.ALLOW_IRREGULAR_GROWTH) {
            // Irregular growth (acacia bend, cherry branches, pale oak) often has many horizontal logs
            // Relax check: only block extreme horizontal structures (ratio < 0.25)
            int verticalLogs = tree.getVerticalLogCount();
            int horizontalLogs = tree.getHorizontalLogCount();
            if (horizontalLogs == 0) return true;
            double ratio = (double) verticalLogs / horizontalLogs;
            return ratio >= Math.min(TreeFellerConfig.MIN_VERTICAL_LOG_RATIO, 0.25);
        }
        int verticalLogs = tree.getVerticalLogCount();
        int horizontalLogs = tree.getHorizontalLogCount();
        if (horizontalLogs == 0) return true;
        double ratio = (double) verticalLogs / horizontalLogs;
        return ratio >= TreeFellerConfig.MIN_VERTICAL_LOG_RATIO;
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        ActiveToolAPI.getInstance().onShift(player, TOOL_NAME, this::isHoldingAxe, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block != null && LOGS.contains(block.getType())) {
            markPlayerPlaced(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME)) return;
        if (!isHoldingAxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "wrong tool");
            return;
        }
        Block block = event.getBlock();
        Material blockType = block.getType();
        if (!LOGS.contains(blockType)) return;
        if (!TreeFellerConfig.ENABLED) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        // Pre-check tool match synchronously before async
        ItemStack toolPreview = player.getInventory().getItemInMainHand();
        if (toolMatcher.match(toolPreview) == null) return;

        if (TreeFellerConfig.ASYNC_DETECTION) {
            // Async path: cancel immediately, detect off-thread, then schedule breaks on region thread
            // ponytail: async block reads not thread-safe on Folia — use region scheduler there
            event.setCancelled(true);
            BlockPos originPos = BlockPos.from(block);
            World world = block.getWorld();
            Location blockLoc = block.getLocation();
            ItemStack toolSnapshot = toolPreview.clone();
            // Need to capture player-placed check synchronously for this block before async (chunk PDC is main-thread only)
            boolean originPlayerPlaced = !TreeFellerConfig.ALLOW_PLAYER_PLACED && isPlayerPlaced(block);

            Runnable detectionTask = () -> {
                if (!isCurrent.getAsBoolean()) {
                    // feature disabled, restore single break
                    SchedulerUtils.runAtLocationTask(blockLoc, () -> {
                        if (!isCurrent.getAsBoolean()) return;
                        Block b = originPos.getBlock(world);
                        if (b != null && b.getType() != Material.AIR) b.breakNaturally(toolSnapshot);
                    });
                    return;
                }
                TreeStructure tree;
                try {
                    tree = treeDetector.detect(world, originPos);
                } catch (Exception ex) {
                    plugin.getLogger().warning("TreeFeller async detect failed: " + ex.getMessage());
                    SchedulerUtils.runAtLocationTask(blockLoc, () -> {
                        Block b = originPos.getBlock(world);
                        if (b != null && b.getType() != Material.AIR) b.breakNaturally(toolSnapshot);
                    });
                    return;
                }
                if (tree == null) {
                    // Not a tree -> restore single block break on region thread
                    SchedulerUtils.runAtLocationTask(blockLoc, () -> {
                        Block b = originPos.getBlock(world);
                        if (b != null && LOGS.contains(b.getType())) {
                            cleanupPlayerPlaced(b);
                            b.breakNaturally(toolSnapshot);
                        }
                    });
                    return;
                }
                // Validate and fell on region thread
                SchedulerUtils.runAtLocationTask(blockLoc, () -> handleFell(player, block, originPlayerPlaced, tree, toolSnapshot, world));
            };

            if (SchedulerUtils.isFolia()) {
                // Folia: run detection on region thread (still async vs event)
                SchedulerUtils.runAtLocationTask(blockLoc, detectionTask);
            } else {
                SchedulerUtils.runAsyncTask(detectionTask);
            }
            return;
        }

        // Sync path (legacy)
        World world = block.getWorld();
        TreeStructure tree = treeDetector.detect(world, BlockPos.from(block));
        if (tree == null) return;

        // Re-validate synchronously (same checks as handleFell but without async fallback)
        if (hasPlayerPlacedLog(world, tree.getLogs())) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Cannot fell player-placed trees", NamedTextColor.YELLOW));
            return;
        }
        if (!isValidCutHeight(tree, block)) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Must cut within " + TreeFellerConfig.MAX_HEIGHT_FROM_BOTTOM + " blocks from bottom (cut at Y=" + block.getY() + ", tree bottom at Y=" + tree.getBottomY() + ")", NamedTextColor.YELLOW));
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        ToolConfig toolConfig = toolMatcher.match(tool);
        if (toolConfig == null) return;
        if (tree.getLogCount() < TreeFellerConfig.REQUIRED_LOGS) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Insufficient logs detected (" + tree.getLogCount() + " < " + TreeFellerConfig.REQUIRED_LOGS + ")", NamedTextColor.YELLOW));
            return;
        }
        if (!isValidVerticalRatio(tree)) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Insufficient vertical logs (ratio too low)", NamedTextColor.YELLOW));
            return;
        }
        LeafValidator validator = new LeafValidator(tree.getTreeType());
        if (!validator.validate(world, tree.getLogs(), tree.getLeaves())) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Insufficient leaves detected", NamedTextColor.YELLOW));
            return;
        }
        List<BlockPos> logsToFell = tree.getLogs();
        if (tree.isOverflow() && !TreeFellerConfig.ALLOW_PARTIAL) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Tree exceeds the maximum size and partial felling is disabled", NamedTextColor.YELLOW));
            return;
        }
        int logCount = logsToFell.size();
        int totalDurabilityCost = toolConfig.getDurabilityCost() * logCount;
        ItemStack dropTool = tool.clone();
        if (!ItemUtils.consumeDurabilityOrDeactivate(player, tool, totalDurabilityCost, toolConfig.getName())) {
            if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Tool would break (cost: " + totalDurabilityCost + ")", NamedTextColor.YELLOW));
            return;
        }
        event.setCancelled(true);
        cleanupPlayerPlaced(block);
        breakTree(player, tree, logsToFell, dropTool);
        if (TreeFellerConfig.CASCADE) handleCascade(player, tree, tool, toolConfig);
        if (TreeFellerConfig.DEBUG) sendDebug(player, Component.text("TreeFeller: Felled " + logsToFell.size() + " logs", NamedTextColor.GREEN));
    }

    private void handleFell(Player player, Block block, boolean originPlayerPlaced, TreeStructure tree, ItemStack toolSnapshot, World world) {
        if (!isCurrent.getAsBoolean()) return;
        // origin placed check already done for async path
        if (originPlayerPlaced || hasPlayerPlacedLog(world, tree.getLogs())) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Cannot fell player-placed trees", NamedTextColor.YELLOW));
            }
            // restore single break if we previously cancelled async
            if (TreeFellerConfig.ASYNC_DETECTION) {
                Block b = tree.getOrigin().getBlock(world);
                if (b != null && LOGS.contains(b.getType())) {
                    b.breakNaturally(toolSnapshot);
                }
            }
            return;
        }

        if (!isValidCutHeight(tree, block)) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Must cut within " + TreeFellerConfig.MAX_HEIGHT_FROM_BOTTOM +
                        " blocks from bottom (cut at Y=" + block.getY() + ", tree bottom at Y=" + tree.getBottomY() + ")", NamedTextColor.YELLOW));
            }
            if (TreeFellerConfig.ASYNC_DETECTION) {
                block.breakNaturally(toolSnapshot);
            }
            return;
        }

        ToolConfig toolConfig = toolMatcher.match(toolSnapshot);
        if (toolConfig == null) {
            if (TreeFellerConfig.ASYNC_DETECTION) block.breakNaturally(toolSnapshot);
            return;
        }

        if (tree.getLogCount() < TreeFellerConfig.REQUIRED_LOGS) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Insufficient logs detected (" +
                        tree.getLogCount() + " < " + TreeFellerConfig.REQUIRED_LOGS + ")", NamedTextColor.YELLOW));
            }
            if (TreeFellerConfig.ASYNC_DETECTION) block.breakNaturally(toolSnapshot);
            return;
        }

        if (!isValidVerticalRatio(tree)) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Insufficient vertical logs (ratio too low)", NamedTextColor.YELLOW));
            }
            if (TreeFellerConfig.ASYNC_DETECTION) block.breakNaturally(toolSnapshot);
            return;
        }

        LeafValidator validator = new LeafValidator(tree.getTreeType());
        if (!validator.validate(world, tree.getLogs(), tree.getLeaves())) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Insufficient leaves detected", NamedTextColor.YELLOW));
            }
            if (TreeFellerConfig.ASYNC_DETECTION) block.breakNaturally(toolSnapshot);
            return;
        }

        List<BlockPos> logsToFell = tree.getLogs();
        if (tree.isOverflow() && !TreeFellerConfig.ALLOW_PARTIAL) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Tree exceeds the maximum size and partial felling is disabled",
                        NamedTextColor.YELLOW));
            }
            if (TreeFellerConfig.ASYNC_DETECTION) block.breakNaturally(toolSnapshot);
            return;
        }

        int logCount = logsToFell.size();
        int totalDurabilityCost = toolConfig.getDurabilityCost() * logCount;
        ItemStack dropTool = toolSnapshot.clone();
        // Need live tool from player inventory for durability consumption
        ItemStack liveTool = player.getInventory().getItemInMainHand();
        // If async, liveTool may differ from snapshot if player switched item; use snapshot for cost check but apply to live
        ItemStack targetTool = liveTool != null && toolMatcher.match(liveTool) != null ? liveTool : toolSnapshot;

        if (!ItemUtils.consumeDurabilityOrDeactivate(player, targetTool, totalDurabilityCost, toolConfig.getName())) {
            if (TreeFellerConfig.DEBUG) {
                sendDebug(player, Component.text("TreeFeller: Tool would break (cost: " + totalDurabilityCost + ")", NamedTextColor.YELLOW));
            }
            return;
        }

        // For sync path, event already cancelled by caller? async path already cancelled.
        // Ensure single block not double-broken: for sync path we cancel here.
        if (!TreeFellerConfig.ASYNC_DETECTION) {
            // Check if block still exists (not already cancelled async)
            // Cancel the original event effect by manually handling break
            // The caller hasn't cancelled yet for sync path, so we rely on breaking tree instead of single
            // But onBlockBreak sync path was not cancelled yet, we need to prevent default break.
            // Since we are inside handler with HIGHEST, we cannot directly cancel the event here easily without reference.
            // Instead, we break tree and the caller will setCancelled(true). For this helper we assume caller manages cancellation.
            // This helper is called from sync path where cancellation not yet done, so we directly break tree and assume caller cancelled.
            // To make this work for both paths, we ensure block is not broken naturally before tree.
        }
        // For async, event already cancelled, just break tree
        // For sync, we need to cancel original break: find the BlockBreakEvent? Not available here, so we handle via breakTree which breaks logs.
        // The original log block will be included in logsToFell and broken via animation, so we just need to ensure we don't double.
        if (!TreeFellerConfig.ASYNC_DETECTION) {
            // In sync path, we are still inside onBlockBreak, but we haven't cancelled event yet.
            // We will cancel via helper: we need to return flag. Instead, we directly handle break and let caller cancel.
            // This method is also called from sync path inside onBlockBreak after detection, so we can just proceed.
            // To avoid duplication, we signal via exception? Simpler: let caller handle cancellation.
            // Here we just proceed to breakTree; caller must have setCancelled before calling? For sync we set now.
            // We don't have event reference, so we break tree and the single block will be overwritten.
        }

        // Ensure origin block's player-placed mark cleaned
        cleanupPlayerPlaced(block);
        breakTree(player, tree, logsToFell, dropTool);

        if (TreeFellerConfig.CASCADE) {
            handleCascade(player, tree, targetTool, toolConfig);
        }

        if (TreeFellerConfig.DEBUG) {
            sendDebug(player, Component.text("TreeFeller: Felled " + logsToFell.size() + " logs",
                    NamedTextColor.GREEN));
        }
    }

    private void breakTree(Player player, TreeStructure tree, List<BlockPos> logsToFell, ItemStack dropTool) {
        World world = player.getWorld();
        animation.playAnimation(world, logsToFell, dropTool, effects, isCurrent, taskOwner);
        long decayStartDelay = TreeFellerConfig.ANIMATION_ENABLED
                ? (long) logsToFell.size() * TreeFellerConfig.ANIMATION_DELAY_TICKS
                : 0L;
        fastLeafDecay.schedule(world, tree.getLeaves(), decayStartDelay, isCurrent, taskOwner);
        if (TreeFellerConfig.SOUNDS_ENABLED) {
            try {
                Location loc;
                try { loc = player.getLocation(); } catch (Throwable ex) { loc = tree.getOrigin().toLocation(world); }
                // playSound must be on block's region thread; breakTree is already called on that thread
                world.playSound(loc, TreeFellerConfig.FELL_SOUND,
                        TreeFellerConfig.SOUND_VOLUME, TreeFellerConfig.SOUND_PITCH);
            } catch (Throwable ignored) {}
        }
        if (TreeFellerConfig.REPLANT_SAPLINGS && Math.random() <= TreeFellerConfig.REPLANT_CHANCE) {
            // replant must be on sapling block's region thread
            BlockPos origin = tree.getOrigin();
            if (origin != null) {
                BlockPos saplingPos = new BlockPos(origin.x(), origin.y() + 1, origin.z());
                SchedulerUtils.runAtLocation(saplingPos.toLocation(world), () -> replantSapling(world, origin));
            } else {
                replantSapling(world, origin);
            }
        }
    }

    private void handleCascade(Player player, TreeStructure tree, ItemStack tool, ToolConfig toolConfig) {
        World world = player.getWorld();
        Set<BlockPos> processedLogs = new HashSet<>(tree.getLogs());
        Queue<BlockPos> cascadeQueue = new ArrayDeque<>(tree.getLogs());
        int cascadedTrees = 0;
        int maxCascades = 100;

        while (!cascadeQueue.isEmpty() && cascadedTrees < maxCascades) {
            BlockPos checkPos = cascadeQueue.poll();
            Block checkBlock = checkPos.getBlock(world);
            if (checkBlock == null) continue;
            for (BlockPos offset : new BlockPos[] {
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
            }) {
                BlockPos adjacentPos = new BlockPos(
                    checkPos.x() + offset.x(),
                    checkPos.y() + offset.y(),
                    checkPos.z() + offset.z()
                );
                if (processedLogs.contains(adjacentPos)) continue;
                Block adjacentBlock = adjacentPos.getBlock(world);
                if (adjacentBlock == null || !LOGS.contains(adjacentBlock.getType())) continue;
                TreeStructure adjacentTree = treeDetector.detect(world, adjacentPos);
                if (adjacentTree == null) continue;
                if (hasPlayerPlacedLog(world, adjacentTree.getLogs())) continue;
                if (adjacentTree.getLogCount() < TreeFellerConfig.REQUIRED_LOGS) continue;
                if (!isValidVerticalRatio(adjacentTree)) continue;
                LeafValidator validator = new LeafValidator(adjacentTree.getTreeType());
                if (!validator.validate(world, adjacentTree.getLogs(), adjacentTree.getLeaves())) continue;
                processedLogs.addAll(adjacentTree.getLogs());
                List<BlockPos> logsToFell = adjacentTree.getLogs();
                if (adjacentTree.isOverflow() && !TreeFellerConfig.ALLOW_PARTIAL) continue;
                int logCount = logsToFell.size();
                int durabilityCost = toolConfig.getDurabilityCost() * logCount;
                if (!ItemUtils.consumeDurabilityOrDeactivate(player, tool, durabilityCost, toolConfig.getName())) {
                    if (TreeFellerConfig.DEBUG) {
                        sendDebug(player, Component.text("TreeFeller Cascade: Tool would break", NamedTextColor.YELLOW));
                    }
                    return;
                }
                breakTree(player, adjacentTree, logsToFell, tool.clone());
                cascadedTrees++;
                cascadeQueue.addAll(adjacentTree.getLogs());
                if (TreeFellerConfig.DEBUG) {
                    sendDebug(player, Component.text("TreeFeller Cascade: Felled tree " + cascadedTrees, NamedTextColor.GREEN));
                }
            }
        }
        if (TreeFellerConfig.DEBUG && cascadedTrees > 0) {
            sendDebug(player, Component.text("TreeFeller Cascade: Total " + cascadedTrees + " trees felled", NamedTextColor.GREEN));
        }
    }

    private void replantSapling(World world, BlockPos origin) {
        if (origin == null) return;
        BlockPos saplingPos = new BlockPos(origin.x(), origin.y() + 1, origin.z());
        Block saplingBlock = saplingPos.getBlock(world);
        if (saplingBlock != null && saplingBlock.getType() == Material.AIR) {
            Material sapling = getSaplingForTree(origin.getBlock(world).getType());
            if (sapling != null) {
                saplingBlock.setType(sapling);
            }
        }
    }

    private Material getSaplingForTree(Material logType) {
        return switch (logType) {
            case OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD -> Material.OAK_SAPLING;
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD -> Material.DARK_OAK_SAPLING;
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD -> Material.MANGROVE_PROPAGULE;
            case CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD -> Material.CHERRY_SAPLING;
            case PALE_OAK_LOG, PALE_OAK_WOOD, STRIPPED_PALE_OAK_LOG, STRIPPED_PALE_OAK_WOOD -> Material.PALE_OAK_SAPLING;
            default -> null;
        };
    }
}
