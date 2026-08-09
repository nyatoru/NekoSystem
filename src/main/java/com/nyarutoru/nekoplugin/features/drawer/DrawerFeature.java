package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.features.drawer.gui.DrawerGUI;

/**
 * Drawer storage feature - craft and place drawers to store large quantities of
 * a single item type.
 */
public class DrawerFeature extends AbstractFeature {
    private static final long DEFAULT_AUTO_SAVE_INTERVAL_SECONDS = 5 * 60;
    private static final boolean DEFAULT_DEPOSIT_ENABLED = true;
    private static final boolean DEFAULT_WITHDRAW_ENABLED = true;

    private DrawerRecipes recipes;
    private long autoSaveIntervalSeconds = DEFAULT_AUTO_SAVE_INTERVAL_SECONDS;
    private boolean depositEnabled = DEFAULT_DEPOSIT_ENABLED;
    private boolean withdrawEnabled = DEFAULT_WITHDRAW_ENABLED;

    public DrawerFeature() {
        super("drawer", "Drawer Storage");
    }

    /** Registers safe storage policies; tier identity, order, and capacity remain fixed. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        register(registry, state, SettingDescriptor.longValue("autosave-interval-seconds", "Drawer autosave interval (seconds)",
                DEFAULT_AUTO_SAVE_INTERVAL_SECONDS, 10, 86400, ApplySemantics.RESCHEDULE, value -> {
                    autoSaveIntervalSeconds = value;
                    DrawerManager.getInstance().setAutoSaveIntervalSeconds(value);
                }));
        register(registry, state, SettingDescriptor.string("blocked-material-categories", "Blocked drawer material categories",
                String.join(",", com.nyarutoru.nekoplugin.features.drawer.data.Drawer.defaultBlockedCategories()), ApplySemantics.IMMEDIATE,
                value -> com.nyarutoru.nekoplugin.features.drawer.data.Drawer.setBlockedCategories(java.util.Arrays.stream(value.split(",")).toList())));
        register(registry, state, SettingDescriptor.bool("deposit-enabled", "Drawer deposits enabled", DEFAULT_DEPOSIT_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { depositEnabled = value; com.nyarutoru.nekoplugin.features.drawer.data.Drawer.setDepositsEnabled(value); }));
        register(registry, state, SettingDescriptor.bool("withdraw-enabled", "Drawer withdrawals enabled", DEFAULT_WITHDRAW_ENABLED,
                ApplySemantics.IMMEDIATE, value -> { withdrawEnabled = value; com.nyarutoru.nekoplugin.features.drawer.data.Drawer.setWithdrawalsEnabled(value); }));
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

    public boolean isDepositEnabled() {
        return depositEnabled;
    }

    public boolean isWithdrawEnabled() {
        return withdrawEnabled;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        DrawerManager.getInstance().setAutoSaveIntervalSeconds(autoSaveIntervalSeconds);
        DrawerManager.getInstance().initialize(plugin);

        registerListener(new DrawerListener(), plugin);
        recipes = new DrawerRecipes();
        recipes.registerAll();

        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        if (recipes != null) {
            recipes.unregisterAll();
            recipes = null;
        }
        DrawerGUI.closeAllViewers();
        DrawerManager.getInstance().shutdown();
    }
}
