package com.nyarutoru.nekoplugin.features.furnace;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

/**
 * Upgrade Furnace feature - craft a tiered furnace that smelts up to 9x faster.
 */
public class FurnaceFeature extends AbstractFeature {
    private FurnaceRecipes recipes;

    public FurnaceFeature() {
        super("furnace", "Upgrade Furnace");
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Double> scale = SettingDescriptor.doubleValue(
                "speed-scale", "Global smelting speed scale", 1.0,
                0.0, 10.0, ApplySemantics.IMMEDIATE, this::setSpeedScale);
        registry.register(getId(), scale);
        String stored = state.settingValue(getId(), scale.key());
        try {
            scale.apply(stored == null ? scale.defaultValue() : scale.parse(stored));
        } catch (IllegalArgumentException ignored) {
            scale.apply(scale.defaultValue());
        }
    }

    private void setSpeedScale(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 10.0) {
            throw new IllegalArgumentException("Speed scale must be between 0 and 10");
        }
        FurnaceManager.getInstance().setSpeedScale(value);
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        FurnaceManager.getInstance().start();
        registerListener(new FurnaceListener(), plugin);
        recipes = new FurnaceRecipes();
        recipes.registerAll();
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
        FurnaceManager.getInstance().stop();
    }
}