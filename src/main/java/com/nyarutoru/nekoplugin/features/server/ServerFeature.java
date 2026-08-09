package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Material;

import java.util.List;

/**
 * Server Feature - server-side optimizations and management.
 * Includes: Pillager management, Concrete converter, Custom Crafting Table,
 * Block Interactions
 */
public class ServerFeature extends AbstractFeature {

    private static final int DEFAULT_CONCRETE_DELAY_SECONDS = 10;
    private static final int DEFAULT_CONCRETE_SCAN_SECONDS = 1;
    private static final int DEFAULT_LAG_CHECK_SECONDS = 10;
    private static final int DEFAULT_MAP_WINDOW_SECONDS = 10;
    private static final int DEFAULT_MAP_COOLDOWN_MINUTES = 5;
    private static final int DEFAULT_MAP_RADIUS = 128;
    private static final int DEFAULT_MAP_RETENTION_MINUTES = 1;
    private ConcreteConverter concreteConverter;
    private CustomCraftingListener customCraftingListener;
    private ServerEventsListener serverEventsListener;
    private TPSBossBarTask tpsTask;
    private SchedulerUtils.TaskHandle tpsTaskHandle;
    private int concreteDelaySeconds = DEFAULT_CONCRETE_DELAY_SECONDS;
    private int concreteScanSeconds = DEFAULT_CONCRETE_SCAN_SECONDS;
    private int lagCheckSeconds = DEFAULT_LAG_CHECK_SECONDS;
    private int mapWindowSeconds = DEFAULT_MAP_WINDOW_SECONDS;
    private int mapCooldownMinutes = DEFAULT_MAP_COOLDOWN_MINUTES;
    private int mapRadius = DEFAULT_MAP_RADIUS;
    private int mapRetentionMinutes = DEFAULT_MAP_RETENTION_MINUTES;
    private List<Material> deepslateBlocks = List.copyOf(ServerEventsListener.DEFAULT_DEEPSLATE_BLOCKS);
    private List<Material> glassBlocks = List.copyOf(ServerEventsListener.DEFAULT_GLASS_BLOCKS);
    private boolean tpsEnabled = true;
    private int minEfficiency = 5;
    private int minHasteAmplifier = 1;
    private boolean instantBreakEnabled = true;
    private boolean ladderEnabled = true;
    private boolean anvilRepairEnabled = true;
    private boolean joinMessagesEnabled = true;
    private boolean quitMessagesEnabled = true;
    private boolean lagNotificationsEnabled = true;
    private boolean lagBroadcastEnabled = true;
    private boolean lagOpDetailsEnabled = true;
    private boolean lagConsoleLoggingEnabled = true;
    private int minLagPlayers = 3;
    private double mapLagTps = 18.0;
    private String joinMessage = "<green><bold>+</bold> <gray>{player} joined the server.";
    private String quitMessage = "<red><bold>-</bold> <gray>{player} left the server.";
    private String anvilMessage = "✓ Anvil repaired!";
    private double tpsGood = 18.0;
    private double tpsWarning = 15.0;
    private double msptGood = 40.0;
    private double msptWarning = 50.0;
    private double cpuGood = 60.0;
    private double cpuWarning = 80.0;
    private ServerRecipes serverRecipes;

    public ServerFeature() {
        super("server", "Server Utilities");
    }

    /** Registers safe server settings and applies persisted values before enablement. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.integer("concrete-conversion-seconds", "Concrete conversion delay (seconds)", DEFAULT_CONCRETE_DELAY_SECONDS, 1, 3600, ApplySemantics.IMMEDIATE, value -> { concreteDelaySeconds = value; if (concreteConverter != null) concreteConverter.setConvertTimeSeconds(value); }));
        register(registry, state, SettingDescriptor.integer("concrete-scan-seconds", "Concrete scan interval (seconds)", DEFAULT_CONCRETE_SCAN_SECONDS, 1, 60, ApplySemantics.RESCHEDULE, value -> { concreteScanSeconds = value; if (concreteConverter != null) concreteConverter.setCheckIntervalSeconds(value); }));

        register(registry, state, SettingDescriptor.integer("instant-break-efficiency", "Instant break minimum Efficiency", 5, 1, 10, ApplySemantics.IMMEDIATE, value -> { minEfficiency = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.integer("instant-break-haste-amplifier", "Instant break minimum Haste amplifier", 1, 0, 10, ApplySemantics.IMMEDIATE, value -> { minHasteAmplifier = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("instant-break-enabled", "Instant break enabled", true, ApplySemantics.IMMEDIATE, value -> { instantBreakEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("ladder-placement-enabled", "Ladder auto-placement enabled", true, ApplySemantics.IMMEDIATE, value -> { ladderEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("anvil-repair-enabled", "Anvil repair enabled", true, ApplySemantics.IMMEDIATE, value -> { anvilRepairEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("join-messages-enabled", "Join messages enabled", true, ApplySemantics.IMMEDIATE, value -> { joinMessagesEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("quit-messages-enabled", "Quit messages enabled", true, ApplySemantics.IMMEDIATE, value -> { quitMessagesEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.string("join-message", "Join message ({player} placeholder)", joinMessage, ApplySemantics.IMMEDIATE, value -> { joinMessage = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.string("quit-message", "Quit message ({player} placeholder)", quitMessage, ApplySemantics.IMMEDIATE, value -> { quitMessage = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.string("anvil-repair-message", "Anvil repair message", anvilMessage, ApplySemantics.IMMEDIATE, value -> { anvilMessage = value; applyListenerSettings(); }));

        register(registry, state, SettingDescriptor.bool("map-lag-notifications-enabled", "Map lag notifications enabled", true, ApplySemantics.IMMEDIATE, value -> { lagNotificationsEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("map-lag-broadcast-enabled", "Map lag broadcast enabled", true, ApplySemantics.IMMEDIATE, value -> { lagBroadcastEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("map-lag-op-details-enabled", "Map lag OP details enabled", true, ApplySemantics.IMMEDIATE, value -> { lagOpDetailsEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.bool("map-lag-console-logging-enabled", "Map lag console logging enabled", true, ApplySemantics.IMMEDIATE, value -> { lagConsoleLoggingEnabled = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.integer("map-lag-check-seconds", "Map lag check interval (seconds)", DEFAULT_LAG_CHECK_SECONDS, 1, 60, ApplySemantics.RESCHEDULE, value -> { lagCheckSeconds = value; if (serverEventsListener != null) serverEventsListener.setLagCheckIntervalSeconds(value); }));
        register(registry, state, SettingDescriptor.doubleValue("map-lag-tps-threshold", "Map lag TPS threshold", 18.0, 1.0, 20.0, ApplySemantics.IMMEDIATE, value -> { mapLagTps = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.integer("map-lag-min-players", "Map lag minimum players", 3, 1, 100, ApplySemantics.IMMEDIATE, value -> { minLagPlayers = value; applyListenerSettings(); }));
        register(registry, state, SettingDescriptor.integer("map-lag-window-seconds", "Map lag scan window (seconds)", DEFAULT_MAP_WINDOW_SECONDS, 1, 60, ApplySemantics.IMMEDIATE, value -> { mapWindowSeconds = value; if (serverEventsListener != null) serverEventsListener.getTracker().setTimeWindowSeconds(value); }));
        register(registry, state, SettingDescriptor.integer("map-lag-cooldown-minutes", "Map lag notification cooldown (minutes)", DEFAULT_MAP_COOLDOWN_MINUTES, 1, 60, ApplySemantics.IMMEDIATE, value -> { mapCooldownMinutes = value; if (serverEventsListener != null) serverEventsListener.getTracker().setCooldownMinutes(value); }));
        register(registry, state, SettingDescriptor.integer("map-lag-radius", "Map lag attribution radius (blocks)", DEFAULT_MAP_RADIUS, 1, 512, ApplySemantics.IMMEDIATE, value -> { mapRadius = value; if (serverEventsListener != null) serverEventsListener.getTracker().setTrackingRadius(value); }));
        register(registry, state, SettingDescriptor.integer("map-lag-retention-minutes", "Map lag data retention (minutes)", DEFAULT_MAP_RETENTION_MINUTES, 1, 60, ApplySemantics.IMMEDIATE, value -> { mapRetentionMinutes = value; if (serverEventsListener != null) serverEventsListener.getTracker().setDataRetentionMinutes(value); }));
        register(registry, state, SettingDescriptor.materials("deepslate-materials", "Instant-break deepslate materials", List.copyOf(deepslateBlocks), ApplySemantics.IMMEDIATE, value -> { deepslateBlocks = List.copyOf(value); if (serverEventsListener != null) serverEventsListener.setDeepslateBlocks(value); }));
        register(registry, state, SettingDescriptor.materials("glass-materials", "Instant-break glass materials", List.copyOf(glassBlocks), ApplySemantics.IMMEDIATE, value -> { glassBlocks = List.copyOf(value); if (serverEventsListener != null) serverEventsListener.setGlassBlocks(value); }));

        register(registry, state, SettingDescriptor.bool("tps-boss-bar-enabled", "TPS boss bar enabled", true, ApplySemantics.IMMEDIATE, value -> { tpsEnabled = value; if (tpsTask != null) tpsTask.setEnabled(value); }));
        register(registry, state, SettingDescriptor.doubleValue("tps-good-threshold", "TPS good threshold", 18.0, 1.0, 20.0, ApplySemantics.IMMEDIATE, value -> configureTps(value, null, null, null, null, null)));
        register(registry, state, SettingDescriptor.doubleValue("tps-warning-threshold", "TPS warning threshold", 15.0, 1.0, 20.0, ApplySemantics.IMMEDIATE, value -> configureTps(null, value, null, null, null, null)));
        register(registry, state, SettingDescriptor.doubleValue("mspt-good-threshold", "MSPT good threshold", 40.0, 1.0, 1000.0, ApplySemantics.IMMEDIATE, value -> configureTps(null, null, value, null, null, null)));
        register(registry, state, SettingDescriptor.doubleValue("mspt-warning-threshold", "MSPT warning threshold", 50.0, 1.0, 1000.0, ApplySemantics.IMMEDIATE, value -> configureTps(null, null, null, value, null, null)));
        register(registry, state, SettingDescriptor.doubleValue("cpu-good-threshold", "CPU good threshold", 60.0, 1.0, 100.0, ApplySemantics.IMMEDIATE, value -> configureTps(null, null, null, null, value, null)));
        register(registry, state, SettingDescriptor.doubleValue("cpu-warning-threshold", "CPU warning threshold", 80.0, 1.0, 100.0, ApplySemantics.IMMEDIATE, value -> configureTps(null, null, null, null, null, value)));
    }

    private <T> void register(SettingRegistry registry, AdminState state, SettingDescriptor<T> descriptor) {
        registry.register(getId(), descriptor);
        String stored = state.settingValue(getId(), descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    private void applyListenerSettings() {
        if (serverEventsListener != null) {
            serverEventsListener.configure(minEfficiency, minHasteAmplifier, instantBreakEnabled, ladderEnabled,
                    anvilRepairEnabled, joinMessagesEnabled, quitMessagesEnabled, lagNotificationsEnabled,
                    lagBroadcastEnabled, lagOpDetailsEnabled, lagConsoleLoggingEnabled, minLagPlayers, mapLagTps,
                    joinMessage, quitMessage, anvilMessage);
        }
    }

    private void configureTps(Double tpsGood, Double tpsWarning, Double msptGood, Double msptWarning, Double cpuGood, Double cpuWarning) {
        double nextTpsGood = tpsGood == null ? this.tpsGood : tpsGood;
        double nextTpsWarning = tpsWarning == null ? this.tpsWarning : tpsWarning;
        double nextMsptGood = msptGood == null ? this.msptGood : msptGood;
        double nextMsptWarning = msptWarning == null ? this.msptWarning : msptWarning;
        double nextCpuGood = cpuGood == null ? this.cpuGood : cpuGood;
        double nextCpuWarning = cpuWarning == null ? this.cpuWarning : cpuWarning;
        if (nextTpsWarning > nextTpsGood) throw new IllegalArgumentException("TPS warning threshold must not exceed good threshold");
        if (nextMsptWarning < nextMsptGood) throw new IllegalArgumentException("MSPT warning threshold must not be below good threshold");
        if (nextCpuWarning < nextCpuGood) throw new IllegalArgumentException("CPU warning threshold must not be below good threshold");
        if (tpsTask != null) tpsTask.configure(nextTpsGood, nextTpsWarning, nextMsptGood, nextMsptWarning, nextCpuGood, nextCpuWarning);
        this.tpsGood = nextTpsGood;
        this.tpsWarning = nextTpsWarning;
        this.msptGood = nextMsptGood;
        this.msptWarning = nextMsptWarning;
        this.cpuGood = nextCpuGood;
        this.cpuWarning = nextCpuWarning;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        // Concrete conversion
        concreteConverter = new ConcreteConverter();
        concreteConverter.setConvertTimeSeconds(concreteDelaySeconds);
        concreteConverter.setCheckIntervalSeconds(concreteScanSeconds);
        ownTask(concreteConverter.start());

        // Custom Crafting Table
        customCraftingListener = new CustomCraftingListener(plugin);
        registerListener(customCraftingListener, plugin);
        registerListener(customCraftingListener.getRecipeBookGUI(), plugin);
        // RecipePreviewGUI now uses GuiAPI so no separate listener registration needed

        // Server Events (join/quit, instant break, ladders, anvil repair, lag notification)
        serverEventsListener = new ServerEventsListener(plugin);
        serverEventsListener.start();
        serverEventsListener.setLagCheckIntervalSeconds(lagCheckSeconds);
        serverEventsListener.setDeepslateBlocks(deepslateBlocks);
        serverEventsListener.setGlassBlocks(glassBlocks);
        applyListenerSettings();
        serverEventsListener.getTracker().setTimeWindowSeconds(mapWindowSeconds);
        serverEventsListener.getTracker().setCooldownMinutes(mapCooldownMinutes);
        serverEventsListener.getTracker().setTrackingRadius(mapRadius);
        serverEventsListener.getTracker().setDataRetentionMinutes(mapRetentionMinutes);
        registerListener(serverEventsListener, plugin);

        // TPS BossBar - run every second (20 ticks)
        tpsTask = new TPSBossBarTask();
        tpsTask.setEnabled(tpsEnabled);
        tpsTask.configure(tpsGood, tpsWarning, msptGood, msptWarning, cpuGood, cpuWarning);
        tpsTaskHandle = ownTask(SchedulerUtils.runGlobalTimerTask(tpsTask::run, 20L, 20L));

        // Server Recipes (furnace recipes, etc.)
        serverRecipes = new ServerRecipes(plugin);
        serverRecipes.registerAll();

        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (concreteConverter != null) {
            concreteConverter.stop();
        }
        if (serverEventsListener != null) {
            serverEventsListener.stop();
            serverEventsListener = null;
        }
        if (customCraftingListener != null) {
            customCraftingListener.cleanup();
            customCraftingListener.getRecipeBookGUI().cleanup();
            customCraftingListener = null;
        }
        if (tpsTask != null) {
            tpsTask.cleanup();
            tpsTask = null;
        }
        if (serverRecipes != null) {
            serverRecipes.unregisterAll();
            serverRecipes = null;
        }
        tpsTaskHandle = null;
    }
}
