package com.nyarutoru.nekoplugin.features.mending;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

/**
 * Right-click a damaged Mending item to repair it, costing the player's
 * current experience points.
 */
public final class MendingRepairFeature extends AbstractFeature {

    private static final int DEFAULT_REPAIR_RATE = 2;

    private MendingRepairListener listener;
    private int repairRate = DEFAULT_REPAIR_RATE;

    public MendingRepairFeature() {
        super("mending_repair", "Mending Repair");
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> rate = SettingDescriptor.integer(
                "repair-rate", "Durability repaired per XP point", DEFAULT_REPAIR_RATE,
                1, 64, ApplySemantics.IMMEDIATE, this::setRepairRate);
        registry.register(getId(), rate);
        String stored = state.settingValue(getId(), rate.key());
        try {
            rate.apply(stored == null ? rate.defaultValue() : rate.parse(stored));
        } catch (IllegalArgumentException ignored) {
            rate.apply(rate.defaultValue());
        }
    }

    private void setRepairRate(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("Repair rate must be between 1 and 64");
        repairRate = value;
        if (listener != null) listener.setRepairRate(value);
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        listener = new MendingRepairListener();
        listener.setRepairRate(repairRate);
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        listener = null;
    }
}