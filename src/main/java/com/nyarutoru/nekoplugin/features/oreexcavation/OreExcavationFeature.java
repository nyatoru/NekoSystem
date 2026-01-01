package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

/**
 * Ore Excavation feature - balanced VeinMiner alternative.
 * Uses ActiveToolAPI for shift activation.
 */
public class OreExcavationFeature implements Feature {

    public static final String ID = "ore_excavation";
    public static final String NAME = "Ore Excavation";

    private boolean enabled = false;
    private OreExcavationListener listener;

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
        listener = new OreExcavationListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        this.enabled = true;
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
