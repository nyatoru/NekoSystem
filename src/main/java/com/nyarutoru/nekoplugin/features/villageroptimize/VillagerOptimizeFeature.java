package com.nyarutoru.nekoplugin.features.villageroptimize;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

public class VillagerOptimizeFeature extends AbstractFeature {

    public VillagerOptimizeFeature() {
        super("villager_optimize", "Villager Optimize");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new VillagerOptimizeListener(plugin), plugin);
        super.onEnable(plugin);
    }
}
