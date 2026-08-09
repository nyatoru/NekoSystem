package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Player Feature - integrated player quality-of-life improvements.
 * Includes: AFK System, Auto Item Replenishment
 */
public class PlayerFeature extends AbstractFeature {

    public PlayerFeature() {
        super("player", "Player Utilities");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        PlayerFeatureListener listener = new PlayerFeatureListener(plugin);
        listener.start();
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }
}
