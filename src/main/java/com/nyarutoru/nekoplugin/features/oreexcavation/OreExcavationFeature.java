package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Ore Excavation feature - balanced VeinMiner alternative.
 * Uses ActiveToolAPI for shift activation.
 */
public class OreExcavationFeature extends AbstractFeature {

    public OreExcavationFeature() {
        super("ore_excavation", "Ore Excavation");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new OreExcavationListener(), plugin);
        super.onEnable(plugin);
    }
}
