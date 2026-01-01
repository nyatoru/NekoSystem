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
    private ConcreteConverter concreteConverter;
    private CustomCraftingListener customCraftingListener;
    private ServerEventsListener serverEventsListener;
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
        // Concrete conversion
        concreteConverter = new ConcreteConverter();
        concreteConverter.start();

        // Custom Crafting Table
        customCraftingListener = new CustomCraftingListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(customCraftingListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(customCraftingListener.getRecipeBookGUI(), plugin);
        // RecipePreviewGUI now uses GuiAPI so no separate listener registration needed

        // Server Events (join/quit, instant break, ladders, anvil repair, lag
        // notification)
        serverEventsListener = new ServerEventsListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(serverEventsListener, plugin);

        // TPS BossBar - run every second (20 ticks)
        tpsTask = new TPSBossBarTask();
        SchedulerUtils.runGlobalTimer(tpsTask::run, 20L, 20L);

        // Server Recipes (furnace recipes, etc.)
        new ServerRecipes(plugin).registerAll();

        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (concreteConverter != null) {
            concreteConverter.stop();
        }
        if (customCraftingListener != null) {
            HandlerList.unregisterAll(customCraftingListener);
            HandlerList.unregisterAll(customCraftingListener.getRecipeBookGUI());
        }
        if (serverEventsListener != null) {
            HandlerList.unregisterAll(serverEventsListener);
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
