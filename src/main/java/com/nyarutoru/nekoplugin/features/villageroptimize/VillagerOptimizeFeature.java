package com.nyarutoru.nekoplugin.features.villageroptimize;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;

public class VillagerOptimizeFeature extends AbstractFeature {
    private VillagerOptimizeListener listener;
    private boolean protectDamage = true;
    private boolean protectKnockback = true;
    private boolean protectTargeting = true;
    private boolean restoreAiOnDisable = true;

    public VillagerOptimizeFeature() {
        super("villager_optimize", "Villager Optimize");
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.longValue("optimize-cooldown-seconds", "Optimization cooldown (seconds)", 600L, 0L, 86400L, ApplySemantics.IMMEDIATE,
            value -> VillagerOptimizePolicy.optimizeCooldownMillis = value * 1000L));
        register(registry, state, SettingDescriptor.longValue("level-check-cooldown-seconds", "Level check cooldown (seconds)", 5L, 0L, 3600L, ApplySemantics.IMMEDIATE,
            value -> VillagerOptimizePolicy.levelCheckCooldownMillis = value * 1000L));
        register(registry, state, SettingDescriptor.string("optimize-names", "Optimization name tags", "optimize,disableai", ApplySemantics.IMMEDIATE,
            VillagerOptimizePolicy::setOptimizeNames));
        register(registry, state, SettingDescriptor.bool("protect-damage", "Protect optimized villagers from damage", true, ApplySemantics.IMMEDIATE,
            value -> { protectDamage = value; if (listener != null) configureListener(); }));
        register(registry, state, SettingDescriptor.bool("protect-knockback", "Protect optimized villagers from knockback", true, ApplySemantics.IMMEDIATE,
            value -> { protectKnockback = value; if (listener != null) configureListener(); }));
        register(registry, state, SettingDescriptor.bool("protect-targeting", "Prevent targeting optimized villagers", true, ApplySemantics.IMMEDIATE,
            value -> { protectTargeting = value; if (listener != null) configureListener(); }));
        register(registry, state, SettingDescriptor.bool("restore-ai-on-disable", "Restore optimized villagers on disable", true, ApplySemantics.IMMEDIATE,
            value -> restoreAiOnDisable = value));
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

    private void configureListener() {
        listener.configure(protectDamage, protectKnockback, protectTargeting, restoreAiOnDisable);
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        listener = new VillagerOptimizeListener(plugin);
        listener.configure(protectDamage, protectKnockback, protectTargeting, restoreAiOnDisable);
        registerListener(listener, plugin);
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (listener != null) {
            listener.restoreLoadedOptimizedVillagers();
            listener = null;
        }
    }
}
