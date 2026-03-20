package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Sand Excavation Feature - mass mine sand and gravel with shovels.
 */
public class SandExcavationFeature extends AbstractFeature {

    public SandExcavationFeature() {
        super("sand_excavation", "Sand Excavation");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new SandExcavationListener(), plugin);
        super.onEnable(plugin);
    }
}
