package com.nyarutoru.nekoplugin.core;

/**
 * Core plugin manager for global initialization.
 */
public class PluginManager {

    private static PluginManager instance;

    private PluginManager() {
    }

    public static PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }

    /**
     * Initialize core plugin systems.
     */
    public void initialize() {
        // Initialize core managers here
    }
}
