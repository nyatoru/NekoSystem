package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Graves Feature - Creates graves when players die to store their items.
 * Players can retrieve items by right-clicking their grave.
 * 
 * Features:
 * - Player head grave markers
 * - 20-minute grave lifetime
 * - Safe location placement
 * - OP bypass protection
 * - Database persistence
 * - Multiple graves per player (max 3)
 */
public class GravesFeature extends AbstractFeature {

    private NekoPlugin plugin;
    private GraveManager graveManager;
    private GraveCommands commands;

    public GravesFeature() {
        super("graves", "Graves");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        this.plugin = plugin;
        
        // Initialize grave manager
        graveManager = GraveManager.init(plugin);
        graveManager.start();
        
        // Register listener
        registerListener(new GraveListener(plugin, graveManager), plugin);
        
        // Register commands
        commands = new GraveCommands(plugin, graveManager);
        commands.register();
        
        super.onEnable(plugin);
        
        plugin.getLogger().info("Graves feature enabled - Players will be buried with their items");
    }

    @Override
    protected void cleanup() {
        if (graveManager != null) {
            graveManager.stop();
        }
        
        if (commands != null) {
            commands.unregister();
        }
        
        if (plugin != null) {
            plugin.getLogger().info("Graves feature disabled");
        }
    }

    /**
     * Gets the grave manager instance.
     *
     * @return The grave manager
     */
    public GraveManager getGraveManager() {
        return graveManager;
    }
}
