package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Woodcutting feature - enhanced wood processing using stonecutter.
 * Allows converting logs to all wood-related items in the stonecutter:
 * planks, stairs, slabs, fences, doors, trapdoors, pressure plates, buttons, signs.
 * 
 * Prevents duplicate recipe registration by tracking processed wood types.
 */
public class WoodcuttingFeature extends AbstractFeature {

    public WoodcuttingFeature() {
        super("woodcutting", "Woodcutting");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        WoodOnStoneCutter woodOnStoneCutter = new WoodOnStoneCutter(plugin);
        woodOnStoneCutter.registerRecipes();
        super.onEnable(plugin);
        plugin.getLogger().info("Woodcutting feature enabled");
    }
}
