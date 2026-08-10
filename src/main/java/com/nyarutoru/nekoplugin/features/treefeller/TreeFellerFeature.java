package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.features.treefeller.tree.TreeType;
import com.nyarutoru.nekoplugin.features.treefeller.tool.ToolConfig;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TreeFellerFeature extends AbstractFeature {

    private TreeFellerListener listener;
    private NekoPlugin plugin;
    private final Set<SchedulerUtils.TaskHandle> tasks = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new TreeFellerFeature.
     */
    public TreeFellerFeature() {
        super("treefeller", "TreeFeller");
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.bool("debug", "Debug messages", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.DEBUG = value));
        register(registry, state, SettingDescriptor.integer("max-tree-size", "Maximum tree size", 1500, 1, 10000, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.MAX_TREE_SIZE = value));
        register(registry, state, SettingDescriptor.bool("require-leaves", "Require leaves", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.REQUIRE_LEAVES = value));
        register(registry, state, SettingDescriptor.integer("max-height-from-bottom", "Maximum cut height from bottom", 5, 1, 64, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.MAX_HEIGHT_FROM_BOTTOM = value));
        register(registry, state, SettingDescriptor.integer("required-logs", "Required logs", 4, 1, 100, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.REQUIRED_LOGS = value));
        register(registry, state, SettingDescriptor.doubleValue("minimum-vertical-log-ratio", "Minimum vertical log ratio", 0.5, 0.0, 100.0, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.MIN_VERTICAL_LOG_RATIO = value));
        register(registry, state, SettingDescriptor.integer("leaf-detect-range", "Leaf detection range", 6, 1, 16, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.LEAF_DETECT_RANGE = value));
        register(registry, state, SettingDescriptor.bool("diagonal-leaves", "Include diagonal leaves", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.DIAGONAL_LEAVES = value));
        register(registry, state, SettingDescriptor.bool("secondary-tree-verification", "Secondary tree verification", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.SECONDARY_TREE_VERIFICATION = value));
        register(registry, state, SettingDescriptor.bool("ignore-parallel-trunk-pillars", "Ignore parallel trunk pillars", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.IGNORE_PARALLEL_TRUNK_PILLARS = value));
        register(registry, state, SettingDescriptor.bool("use-leaf-distance", "Use leaf distance", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.USE_LEAF_DISTANCE = value));
        register(registry, state, SettingDescriptor.bool("fast-leaf-decay", "Fast leaf decay", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.FAST_LEAF_DECAY_ENABLED = value));
        registerFastLeafDecayDelaySettings(registry, state);
        register(registry, state, SettingDescriptor.bool("individual-tree-detection", "Individual tree detection (isolate overlapping canopies)", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.INDIVIDUAL_TREE_DETECTION = value));
        register(registry, state, SettingDescriptor.integer("individual-detection-range", "Individual detection range", 6, 1, 16, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.INDIVIDUAL_DETECTION_RANGE = value));
        register(registry, state, SettingDescriptor.bool("allow-irregular-growth", "Allow irregular/bent tree shapes (acacia, cherry, pale oak)", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ALLOW_IRREGULAR_GROWTH = value));
        register(registry, state, SettingDescriptor.bool("async-detection", "Async tree detection (Paper async, Folia region)", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ASYNC_DETECTION = value));
        register(registry, state, SettingDescriptor.bool("ignore-leaf-data", "Ignore leaf data", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.IGNORE_LEAF_DATA = value));
        register(registry, state, SettingDescriptor.bool("allow-player-placed", "Allow player-placed logs", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ALLOW_PLAYER_PLACED = value));
        register(registry, state, SettingDescriptor.bool("replant-saplings", "Replant saplings", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.REPLANT_SAPLINGS = value));
        register(registry, state, SettingDescriptor.doubleValue("replant-chance", "Sapling replant chance", 1.0, 0.0, 1.0, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.REPLANT_CHANCE = value));
        register(registry, state, SettingDescriptor.bool("cascade", "Cascade connected trees", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.CASCADE = value));
        register(registry, state, SettingDescriptor.bool("allow-partial", "Allow partial large-tree felling", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ALLOW_PARTIAL = value));
        register(registry, state, SettingDescriptor.integer("root-distance", "Mangrove root search distance", 6, 1, 32, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ROOT_DISTANCE = value));
        register(registry, state, SettingDescriptor.bool("animation-enabled", "Felling animation", false, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ANIMATION_ENABLED = value));
        register(registry, state, SettingDescriptor.integer("animation-delay-ticks", "Animation delay (ticks)", 2, 0, 100, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ANIMATION_DELAY_TICKS = value));
        register(registry, state, SettingDescriptor.bool("animation-bottom-up", "Animation bottom-up", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.ANIMATION_BOTTOM_UP = value));
        register(registry, state, SettingDescriptor.bool("sounds-enabled", "Felling sounds", true, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.SOUNDS_ENABLED = value));
        register(registry, state, SettingDescriptor.string("fell-sound", "Felling sound", "minecraft:block.wood.break", ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.FELL_SOUND = TreeFellerConfig.resolveSound(value)));
        register(registry, state, SettingDescriptor.doubleValue("sound-volume", "Felling sound volume", 1.0, 0.0, 1.0, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.SOUND_VOLUME = value.floatValue()));
        register(registry, state, SettingDescriptor.doubleValue("sound-pitch", "Felling sound pitch", 1.0, 0.5, 2.0, ApplySemantics.IMMEDIATE, value -> TreeFellerConfig.SOUND_PITCH = value.floatValue()));
    }

    private void registerFastLeafDecayDelaySettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> minimum = SettingDescriptor.integer(
                "fast-leaf-decay-min-delay", "Fast leaf decay minimum delay (ticks)",
                20, 0, 1200, ApplySemantics.IMMEDIATE, TreeFellerConfig::setFastLeafDecayMinDelay);
        SettingDescriptor<Integer> maximum = SettingDescriptor.integer(
                "fast-leaf-decay-max-delay", "Fast leaf decay maximum delay (ticks)",
                100, 0, 2400, ApplySemantics.IMMEDIATE, TreeFellerConfig::setFastLeafDecayMaxDelay);
        registry.register(getId(), minimum);
        registry.register(getId(), maximum);

        int minimumValue = parseOrDefault(state, minimum);
        int maximumValue = parseOrDefault(state, maximum);
        if (minimumValue > maximumValue) {
            maximumValue = Math.max(minimumValue, maximum.defaultValue());
            if (maximumValue > 2400) {
                minimumValue = maximum.defaultValue();
                maximumValue = maximum.defaultValue();
            }
        }
        TreeFellerConfig.setFastLeafDecayDelays(minimumValue, maximumValue);
        state.setSettingValue(getId(), minimum.key(), minimum.format(minimumValue));
        state.setSettingValue(getId(), maximum.key(), maximum.format(maximumValue));
    }

    private <T> T parseOrDefault(AdminState state, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue(getId(), descriptor.key());
        if (stored == null) {
            return descriptor.defaultValue();
        }
        try {
            return descriptor.parse(stored);
        } catch (IllegalArgumentException ignored) {
            return descriptor.defaultValue();
        }
    }

    private <T> void register(SettingRegistry registry, AdminState state, SettingDescriptor<T> descriptor) {
        registry.register(getId(), descriptor);
        String stored = state.settingValue(getId(), descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            T fallback = descriptor.defaultValue();
            descriptor.apply(fallback);
            state.setSettingValue(getId(), descriptor.key(), descriptor.format(fallback));
        }
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        this.plugin = plugin;
        tasks.clear();

        // Register event listener
        listener = new TreeFellerListener(plugin, this::isEnabled, tasks::add);
        registerListener(listener, plugin);
        super.onEnable(plugin);
        plugin.getLogger().info("TreeFeller event listener registered");

        // Log configuration summary
        logConfiguration();

        plugin.getLogger().info("TreeFeller feature enabled successfully!");
    }

    @Override
    protected void cleanup() {
        tasks.forEach(SchedulerUtils::cancelTask);
        tasks.clear();
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
        plugin.getLogger().info("Fast Leaf Decay: " + TreeFellerConfig.FAST_LEAF_DECAY_ENABLED);
        plugin.getLogger().info("Diagonal Leaves: " + TreeFellerConfig.DIAGONAL_LEAVES);
        plugin.getLogger().info("Secondary Tree Verification: " + TreeFellerConfig.SECONDARY_TREE_VERIFICATION);
        plugin.getLogger().info("Individual Tree Detection: " + TreeFellerConfig.INDIVIDUAL_TREE_DETECTION + " (range " + TreeFellerConfig.INDIVIDUAL_DETECTION_RANGE + ")");
        plugin.getLogger().info("Allow Irregular Growth: " + TreeFellerConfig.ALLOW_IRREGULAR_GROWTH);
        plugin.getLogger().info("Async Detection: " + TreeFellerConfig.ASYNC_DETECTION);
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
