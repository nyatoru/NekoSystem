package com.nyarutoru.nekoplugin.features.furnace;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Upgrade Furnace feature - craft a tiered furnace that smelts up to 9x faster.
 */
public class FurnaceFeature extends AbstractFeature {
    private FurnaceRecipes recipes;

    public FurnaceFeature() {
        super("furnace", "Upgrade Furnace");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        FurnaceManager.getInstance().start();
        registerListener(new FurnaceListener(), plugin);
        recipes = new FurnaceRecipes();
        recipes.registerAll();
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
        FurnaceManager.getInstance().stop();
    }
}
