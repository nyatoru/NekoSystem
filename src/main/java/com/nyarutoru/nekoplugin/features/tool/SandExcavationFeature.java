package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Sand Excavation Feature - mass mine sand and gravel with shovels.
 */
public class SandExcavationFeature extends AbstractFeature {
    private SandExcavationListener listener;

    public SandExcavationFeature() {
        super("sand_excavation", "Sand Excavation");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        if (listener == null) listener = new SandExcavationListener();
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        ActiveToolAPI.getInstance().registerSettings(registry, state, getId());
        SandExcavationListener current = listener == null ? new SandExcavationListener() : listener;
        current.registerSettings(registry, state);
        if (listener == null) listener = current;
    }

    @Override
    protected void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveToolAPI.getInstance().cleanupTool(player, SandExcavationListener.TOOL_NAME);
        }
    }
}
