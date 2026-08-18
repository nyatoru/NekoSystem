package com.nyarutoru.nekoplugin.features.mending;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Right-click a damaged Mending item to repair it, costing the player's
 * current experience points.
 */
public final class MendingRepairFeature extends AbstractFeature {

    public MendingRepairFeature() {
        super("mending_repair", "Mending Repair");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new MendingRepairListener(), plugin);
        super.onEnable(plugin);
    }
}
