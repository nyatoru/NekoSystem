package com.nyarutoru.nekoplugin.features.shutup;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;

/**
 * Shut Up items - place a furnace to silence the targeted mob sounds in a 33x33 area.
 */
public final class ShutUpFeature extends AbstractFeature {

    private ShutUpItems recipes;

    public ShutUpFeature() {
        super("shutup", "Shut Up");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new ShutUpListener(), plugin);
        recipes = new ShutUpItems();
        recipes.registerAll();
        ownTask(SchedulerUtils.runGlobalTimerTask(ShutUpManager.getInstance()::tick, 20L, 20L));
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
        ShutUpManager.getInstance().shutdown();
    }
}
