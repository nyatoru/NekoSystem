package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Player Feature - integrated player quality-of-life improvements.
 * Includes: Pet Carrying, AFK System, Auto Item Replenishment
 */
public class PlayerFeature implements Feature {

    public static final String ID = "player";
    public static final String NAME = "Player Utilities";

    private boolean enabled = false;
    private PlayerFeatureListener listener;

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
        listener = new PlayerFeatureListener(plugin);
        listener.start();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        this.enabled = true;
        plugin.getLogger().info("Player feature enabled (Pet Carry, AFK, Auto-Replenish).");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.stop();
            HandlerList.unregisterAll(listener);
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public PlayerFeatureListener getListener() {
        return listener;
    }
}
