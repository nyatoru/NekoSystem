package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe in-memory model for persisted operator choices. */
public final class AdminState {
    private final Map<String, Boolean> desiredFeatures = new LinkedHashMap<>();
    private final Map<String, String> settingValues = new LinkedHashMap<>();

    public synchronized boolean desiredEnabled(String featureId) {
        return desiredFeatures.getOrDefault(featureId, true);
    }

    public synchronized void setDesiredEnabled(String featureId, boolean enabled) {
        desiredFeatures.put(featureId, enabled);
    }

    public synchronized String settingValue(String featureId, String settingKey) {
        return settingValues.get(featureId + "." + settingKey);
    }

    public synchronized void setSettingValue(String featureId, String settingKey, String value) {
        settingValues.put(featureId + "." + settingKey, value);
    }

    /** Replaces an invalid or absent persisted value with the descriptor's canonical default. */
    public synchronized <T> String canonicalizeSetting(String featureId, SettingDescriptor<T> descriptor) {
        String stored = settingValue(featureId, descriptor.key());
        if (stored == null) {
            return descriptor.format(descriptor.defaultValue());
        }
        try {
            String canonical = descriptor.format(descriptor.parse(stored));
            if (!canonical.equals(stored)) setSettingValue(featureId, descriptor.key(), canonical);
            return canonical;
        } catch (IllegalArgumentException exception) {
            String canonical = descriptor.format(descriptor.defaultValue());
            setSettingValue(featureId, descriptor.key(), canonical);
            return canonical;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Map.copyOf(desiredFeatures), Map.copyOf(settingValues));
    }

    public synchronized void replace(Map<String, Boolean> features, Map<String, String> values) {
        desiredFeatures.clear();
        desiredFeatures.putAll(features);
        settingValues.clear();
        settingValues.putAll(values);
    }

    public record Snapshot(Map<String, Boolean> desiredFeatures, Map<String, String> settingValues) { }
}
