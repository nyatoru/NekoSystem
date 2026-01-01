package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Sand Excavation Feature - mass mine sand and gravel with shovels.
 */
public class SandExcavationFeature implements Feature {

    public static final String ID = "sand_excavation";
    public static final String NAME = "Sand Excavation";

    private boolean enabled = false;
    private SandExcavationListener listener;

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
        listener = new SandExcavationListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
