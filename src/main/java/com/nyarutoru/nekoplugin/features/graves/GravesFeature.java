package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

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
public class GravesFeature implements Feature {

    public static final String ID = "graves";
    public static final String NAME = "Graves";

    private boolean enabled = false;
    private NekoPlugin plugin;
    private GraveListener listener;
    private GraveManager graveManager;
    private GraveCommands commands;

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
        this.plugin = plugin;
        
        // Initialize grave manager
        graveManager = GraveManager.init(plugin);
        graveManager.start();
        
        // Register listener
        listener = new GraveListener(plugin, graveManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        
        // Register commands
        commands = new GraveCommands(plugin, graveManager);
        commands.register();
        
        this.enabled = true;
        
        plugin.getLogger().info("Graves feature enabled - Players will be buried with their items");
    }

    @Override
    public void onDisable() {
        if (graveManager != null) {
            graveManager.stop();
        }
        
        if (commands != null) {
            commands.unregister();
        }
        
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        
        this.enabled = false;
        
        if (plugin != null) {
            plugin.getLogger().info("Graves feature disabled");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
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
