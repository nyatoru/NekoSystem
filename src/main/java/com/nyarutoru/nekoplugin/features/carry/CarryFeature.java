package com.nyarutoru.nekoplugin.features.carry;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

public final class CarryFeature extends AbstractFeature {
    private CarryManager manager;

    public CarryFeature() {
        super("carry", "Carry");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        manager = new CarryManager();
        registerListener(new CarryListener(manager), plugin);
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (manager != null) {
            manager.shutdown();
            manager = null;
        }
    }
}
