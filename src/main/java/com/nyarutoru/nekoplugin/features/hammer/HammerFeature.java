package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

/**
 * Hammer feature - mine 3x3 areas with a special pickaxe variant.
 * Hammers come in all standard tiers and are slightly harder to craft.
 */
public class HammerFeature extends AbstractFeature {

    private HammerRecipes recipes;
    private HammerListener listener;

    public HammerFeature() {
        super("hammer", "Hammer");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        recipes = new HammerRecipes(plugin);
        recipes.registerAll();
        if (listener == null) listener = new HammerListener(plugin);
        registerListener(listener, plugin);
        super.onEnable(plugin);
        listener.start();
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        if (listener == null) {
            NekoPlugin plugin = NekoPlugin.getInstance();
            if (plugin == null) throw new IllegalStateException("Plugin must be initialized before registering settings");
            listener = new HammerListener(plugin);
        }
        listener.registerSettings(registry, state);
    }

    @Override
    protected void cleanup() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
        if (listener != null) {
            listener.stop();
        }
    }
}
