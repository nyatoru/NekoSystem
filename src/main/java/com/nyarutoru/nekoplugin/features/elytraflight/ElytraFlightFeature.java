package com.nyarutoru.nekoplugin.features.elytraflight;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

/**
 * Elytra Flight - fueled elytra gliding using ender pearls as fuel.
 * Sneak while holding an ender pearl with an elytra equipped to toggle the mode.
 */
public class ElytraFlightFeature extends AbstractFeature {

    private static final int DEFAULT_PEARL_SECONDS = 6;
    private static final int DEFAULT_DURABILITY_MULTIPLIER = 3;
    private static final int DEFAULT_SHIFT_COUNT = 10;

    private ElytraFlightListener listener;
    private int pearlSeconds = DEFAULT_PEARL_SECONDS;
    private int durabilityMultiplier = DEFAULT_DURABILITY_MULTIPLIER;
    private int shiftCount = DEFAULT_SHIFT_COUNT;

    public ElytraFlightFeature() {
        super("elytraflight", "Elytra Flight");
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> pearl = SettingDescriptor.integer(
                "pearl-flight-seconds", "Seconds of gliding per ender pearl", DEFAULT_PEARL_SECONDS,
                1, 3600, ApplySemantics.IMMEDIATE, this::setPearlSeconds);
        SettingDescriptor<Integer> multiplier = SettingDescriptor.integer(
                "elytra-durability-multiplier", "Elytra durability drain multiplier", DEFAULT_DURABILITY_MULTIPLIER,
                1, 100, ApplySemantics.IMMEDIATE, this::setDurabilityMultiplier);
        SettingDescriptor<Integer> shift = SettingDescriptor.integer(
                "flight-shift-count", "Sneak presses required to toggle fueled flight", DEFAULT_SHIFT_COUNT,
                2, 100, ApplySemantics.IMMEDIATE, this::setShiftCount);

        register(registry, state, pearl);
        register(registry, state, multiplier);
        register(registry, state, shift);
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        listener = new ElytraFlightListener();
        listener.setPearlSeconds(pearlSeconds);
        listener.setDurabilityMultiplier(durabilityMultiplier);
        listener.setShiftCount(shiftCount);
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (listener != null) {
            listener.shutdown();
            listener = null;
        }
    }

    private <T> void register(SettingRegistry registry, AdminState state, SettingDescriptor<T> descriptor) {
        registry.register(getId(), descriptor);
        String stored = state.settingValue(getId(), descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    private void setPearlSeconds(int value) {
        pearlSeconds = value;
        if (listener != null) listener.setPearlSeconds(value);
    }

    private void setDurabilityMultiplier(int value) {
        durabilityMultiplier = value;
        if (listener != null) listener.setDurabilityMultiplier(value);
    }

    private void setShiftCount(int value) {
        shiftCount = value;
        if (listener != null) listener.setShiftCount(value);
    }
}
