package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Hammer feature - mine 3x3 areas with a special pickaxe variant.
 * Hammers come in all standard tiers and are slightly harder to craft.
 */
public class HammerFeature extends AbstractFeature {

    public HammerFeature() {
        super("hammer", "Hammer");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        new HammerRecipes(plugin).registerAll();
        registerListener(new HammerListener(plugin), plugin);
        super.onEnable(plugin);
    }
}
