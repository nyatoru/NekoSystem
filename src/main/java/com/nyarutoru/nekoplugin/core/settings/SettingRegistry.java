package com.nyarutoru.nekoplugin.core.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of settings grouped by feature ID. */
public final class SettingRegistry {
    private final Map<String, LinkedHashMap<String, SettingDescriptor<?>>> settings = new LinkedHashMap<>();

    public synchronized void register(String featureId, SettingDescriptor<?> descriptor) {
        SettingDescriptor<?> previous = settings.computeIfAbsent(featureId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(descriptor.key(), descriptor);
        if (previous != null) throw new IllegalArgumentException("Duplicate setting: " + featureId + "." + descriptor.key());
    }

    public synchronized List<SettingDescriptor<?>> get(String featureId) {
        Map<String, SettingDescriptor<?>> registered = settings.get(featureId);
        return registered == null ? List.of() : List.copyOf(registered.values());
    }
}
