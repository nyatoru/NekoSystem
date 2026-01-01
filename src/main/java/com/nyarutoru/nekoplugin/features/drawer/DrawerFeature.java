package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import org.bukkit.event.HandlerList;

/**
 * Drawer storage feature - craft and place drawers to store large quantities of
 * a single item type.
 */
public class DrawerFeature implements Feature {

    public static final String ID = "drawer";
    public static final String NAME = "Drawer Storage";

    private boolean enabled = false;
    private DrawerListener drawerListener;

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
        DrawerManager.getInstance().initialize(plugin);

        drawerListener = new DrawerListener();
        plugin.getServer().getPluginManager().registerEvents(drawerListener, plugin);

        new DrawerRecipes().registerAll();

        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (drawerListener != null) {
            HandlerList.unregisterAll(drawerListener);
        }
        DrawerManager.getInstance().shutdown();
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
