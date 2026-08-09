package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

public final class GravesFeature extends AbstractFeature {
    private GraveManager manager;
    private GraveCommands commands;
    private boolean commandsRegistered;
    private long graveLifetimeMillis = GraveConfig.DEFAULT_GRAVE_LIFETIME_MS;
    private long checkIntervalTicks = GraveConfig.DEFAULT_GRAVE_CHECK_INTERVAL_TICKS;
    private int maxGravesPerPlayer = GraveConfig.DEFAULT_MAX_GRAVES_PER_PLAYER;
    private int safeLocationSearchRadius = GraveConfig.DEFAULT_MAX_SAFE_LOCATION_SEARCH_RADIUS;
    private long displayUpdateIntervalTicks = GraveConfig.DEFAULT_DISPLAY_UPDATE_INTERVAL_TICKS;

    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.longValue("future-grave-lifetime-minutes", "Future grave lifetime (minutes)", 20L,
            1L, 10080L, ApplySemantics.FUTURE_ONLY, value -> { graveLifetimeMillis = value * 60_000L; if (manager != null) manager.setGraveLifetimeMillis(graveLifetimeMillis); }));
        register(registry, state, SettingDescriptor.integer("safe-search-radius", "Safe grave search radius (blocks)",
            GraveConfig.DEFAULT_MAX_SAFE_LOCATION_SEARCH_RADIUS, 0, 32, ApplySemantics.IMMEDIATE,
            value -> { safeLocationSearchRadius = value; if (manager != null) manager.setSafeLocationSearchRadius(safeLocationSearchRadius); }));
        register(registry, state, SettingDescriptor.integer("max-graves-per-player", "Maximum active graves per player",
            GraveConfig.DEFAULT_MAX_GRAVES_PER_PLAYER, 1, 100, ApplySemantics.IMMEDIATE,
            value -> { maxGravesPerPlayer = value; if (manager != null) manager.setMaxGravesPerPlayer(maxGravesPerPlayer); }));
        register(registry, state, SettingDescriptor.longValue("expiry-check-interval-seconds", "Grave expiry check interval (seconds)", 60L,
            1L, 3600L, ApplySemantics.RESCHEDULE,
            value -> { checkIntervalTicks = value * 20L; if (manager != null) manager.setCheckIntervalTicks(checkIntervalTicks); }));
        register(registry, state, SettingDescriptor.longValue("display-update-interval-seconds", "Grave display update interval (seconds)", 1L,
            1L, 3600L, ApplySemantics.RESCHEDULE,
            value -> { displayUpdateIntervalTicks = value * 20L; if (manager != null) manager.setDisplayUpdateIntervalTicks(displayUpdateIntervalTicks); }));
    }

    public GravesFeature() {
        super("graves", "Grave");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        manager = new GraveManager(plugin);
        manager.setGraveLifetimeMillis(graveLifetimeMillis);
        manager.setCheckIntervalTicks(checkIntervalTicks);
        manager.setMaxGravesPerPlayer(maxGravesPerPlayer);
        manager.setSafeLocationSearchRadius(safeLocationSearchRadius);
        manager.setDisplayUpdateIntervalTicks(displayUpdateIntervalTicks);
        if (!manager.start()) throw new IllegalStateException("Grave persistence could not be initialized");
        try {
            if (commands == null) commands = new GraveCommands(() -> manager);
            if (!commandsRegistered) {
                plugin.registerCommand("grave", "Lists your active graves", java.util.List.of("graves"), commands.playerCommand());
                plugin.registerCommand("graveadmin", "Manages active graves", commands.adminCommand());
                commandsRegistered = true;
            }
            registerListener(new GraveListener(manager), plugin);
            super.onEnable(plugin);
        } catch (RuntimeException exception) {
            cleanup();
            throw exception;
        }
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

    @Override
    protected void cleanup() {
        if (manager != null) manager.stop();
        manager = null;
    }

    public GraveManager getGraveManager() { return manager; }
}
