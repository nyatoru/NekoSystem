package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;

/**
 * Server Feature - server-side optimizations and management.
 * Includes: Pillager cluster management
 */
public class ServerFeature implements Feature {

    public static final String ID = "server";
    public static final String NAME = "Server Utilities";

    private boolean enabled = false;
    private PillagerManager pillagerManager;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        // Pillager management
        pillagerManager = new PillagerManager(plugin);
        pillagerManager.start();

        this.enabled = true;
        plugin.getLogger().info("Server feature enabled (Pillager Management).");
    }

    @Override
    public void onDisable() {
        if (pillagerManager != null) {
            pillagerManager.stop();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
