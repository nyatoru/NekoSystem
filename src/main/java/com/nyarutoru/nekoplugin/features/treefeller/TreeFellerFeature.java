package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Tree Feller feature - chop entire trees with one swing.
 * Uses ActiveToolAPI for shift activation.
 */
public class TreeFellerFeature implements Feature {

    public static final String ID = "tree_feller";
    public static final String NAME = "Tree Feller";

    private boolean enabled = false;
    private TreeFellerListener listener;
    private FastLeafDecayListener leafDecayListener;

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
        listener = new TreeFellerListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Register fast leaf decay for ALL trees
        leafDecayListener = new FastLeafDecayListener();
        plugin.getServer().getPluginManager().registerEvents(leafDecayListener, plugin);

        this.enabled = true;
        plugin.getLogger().info("Tree Feller feature enabled with fast leaf decay.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (leafDecayListener != null) {
            HandlerList.unregisterAll(leafDecayListener);
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
