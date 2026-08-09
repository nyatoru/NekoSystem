package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Dedicated YAML persistence with coalesced asynchronous writes. */
public final class AdminConfigStore {
    private final NekoPlugin plugin;
    private final AdminState state;
    private final File file;
    private final AtomicBoolean writeScheduled = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Object fileLock = new Object();
    private volatile boolean shuttingDown;

    public AdminConfigStore(NekoPlugin plugin, AdminState state) {
        this.plugin = plugin;
        this.state = state;
        this.file = new File(plugin.getDataFolder(), "admin.yml");
    }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Boolean> features = new LinkedHashMap<>();
        ConfigurationSection featureSection = yaml.getConfigurationSection("features");
        if (featureSection != null) {
            for (Map.Entry<String, Object> entry : featureSection.getValues(false).entrySet()) {
                Boolean value = readPersistedBoolean(entry.getValue());
                if (value != null) features.put(entry.getKey(), value);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        ConfigurationSection settingSection = yaml.getConfigurationSection("settings");
        if (settingSection != null) {
            for (Map.Entry<String, Object> entry : settingSection.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    values.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        state.replace(features, values);
    }

    public void requestSave() {
        dirty.set(true);
        scheduleWriter();
    }

    private void scheduleWriter() {
        if (shuttingDown || !writeScheduled.compareAndSet(false, true)) return;
        SchedulerUtils.runAsync(() -> {
            try {
                while (!shuttingDown && dirty.getAndSet(false)) {
                    synchronized (fileLock) {
                        if (!shuttingDown) save(state.snapshot());
                    }
                }
            } finally {
                writeScheduled.set(false);
                if (dirty.get()) scheduleWriter();
            }
        });
    }

    public void flush() {
        shuttingDown = true;
        synchronized (fileLock) {
            save(state.snapshot());
        }
    }

    private void save(AdminState.Snapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.desiredFeatures().forEach((key, value) -> yaml.set("features." + key, value));
        snapshot.settingValues().forEach((path, value) -> yaml.set("settings." + path, value));
        try {
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save administrative configuration", exception);
        }
    }

    static Boolean readPersistedBoolean(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }
}
