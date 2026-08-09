package com.nyarutoru.nekoplugin.features.carry;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

public final class CarryFeature extends AbstractFeature {
    private static final boolean DEFAULT_REQUIRE_SNEAKING = true;
    private static final boolean DEFAULT_ANIMALS_ENABLED = true;
    private static final boolean DEFAULT_VILLAGERS_ENABLED = true;
    private static final boolean DEFAULT_CONTAINERS_ENABLED = true;
    private static final boolean DEFAULT_LECTERNS_ENABLED = true;
    private static final boolean DEFAULT_WORKSTATIONS_ENABLED = true;

    private CarryManager manager;
    private boolean requireSneaking = DEFAULT_REQUIRE_SNEAKING;
    private boolean animalsEnabled = DEFAULT_ANIMALS_ENABLED;
    private boolean villagersEnabled = DEFAULT_VILLAGERS_ENABLED;
    private boolean containersEnabled = DEFAULT_CONTAINERS_ENABLED;
    private boolean lecternsEnabled = DEFAULT_LECTERNS_ENABLED;
    private boolean workstationsEnabled = DEFAULT_WORKSTATIONS_ENABLED;

    public CarryFeature() {
        super("carry", "Carry");
    }

    /** Registers safe carry gesture and category policies before enablement. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.bool("require-sneaking", "Require sneaking to carry", DEFAULT_REQUIRE_SNEAKING,
                ApplySemantics.IMMEDIATE, value -> { requireSneaking = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
        register(registry, state, SettingDescriptor.bool("animals-enabled", "Carry animals", DEFAULT_ANIMALS_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { animalsEnabled = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
        register(registry, state, SettingDescriptor.bool("villagers-enabled", "Carry villagers", DEFAULT_VILLAGERS_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { villagersEnabled = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
        register(registry, state, SettingDescriptor.bool("containers-enabled", "Carry containers", DEFAULT_CONTAINERS_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { containersEnabled = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
        register(registry, state, SettingDescriptor.bool("lecterns-enabled", "Carry lecterns", DEFAULT_LECTERNS_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { lecternsEnabled = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
        register(registry, state, SettingDescriptor.bool("workstations-enabled", "Carry workstations", DEFAULT_WORKSTATIONS_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { workstationsEnabled = value; if (manager != null) manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled); }));
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

    @Override
    public void onEnable(NekoPlugin plugin) {
        manager = new CarryManager();
        manager.configure(requireSneaking, animalsEnabled, villagersEnabled, containersEnabled, lecternsEnabled, workstationsEnabled);
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
