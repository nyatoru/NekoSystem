package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Hammer feature - mine 3x3 areas with a special pickaxe variant.
 * Hammers come in all standard tiers and are slightly harder to craft.
 */
public class HammerFeature implements Feature {

    public static final String ID = "hammer";
    public static final String NAME = "Hammer";

    private boolean enabled = false;
    private HammerListener listener;

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
        // Register recipes
        new HammerRecipes(plugin).registerAll();

        // Register listener
        listener = new HammerListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        this.enabled = true;
        plugin.getLogger().info("Hammer feature enabled.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
