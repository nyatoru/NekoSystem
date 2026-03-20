package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;

/**
 * Drawer storage feature - craft and place drawers to store large quantities of
 * a single item type.
 */
public class DrawerFeature extends AbstractFeature {

    public DrawerFeature() {
        super("drawer", "Drawer Storage");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        DrawerManager.getInstance().initialize(plugin);

        registerListener(new DrawerListener(), plugin);
        new DrawerRecipes().registerAll();

        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        DrawerManager.getInstance().shutdown();
    }
}
