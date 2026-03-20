package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;

/**
 * Server Feature - server-side optimizations and management.
 * Includes: Pillager management, Concrete converter, Custom Crafting Table,
 * Block Interactions
 */
public class ServerFeature extends AbstractFeature {

    private ConcreteConverter concreteConverter;
    private CustomCraftingListener customCraftingListener;
    private ServerEventsListener serverEventsListener;
    private TPSBossBarTask tpsTask;

    public ServerFeature() {
        super("server", "Server Utilities");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        // Concrete conversion
        concreteConverter = new ConcreteConverter();
        concreteConverter.start();

        // Custom Crafting Table
        customCraftingListener = new CustomCraftingListener(plugin);
        registerListener(customCraftingListener, plugin);
        registerListener(customCraftingListener.getRecipeBookGUI(), plugin);
        // RecipePreviewGUI now uses GuiAPI so no separate listener registration needed

        // Server Events (join/quit, instant break, ladders, anvil repair, lag notification)
        serverEventsListener = new ServerEventsListener(plugin);
        registerListener(serverEventsListener, plugin);

        // TPS BossBar - run every second (20 ticks)
        tpsTask = new TPSBossBarTask();
        SchedulerUtils.runGlobalTimer(tpsTask::run, 20L, 20L);

        // Server Recipes (furnace recipes, etc.)
        new ServerRecipes(plugin).registerAll();

        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (concreteConverter != null) {
            concreteConverter.stop();
        }
        if (tpsTask != null) {
            tpsTask.cleanup();
        }
    }
}
