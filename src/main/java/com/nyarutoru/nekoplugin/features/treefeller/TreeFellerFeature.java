package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeType;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolConfig;

/**
 * Main feature class for the TreeFeller system.
 * <p>
 * TreeFeller allows players to cut down entire trees by breaking a single log block.
 * The feature detects connected logs using BFS, validates with leaf detection,
 * and breaks all wood blocks while respecting tool durability and cooldowns.
 * <p>
 * Features:
 * <ul>
 *     <li>Shift-activation (10 shifts within 3 seconds)</li>
 *     <li>BFS-based tree detection algorithm</li>
 *     <li>Leaf validation to prevent felling player structures</li>
 *     <li>Configurable tool requirements (all axe types supported)</li>
 *     <li>Support for all vanilla tree types</li>
 *     <li>Sound effects</li>
 *     <li>Optional falling tree animation</li>
 *     <li>Optional sapling replanting</li>
 *     <li>Debug mode for troubleshooting</li>
 * </ul>
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public class TreeFellerFeature extends AbstractFeature {

    private TreeFellerListener listener;
    private NekoPlugin plugin;

    /**
     * Creates a new TreeFellerFeature.
     */
    public TreeFellerFeature() {
        super("treefeller", "TreeFeller");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        super.onEnable(plugin);
        this.plugin = plugin;

        // Register event listener
        listener = new TreeFellerListener();
        registerListener(listener, plugin);
        plugin.getLogger().info("TreeFeller event listener registered");

        // Log configuration summary
        logConfiguration();

        plugin.getLogger().info("TreeFeller feature enabled successfully!");
    }

    @Override
    protected void cleanup() {
        // Clear references
        listener = null;
    }

    /**
     * Logs a summary of the current configuration.
     */
    private void logConfiguration() {
        if (plugin == null) {
            return;
        }

        plugin.getLogger().info("=== TreeFeller Configuration ===");
        plugin.getLogger().info("Enabled: " + TreeFellerConfig.ENABLED);
        plugin.getLogger().info("Debug Mode: " + TreeFellerConfig.DEBUG);
        plugin.getLogger().info("Max Tree Size: " + TreeFellerConfig.MAX_TREE_SIZE);
        plugin.getLogger().info("Require Leaves: " + TreeFellerConfig.REQUIRE_LEAVES);
        plugin.getLogger().info("Minimum Leaves: " + TreeFellerConfig.MINIMUM_LEAVES);
        plugin.getLogger().info("Leaf Detect Range: " + TreeFellerConfig.LEAF_DETECT_RANGE);
        plugin.getLogger().info("Leaf Break Range: " + TreeFellerConfig.LEAF_BREAK_RANGE);
        plugin.getLogger().info("Diagonal Logs: " + TreeFellerConfig.DIAGONAL_LOGS);
        plugin.getLogger().info("Allow Player Placed: " + TreeFellerConfig.ALLOW_PLAYER_PLACED);
        plugin.getLogger().info("Replant Saplings: " + TreeFellerConfig.REPLANT_SAPLINGS);
        plugin.getLogger().info("Animation Enabled: " + TreeFellerConfig.ANIMATION_ENABLED);
        plugin.getLogger().info("Sounds Enabled: " + TreeFellerConfig.SOUNDS_ENABLED);
        plugin.getLogger().info("Configured Tools: " + TreeFellerConfig.TOOLS.size());
        plugin.getLogger().info("Configured Tree Types: " + TreeFellerConfig.TREE_TYPES.size());

        // List configured tools
        for (ToolConfig tool : TreeFellerConfig.TOOLS) {
            plugin.getLogger().info("  - Tool: " + tool.getName() + " (" + tool.getMaterial() +
                    ", durability cost: " + tool.getDurabilityCost() + ")");
        }

        // List configured tree types
        for (TreeType tree : TreeFellerConfig.TREE_TYPES) {
            plugin.getLogger().info("  - Tree: " + tree.getName() + " (" + tree.getLogBlock() +
                    " -> " + tree.getLeafBlock() + ", max: " + tree.getMaxHeight() +
                    ", required leaves: " + tree.getRequiredLeaves() + ")");
        }

        plugin.getLogger().info("================================");
    }

    /**
     * Gets the event listener.
     *
     * @return the event listener
     */
    public TreeFellerListener getListener() {
        return listener;
    }
}
