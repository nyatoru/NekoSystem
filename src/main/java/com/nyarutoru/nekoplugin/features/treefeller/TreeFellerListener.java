package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.features.treefeller.animation.FallingTreeAnimation;
import com.nyarutoru.nekoplugin.features.treefeller.animation.TreeFellerEffects;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolConfig;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolMatcher;
import com.nyarutoru.nekoplugin.features.treefeller.tree.LeafValidator;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeDetector;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeStructure;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Event listener for the TreeFeller feature.
 * <p>
 * Handles tree felling using shift-activation (10 shifts within 3 seconds).
 * Uses ActiveToolAPI for activation tracking.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public class TreeFellerListener implements Listener {

    public static final String TOOL_NAME = "TreeFeller";
    private static final int MAX_BLOCKS = 500;

    // Log materials that can be vein-mined
    // Using EnumSet for optimal performance
    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.OAK_WOOD,
            Material.SPRUCE_LOG, Material.SPRUCE_WOOD,
            Material.BIRCH_LOG, Material.BIRCH_WOOD,
            Material.JUNGLE_LOG, Material.JUNGLE_WOOD,
            Material.ACACIA_LOG, Material.ACACIA_WOOD,
            Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD,
            Material.MANGROVE_LOG, Material.MANGROVE_WOOD,
            Material.CHERRY_LOG, Material.CHERRY_WOOD,
            // Stripped variants
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD,
            Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD,
            Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD,
            Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD,
            Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD
    );

    private final TreeDetector treeDetector;
    private final ToolMatcher toolMatcher;
    private final TreeFellerEffects effects;
    private final FallingTreeAnimation animation;

    /**
     * Creates a new TreeFellerListener.
     */
    public TreeFellerListener() {
        this.treeDetector = new TreeDetector();
        this.toolMatcher = new ToolMatcher();
        this.effects = new TreeFellerEffects();
        this.animation = new FallingTreeAnimation();
    }

    /**
     * Checks if player is holding a valid axe.
     */
    private boolean isHoldingAxe(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return ItemUtils.isAxe(item);
    }

    /**
     * Handles player sneak events for shift-activation.
     */
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();

        ActiveToolAPI.getInstance().onShift(
                player,
                TOOL_NAME,
                this::isHoldingAxe,
                null);
    }

    /**
     * Handles block break events for tree felling.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME)) {
            return;
        }

        if (!isHoldingAxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "wrong tool");
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!LOGS.contains(blockType)) {
            return;
        }

        // Check if feature is enabled
        if (!TreeFellerConfig.ENABLED) {
            return;
        }

        // Check if player is in valid game mode
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        World world = block.getWorld();
        TreeStructure tree = treeDetector.detect(world, BlockPos.from(block));
        if (tree == null) {
            return;
        }

        // Check if player is using a valid tool
        ItemStack tool = player.getInventory().getItemInMainHand();
        ToolConfig toolConfig = toolMatcher.match(tool);

        if (toolConfig == null) {
            return;
        }

        // Validate tree has sufficient leaves
        LeafValidator validator = new LeafValidator(tree.getTreeType());
        if (!validator.validate(world, tree.getLogs(), tree.getLeaves())) {
            if (TreeFellerConfig.DEBUG) {
                player.sendMessage(Component.text("TreeFeller: Insufficient leaves detected", NamedTextColor.YELLOW));
            }
            return;
        }

        // Cancel the original block break (we'll handle it ourselves)
        event.setCancelled(true);

        // Apply durability cost
        if (!ItemUtils.consumeDurabilityOrDeactivate(player, tool, toolConfig.getDurabilityCost(), toolConfig.getName())) {
            if (TreeFellerConfig.DEBUG) {
                player.sendMessage(Component.text("TreeFeller: Tool would break", NamedTextColor.YELLOW));
            }
            return;
        }

        // Break the tree
        breakTree(player, tree, tool);

        // Log if debug mode
        if (TreeFellerConfig.DEBUG) {
            player.sendMessage(Component.text("TreeFeller: Felled " + tree.getLogCount() + " logs and " +
                    tree.getLeafCount() + " leaves", NamedTextColor.GREEN));
        }
    }

    /**
     * Breaks all blocks in the tree and plays effects.
     *
     * @param player the player who broke the tree
     * @param tree the tree structure to break
     * @param tool the tool used
     */
    private void breakTree(Player player, TreeStructure tree, ItemStack tool) {
        World world = player.getWorld();

        // Get leaves to break
        LeafValidator validator = new LeafValidator(tree.getTreeType());
        Set<BlockPos> leavesToBreak = validator.findLeavesToBreak(world, tree.getLogs());

        // Play animation (which also breaks blocks and plays effects)
        animation.playAnimation(world, tree.getLogs(), effects);

        // Break leaves instantly
        for (BlockPos leafPos : leavesToBreak) {
            Block leafBlock = leafPos.getBlock(world);
            if (leafBlock != null && leafBlock.getType() != Material.AIR) {
                leafBlock.breakNaturally(tool);
                effects.playEffects(leafBlock);
            }
        }

        // Play a completion sound
        if (TreeFellerConfig.SOUNDS_ENABLED) {
            world.playSound(player.getLocation(), TreeFellerConfig.FELL_SOUND,
                    TreeFellerConfig.SOUND_VOLUME, TreeFellerConfig.SOUND_PITCH);
        }

        // Handle sapling replanting
        if (TreeFellerConfig.REPLANT_SAPLINGS && Math.random() <= TreeFellerConfig.REPLANT_CHANCE) {
            replantSapling(world, tree.getOrigin());
        }
    }

    /**
     * Replants a sapling at the base of the tree.
     *
     * @param world the world
     * @param origin the origin block position
     */
    private void replantSapling(World world, BlockPos origin) {
        if (origin == null) {
            return;
        }

        // Get the block above the origin (where sapling should be planted)
        BlockPos saplingPos = new BlockPos(origin.x(), origin.y() + 1, origin.z());
        Block saplingBlock = saplingPos.getBlock(world);

        if (saplingBlock != null && saplingBlock.getType() == Material.AIR) {
            // Get the sapling material for this tree type
            Material sapling = getSaplingForTree(origin.getBlock(world).getType());
            if (sapling != null) {
                saplingBlock.setType(sapling);
            }
        }
    }

    /**
     * Gets the sapling material for a given log type.
     *
     * @param logType the log material
     * @return the corresponding sapling material, or null if not found
     */
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
            default -> null;
        };
    }
}
