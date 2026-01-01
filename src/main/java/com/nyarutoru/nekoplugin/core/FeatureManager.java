package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all plugin features.
 * Handles registration, enabling, and disabling of features.
 */
public class FeatureManager {

    private static volatile FeatureManager instance;
    private final Map<String, Feature> features = new java.util.concurrent.ConcurrentHashMap<>();
    private NekoPlugin plugin;

    private FeatureManager() {
    }

    public static FeatureManager getInstance() {
        if (instance == null) {
            synchronized (FeatureManager.class) {
                if (instance == null) {
                    instance = new FeatureManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the manager with plugin reference.
     */
    public void initialize(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a feature.
     */
    public void registerFeature(Feature feature) {
        if (features.containsKey(feature.getId())) {
            plugin.getLogger().warning("Feature already registered: " + feature.getId());
            return;
        }
        features.put(feature.getId(), feature);
    }

    /**
     * Enables all registered features.
     */
    public void enableAll() {
        int enabled = 0;
        int failed = 0;

        for (Feature feature : features.values()) {
            try {
                feature.onEnable(plugin);
                enabled++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable feature: " + feature.getName(), e);
                failed++;
            }
        }

        // Summary log
        if (failed == 0) {
            plugin.getLogger().info(String.format("✓ Enabled %d features successfully", enabled));
        } else {
            plugin.getLogger().warning(String.format("Enabled %d features (%d failed)", enabled, failed));
        }
    }

    /**
     * Disables all registered features.
     */
    public void disableAll() {
        int disabled = 0;
        int failed = 0;

        for (Feature feature : features.values()) {
            try {
                feature.onDisable();
                disabled++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable feature: " + feature.getName(), e);
                failed++;
            }
        }

        // Summary log
        if (failed == 0) {
            plugin.getLogger().info(String.format("✓ Disabled %d features successfully", disabled));
        } else {
            plugin.getLogger().warning(String.format("Disabled %d features (%d failed)", disabled, failed));
        }
    }

    /**
     * Gets a feature by its ID.
     */
    public Feature getFeature(String id) {
        return features.get(id);
    }

    /**
     * Gets all registered features.
     */
    public Map<String, Feature> getAllFeatures() {
        return new HashMap<>(features);
    }

    /**
     * Gets the number of registered features.
     */
    public int getFeatureCount() {
        return features.size();
    }
}
