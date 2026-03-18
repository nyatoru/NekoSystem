package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Woodcutting feature - enhanced wood processing using stonecutter.
 * Allows converting logs to all wood-related items in the stonecutter:
 * planks, stairs, slabs, fences, doors, trapdoors, pressure plates, buttons, signs.
 */
public class WoodcuttingFeature implements Feature {

    public static final String ID = "woodcutting";
    public static final String NAME = "Woodcutting";

    private boolean enabled = false;
    private WoodOnStoneCutter woodOnStoneCutter;

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
        // Register stonecutter recipes
        woodOnStoneCutter = new WoodOnStoneCutter(plugin);
        woodOnStoneCutter.registerRecipes();

        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (woodOnStoneCutter != null) {
            woodOnStoneCutter.removeRecipes();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
