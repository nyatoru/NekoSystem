package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Woodcutting feature - enhanced wood processing.
 * Includes:
 * - Stonecutter wood recipes (logs to planks, stairs, slabs, etc.)
 * - Log placement on stonecutter converts to planks
 */
public class WoodcuttingFeature implements Feature {

    public static final String ID = "woodcutting";
    public static final String NAME = "Woodcutting";

    private boolean enabled = false;
    private WoodOnStoneCutter woodOnStoneCutter;
    private WoodPlacingListener woodPlacingListener;

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
        // Save default config if not exists
        plugin.saveResource("woodcutting.yml", false);
        
        // Reload config to load woodcutting.yml values
        plugin.reloadConfig();

        // Register stonecutter recipes
        woodOnStoneCutter = new WoodOnStoneCutter(plugin);
        woodOnStoneCutter.registerRecipes();

        // Register listener for log placement on stonecutter
        woodPlacingListener = new WoodPlacingListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(woodPlacingListener, plugin);

        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (woodPlacingListener != null) {
            HandlerList.unregisterAll(woodPlacingListener);
        }
        if (woodOnStoneCutter != null) {
            woodOnStoneCutter.removeRecipes();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
