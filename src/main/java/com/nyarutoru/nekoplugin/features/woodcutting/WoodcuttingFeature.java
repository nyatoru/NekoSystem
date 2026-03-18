package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Woodcutting feature - enhanced wood processing using stonecutter.
 * Allows converting logs to all wood-related items in the stonecutter:
 * planks, stairs, slabs, fences, doors, trapdoors, pressure plates, buttons, signs.
 */
public class WoodcuttingFeature extends AbstractFeature {

    private WoodOnStoneCutter woodOnStoneCutter;

    public WoodcuttingFeature() {
        super("woodcutting", "Woodcutting");
    }

    @Override
    protected void onEnable() {
        NekoPlugin plugin = NekoPlugin.getInstance();
        
        // Register stonecutter recipes
        woodOnStoneCutter = new WoodOnStoneCutter(plugin);
        woodOnStoneCutter.registerRecipes();

        plugin.getLogger().info("Woodcutting feature enabled");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (woodOnStoneCutter != null) {
            woodOnStoneCutter.removeRecipes();
        }
    }
}
