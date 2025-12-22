package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.event.HandlerList;

/**
 * Server Feature - server-side optimizations and management.
 * Includes: Pillager management, Concrete converter, Custom Crafting Table,
 * Block Interactions
 */
public class ServerFeature implements Feature {

    public static final String ID = "server";
    public static final String NAME = "Server Utilities";

    private boolean enabled = false;
    private PillagerManager pillagerManager;
    private ConcreteConverter concreteConverter;
    private CustomCraftingListener customCraftingListener;
    private ServerListener serverListener;
    private TPSBossBarTask tpsTask;

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
        // Pillager management
        pillagerManager = new PillagerManager(plugin);
        pillagerManager.start();

        // Concrete conversion
        concreteConverter = new ConcreteConverter(plugin);
        concreteConverter.start();

        // Custom Crafting Table
        customCraftingListener = new CustomCraftingListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(customCraftingListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(customCraftingListener.getRecipeBookGUI(), plugin);
        // RecipePreviewGUI now uses GuiAPI so no separate listener registration needed

        // Server Listener (instant break, ladder, anvil repair, lag notification)
        serverListener = new ServerListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(serverListener, plugin);

        // TPS BossBar - run every second (20 ticks)
        tpsTask = new TPSBossBarTask();
        SchedulerUtils.runGlobalTimer(tpsTask::run, 20L, 20L);

        // Server Recipes (furnace recipes, etc.)
        new ServerRecipes(plugin).registerAll();

        this.enabled = true;
        plugin.getLogger().info("Server feature enabled.");
    }

    @Override
    public void onDisable() {
        if (pillagerManager != null) {
            pillagerManager.stop();
        }
        if (concreteConverter != null) {
            concreteConverter.stop();
        }
        if (customCraftingListener != null) {
            HandlerList.unregisterAll(customCraftingListener);
            HandlerList.unregisterAll(customCraftingListener.getRecipeBookGUI());
        }
        if (serverListener != null) {
            HandlerList.unregisterAll(serverListener);
        }
        if (tpsTask != null) {
            tpsTask.cleanup();
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
