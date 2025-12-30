package com.nyarutoru.nekoplugin;

import com.nyarutoru.nekoplugin.api.gui.GUIManager;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolListener;
import com.nyarutoru.nekoplugin.core.DatabaseManager;
import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.PluginManager;
import com.nyarutoru.nekoplugin.features.drawer.DrawerFeature;
import com.nyarutoru.nekoplugin.features.hammer.HammerFeature;
import com.nyarutoru.nekoplugin.features.oreexcavation.OreExcavationFeature;
import com.nyarutoru.nekoplugin.features.player.PlayerFeature;
import com.nyarutoru.nekoplugin.features.server.ServerFeature;
import com.nyarutoru.nekoplugin.features.tool.SandExcavationFeature;
import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerFeature;
import com.nyarutoru.nekoplugin.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class NekoPlugin extends JavaPlugin {

    private static NekoPlugin instance;

    public static NekoPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Initialize core managers
        PluginManager.getInstance().initialize();
        DatabaseManager.getInstance().initialize(this);
        FeatureManager.getInstance().initialize(this);
        GUIManager.getInstance().initialize(this);

        // Register features
        FeatureManager.getInstance().registerFeature(new DrawerFeature());
        FeatureManager.getInstance().registerFeature(new OreExcavationFeature());
        FeatureManager.getInstance().registerFeature(new SandExcavationFeature());
        FeatureManager.getInstance().registerFeature(new TreeFellerFeature());
        FeatureManager.getInstance().registerFeature(new HammerFeature());
        FeatureManager.getInstance().registerFeature(new PlayerFeature());
        FeatureManager.getInstance().registerFeature(new ServerFeature());
        FeatureManager.getInstance().registerFeature(new com.nyarutoru.nekoplugin.features.petcarry.PetCarryFeature());

        // Enable all features
        FeatureManager.getInstance().enableAll();

        // Register core listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new ActiveToolListener(), this);

        getLogger().info("NekoPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Disable all features
        FeatureManager.getInstance().disableAll();

        // Close database connection
        DatabaseManager.getInstance().shutdown();

        getLogger().info("NekoPlugin has been disabled!");
    }
}