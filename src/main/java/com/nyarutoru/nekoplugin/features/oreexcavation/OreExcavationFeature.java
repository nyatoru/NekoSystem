package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Ore Excavation feature - balanced VeinMiner alternative.
 * Uses ActiveToolAPI for shift activation.
 */
public class OreExcavationFeature extends AbstractFeature {
    private OreExcavationListener listener;

    public OreExcavationFeature() {
        super("ore_excavation", "Ore Excavation");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        if (listener == null) listener = new OreExcavationListener();
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        ActiveToolAPI.getInstance().registerSettings(registry, state, getId());
        OreExcavationListener current = listener == null ? new OreExcavationListener() : listener;
        current.registerSettings(registry, state);
        if (listener == null) listener = current;
    }

    @Override
    protected void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveToolAPI.getInstance().cleanupTool(player, OreExcavationListener.TOOL_NAME);
        }
    }
}
