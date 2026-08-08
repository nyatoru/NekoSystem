package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

public final class GravesFeature extends AbstractFeature {
    private GraveManager manager;
    private GraveCommands commands;

    public GravesFeature() {
        super("graves", "Grave");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        manager = new GraveManager(plugin);
        if (!manager.start()) throw new IllegalStateException("Grave persistence could not be initialized");
        try {
            commands = new GraveCommands(plugin, manager);
            commands.register();
            registerListener(new GraveListener(manager), plugin);
            super.onEnable(plugin);
        } catch (RuntimeException exception) {
            cleanup();
            throw exception;
        }
    }

    @Override
    protected void cleanup() {
        if (commands != null) commands.unregister();
        if (manager != null) manager.stop();
    }

    public GraveManager getGraveManager() { return manager; }
}
