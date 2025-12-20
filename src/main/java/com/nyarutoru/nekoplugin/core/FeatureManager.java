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

    private static FeatureManager instance;
    private final Map<String, Feature> features = new HashMap<>();
    private NekoPlugin plugin;

    private FeatureManager() {
    }

    public static FeatureManager getInstance() {
        if (instance == null) {
            instance = new FeatureManager();
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
        plugin.getLogger().info("Registered feature: " + feature.getName());
    }

    /**
     * Enables all registered features.
     */
    public void enableAll() {
        for (Feature feature : features.values()) {
            try {
                feature.onEnable(plugin);
                plugin.getLogger().info("Enabled feature: " + feature.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable feature: " + feature.getName(), e);
            }
        }
    }

    /**
     * Disables all registered features.
     */
    public void disableAll() {
        for (Feature feature : features.values()) {
            try {
                feature.onDisable();
                plugin.getLogger().info("Disabled feature: " + feature.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable feature: " + feature.getName(), e);
            }
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
