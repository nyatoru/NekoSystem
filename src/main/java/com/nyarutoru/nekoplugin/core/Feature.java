package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;

/**
 * Interface for plugin features.
 * Each feature should implement this interface to be managed by FeatureManager.
 */
public interface Feature {

    /**
     * Gets the unique identifier for this feature.
     */
    String getId();

    /**
     * Gets the display name for this feature.
     */
    String getName();

    /**
     * Called when the feature is enabled.
     * Register listeners, commands, and initialize resources here.
     */
    void onEnable(NekoPlugin plugin);

    /**
     * Called when the feature is disabled.
     * Unregister listeners, save data, and cleanup resources here.
     */
    void onDisable();

    /**
     * Checks if this feature is currently enabled.
     */
    boolean isEnabled();
}
