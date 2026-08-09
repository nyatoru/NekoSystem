package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

/**
 * Woodcutting feature - enhanced wood processing using stonecutter.
 * Allows converting logs to all wood-related items in the stonecutter:
 * planks, stairs, slabs, fences, doors, trapdoors, pressure plates, buttons, signs.
 * 
 * Prevents duplicate recipe registration by tracking processed wood types.
 */
public class WoodcuttingFeature extends AbstractFeature {

    private WoodOnStoneCutter woodOnStoneCutter;

    public WoodcuttingFeature() {
        super("woodcutting", "Woodcutting");
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        // Recipe output and input policy are intentionally not exposed: this feature
        // has no safe persisted recipe rebuild contract for live administration.
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        woodOnStoneCutter = new WoodOnStoneCutter(plugin);
        woodOnStoneCutter.registerRecipes();
        super.onEnable(plugin);
        plugin.getLogger().info("Woodcutting feature enabled");
    }

    @Override
    protected void cleanup() {
        if (woodOnStoneCutter != null) {
            woodOnStoneCutter.removeRecipes();
            woodOnStoneCutter = null;
        }
    }
}
