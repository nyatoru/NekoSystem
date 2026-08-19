package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * Player Feature - integrated player quality-of-life improvements.
 * Includes: AFK System, Auto Item Replenishment
 */
public class PlayerFeature extends AbstractFeature {

    private static final boolean DEFAULT_AFK_ENABLED = true;
    private static final int DEFAULT_AFK_TIMEOUT_SECONDS = 5 * 60;
    private static final boolean DEFAULT_ACTIVITY_DETECTION = true;
    private static final boolean DEFAULT_AFK_DISPLAY = true;
    private static final String DEFAULT_AFK_PREFIX = "[AFK] ";
    private static final boolean DEFAULT_AFK_BROADCASTS = true;
    private static final boolean DEFAULT_AFK_POPUP = true;
    private static final boolean DEFAULT_MONSTER_PROTECTION = true;
    private static final boolean DEFAULT_AUTO_REPLENISH = true;
    private static final boolean DEFAULT_FOOD_FALLBACK = true;
    private static final boolean DEFAULT_CROP_HARVEST = true;
    private static final List<Material> DEFAULT_ALLOWED_CROPS = PlayerFeatureListener.DEFAULT_ALLOWED_CROPS;
    private static final boolean DEFAULT_HOE_REQUIRED = true;
    private static final boolean DEFAULT_REPLANT_CROPS = true;

    private PlayerFeatureListener listener;
    private boolean afkEnabled = DEFAULT_AFK_ENABLED;
    private int afkTimeoutSeconds = DEFAULT_AFK_TIMEOUT_SECONDS;
    private boolean activityDetection = DEFAULT_ACTIVITY_DETECTION;
    private boolean afkDisplay = DEFAULT_AFK_DISPLAY;
    private String afkPrefix = DEFAULT_AFK_PREFIX;
    private boolean afkBroadcasts = DEFAULT_AFK_BROADCASTS;
    private boolean afkPopup = DEFAULT_AFK_POPUP;
    private boolean monsterProtection = DEFAULT_MONSTER_PROTECTION;
    private boolean autoReplenish = DEFAULT_AUTO_REPLENISH;
    private boolean foodFallback = DEFAULT_FOOD_FALLBACK;
    private boolean cropHarvest = DEFAULT_CROP_HARVEST;
    private List<Material> allowedCrops = DEFAULT_ALLOWED_CROPS;
    private boolean hoeRequired = DEFAULT_HOE_REQUIRED;
    private boolean replantCrops = DEFAULT_REPLANT_CROPS;

    public PlayerFeature() {
        super("player", "Player Utilities");
    }

    /** Registers settings and applies persisted values before feature startup. */
    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Boolean> afk = SettingDescriptor.bool(
                "afk-enabled", "AFK system enabled", DEFAULT_AFK_ENABLED,
                ApplySemantics.IMMEDIATE, this::setAfkEnabled);
        SettingDescriptor<Integer> timeout = SettingDescriptor.integer(
                "afk-timeout-seconds", "AFK timeout seconds", DEFAULT_AFK_TIMEOUT_SECONDS,
                1, 86400, ApplySemantics.RESCHEDULE, this::setAfkTimeoutSeconds);
        SettingDescriptor<Boolean> activity = SettingDescriptor.bool(
                "activity-detection", "AFK activity detection", DEFAULT_ACTIVITY_DETECTION,
                ApplySemantics.RESCHEDULE, this::setActivityDetection);
        SettingDescriptor<Boolean> display = SettingDescriptor.bool(
                "afk-display", "AFK display effects", DEFAULT_AFK_DISPLAY,
                ApplySemantics.IMMEDIATE, this::setAfkDisplay);
        SettingDescriptor<String> prefix = SettingDescriptor.string(
                "afk-display-prefix", "AFK display prefix", DEFAULT_AFK_PREFIX,
                ApplySemantics.IMMEDIATE, this::setAfkPrefix);
        SettingDescriptor<Boolean> broadcasts = SettingDescriptor.bool(
                "afk-broadcasts", "AFK broadcast messages", DEFAULT_AFK_BROADCASTS,
                ApplySemantics.IMMEDIATE, this::setAfkBroadcasts);
        SettingDescriptor<Boolean> popup = SettingDescriptor.bool(
                "afk-popup", "AFK text display popup", DEFAULT_AFK_POPUP,
                ApplySemantics.IMMEDIATE, this::setAfkPopup);
        SettingDescriptor<Boolean> protection = SettingDescriptor.bool(
                "monster-protection", "AFK monster protection", DEFAULT_MONSTER_PROTECTION,
                ApplySemantics.IMMEDIATE, this::setMonsterProtection);
        SettingDescriptor<Boolean> replenish = SettingDescriptor.bool(
                "auto-replenish", "Auto item replenishment", DEFAULT_AUTO_REPLENISH,
                ApplySemantics.IMMEDIATE, this::setAutoReplenish);
        SettingDescriptor<Boolean> food = SettingDescriptor.bool(
                "food-fallback", "Food fallback replenishment", DEFAULT_FOOD_FALLBACK,
                ApplySemantics.IMMEDIATE, this::setFoodFallback);
        SettingDescriptor<Boolean> harvest = SettingDescriptor.bool(
                "crop-harvest", "Crop harvest", DEFAULT_CROP_HARVEST,
                ApplySemantics.IMMEDIATE, this::setCropHarvest);
        SettingDescriptor<List<Material>> crops = SettingDescriptor.materials(
                "allowed-crops", "Allowed crops", DEFAULT_ALLOWED_CROPS,
                ApplySemantics.IMMEDIATE, this::setAllowedCrops);
        SettingDescriptor<Boolean> hoe = SettingDescriptor.bool(
                "hoe-required", "Require a hoe for crop harvest", DEFAULT_HOE_REQUIRED,
                ApplySemantics.IMMEDIATE, this::setHoeRequired);
        SettingDescriptor<Boolean> replant = SettingDescriptor.bool(
                "replant-crops", "Replant harvested crops", DEFAULT_REPLANT_CROPS,
                ApplySemantics.IMMEDIATE, this::setReplantCrops);

        register(registry, state, afk);
        register(registry, state, timeout);
        register(registry, state, activity);
        register(registry, state, display);
        register(registry, state, prefix);
        register(registry, state, broadcasts);
        register(registry, state, popup);
        register(registry, state, protection);
        register(registry, state, replenish);
        register(registry, state, food);
        register(registry, state, harvest);
        register(registry, state, crops);
        register(registry, state, hoe);
        register(registry, state, replant);
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        if (listener == null) listener = new PlayerFeatureListener(plugin, this::ownTask);
        listener.configure(afkEnabled, afkTimeoutSeconds, activityDetection, afkDisplay, afkPrefix,
                afkBroadcasts, afkPopup, monsterProtection, autoReplenish, foodFallback, cropHarvest,
                allowedCrops, hoeRequired, replantCrops);
        registerListener(listener, plugin);
        super.onEnable(plugin);
        listener.start();
    }

    @Override
    protected void cleanup() {
        if (listener != null) {
            listener.stop();
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

    private void setAfkEnabled(boolean value) {
        afkEnabled = value;
        if (listener != null) listener.setAfkEnabled(value);
    }

    private void setAfkTimeoutSeconds(int value) {
        afkTimeoutSeconds = value;
        if (listener != null) listener.setAfkTimeoutSeconds(value);
    }

    private void setActivityDetection(boolean value) {
        activityDetection = value;
        if (listener != null) listener.setActivityDetection(value);
    }

    private void setAfkDisplay(boolean value) {
        afkDisplay = value;
        if (listener != null) listener.setAfkDisplay(value);
    }

    private void setAfkPrefix(String value) {
        if (value.length() > 64) throw new IllegalArgumentException("AFK prefix is too long");
        afkPrefix = value;
        if (listener != null) listener.setAfkPrefix(value);
    }

    private void setAfkBroadcasts(boolean value) {
        afkBroadcasts = value;
        if (listener != null) listener.setAfkBroadcasts(value);
    }

    private void setAfkPopup(boolean value) {
        afkPopup = value;
        if (listener != null) listener.setAfkPopup(value);
    }

    private void setMonsterProtection(boolean value) {
        monsterProtection = value;
        if (listener != null) listener.setMonsterProtection(value);
    }

    private void setAutoReplenish(boolean value) {
        autoReplenish = value;
        if (listener != null) listener.setAutoReplenish(value);
    }

    private void setFoodFallback(boolean value) {
        foodFallback = value;
        if (listener != null) listener.setFoodFallback(value);
    }

    private void setCropHarvest(boolean value) {
        cropHarvest = value;
        if (listener != null) listener.setCropHarvest(value);
    }

    private void setAllowedCrops(List<Material> value) {
        if (value.isEmpty()) throw new IllegalArgumentException("At least one crop is required");
        if (!Set.copyOf(PlayerFeatureListener.DEFAULT_ALLOWED_CROPS).containsAll(value)) {
            throw new IllegalArgumentException("Allowed crops must be wheat, carrots, potatoes, beetroot, or nether wart");
        }
        allowedCrops = List.copyOf(value);
        if (listener != null) listener.setAllowedCrops(value);
    }

    private void setHoeRequired(boolean value) {
        hoeRequired = value;
        if (listener != null) listener.setHoeRequired(value);
    }

    private void setReplantCrops(boolean value) {
        replantCrops = value;
        if (listener != null) listener.setReplantCrops(value);
    }
}
