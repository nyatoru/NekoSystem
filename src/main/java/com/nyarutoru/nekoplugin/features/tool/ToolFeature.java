package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tool Feature - hosts the shared active-tool vein mining tools:
 * Sand Excavation (shovels/sand) and Shears Harvest (shears/leaves).
 */
public class ToolFeature extends AbstractFeature {
    private SandExcavationListener sandListener;
    private ShearsHarvestListener shearsListener;

    public ToolFeature() {
        super("tool", "Tool");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        if (sandListener == null) sandListener = new SandExcavationListener();
        if (shearsListener == null) shearsListener = new ShearsHarvestListener();
        registerListener(sandListener, plugin);
        registerListener(shearsListener, plugin);
        super.onEnable(plugin);
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        String featureId = getId();
        ActiveToolAPI.getInstance().registerSettings(registry, state, featureId);
        SandExcavationListener sand = sandListener == null ? new SandExcavationListener() : sandListener;
        sand.registerSettings(registry, state, featureId);
        if (sandListener == null) sandListener = sand;
        ShearsHarvestListener shears = shearsListener == null ? new ShearsHarvestListener() : shearsListener;
        shears.registerSettings(registry, state, featureId);
        if (shearsListener == null) shearsListener = shears;
    }

    @Override
    protected void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveToolAPI.getInstance().cleanupTool(player, SandExcavationListener.TOOL_NAME);
            ActiveToolAPI.getInstance().cleanupTool(player, ShearsHarvestListener.TOOL_NAME);
        }
    }
}
