package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Manages deterministic, serialized feature lifecycle transitions. */
public class FeatureManager {
    private static volatile FeatureManager instance;
    private final Map<String, Feature> features = new LinkedHashMap<>();
    private final java.util.logging.Logger fallbackLogger;
    private NekoPlugin plugin;

    FeatureManager() {
        this(java.util.logging.Logger.getLogger(FeatureManager.class.getName()));
    }

    FeatureManager(java.util.logging.Logger fallbackLogger) {
        this.fallbackLogger = Objects.requireNonNull(fallbackLogger, "fallbackLogger");
    }

    public static FeatureManager getInstance() {
        if (instance == null) {
            synchronized (FeatureManager.class) {
                if (instance == null) instance = new FeatureManager();
            }
        }
        return instance;
    }

    public synchronized void initialize(NekoPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void registerFeature(Feature feature) {
        Objects.requireNonNull(feature, "feature");
        String id = Objects.requireNonNull(feature.getId(), "feature id");
        if (features.containsKey(id)) {
            logger().warning("Feature already registered: " + id);
            return;
        }
        features.put(id, feature);
        logger().info("Registered feature: " + feature.getName() + " (" + id + ")");
    }

    public synchronized TransitionResult enable(String id) {
        Feature feature = features.get(id);
        if (feature == null) return result(id, TransitionStatus.NOT_FOUND, false, "Unknown feature");
        if (safeIsEnabled(feature)) return result(id, TransitionStatus.ALREADY_IN_STATE, true, "Already enabled");

        try {
            feature.onEnable(plugin);
            if (safeIsEnabled(feature)) return result(id, TransitionStatus.CHANGED, true, "Enabled");
            return failedEnable(feature, null, "Feature did not report enabled");
        } catch (Throwable failure) {
            if (isFatal(failure)) throw (Error) failure;
            return failedEnable(feature, failure, message(failure));
        }
    }

    private TransitionResult failedEnable(Feature feature, Throwable enableFailure, String detail) {
        if (enableFailure != null) {
            logger().log(Level.SEVERE, "Failed to enable feature: " + safeName(feature), enableFailure);
        } else {
            logger().severe("Failed to enable feature: " + safeName(feature) + " (" + detail + ")");
        }
        Throwable rollbackFailure = null;
        try {
            feature.onDisable();
        } catch (Throwable failure) {
            if (isFatal(failure)) throw (Error) failure;
            rollbackFailure = failure;
            logger().log(Level.SEVERE, "Failed to roll back feature: " + safeName(feature), failure);
            if (enableFailure != null && enableFailure != failure) enableFailure.addSuppressed(failure);
        }
        boolean enabled = safeIsEnabled(feature);
        String suffix = rollbackFailure == null ? "" : "; rollback failed: " + message(rollbackFailure);
        return result(feature.getId(), TransitionStatus.FAILED, enabled, detail + suffix);
    }

    public synchronized TransitionResult disable(String id) {
        Feature feature = features.get(id);
        if (feature == null) return result(id, TransitionStatus.NOT_FOUND, false, "Unknown feature");
        if (!safeIsEnabled(feature)) return result(id, TransitionStatus.ALREADY_IN_STATE, false, "Already disabled");
        try {
            feature.onDisable();
            if (safeIsEnabled(feature)) return result(id, TransitionStatus.FAILED, true, "Feature did not report disabled");
            return result(id, TransitionStatus.CHANGED, false, "Disabled");
        } catch (Throwable failure) {
            if (isFatal(failure)) throw (Error) failure;
            logger().log(Level.SEVERE, "Failed to disable feature: " + safeName(feature), failure);
            return result(id, TransitionStatus.FAILED, safeIsEnabled(feature), message(failure));
        }
    }

    public synchronized TransitionResult setEnabled(String id, boolean enabled) {
        return enabled ? enable(id) : disable(id);
    }

    public void enableAll() {
        enableDesired(ignored -> true);
    }

    public synchronized void enableDesired(Predicate<String> desiredEnabled) {
        Objects.requireNonNull(desiredEnabled, "desiredEnabled");
        int enabled = 0;
        int failed = 0;
        for (Feature feature : features.values()) {
            boolean desired;
            try {
                desired = desiredEnabled.test(feature.getId());
            } catch (Throwable failure) {
                if (isFatal(failure)) throw (Error) failure;
                failed++;
                logger().log(Level.SEVERE, "Failed to resolve desired state for feature: " + safeName(feature), failure);
                continue;
            }
            if (!desired) continue;
            TransitionResult transition = enable(feature.getId());
            if (transition.success()) enabled++; else failed++;
        }
        if (failed == 0) logger().info("✓ Enabled " + enabled + " features successfully");
        else logger().warning("Enabled " + enabled + " features (" + failed + " failed)");
    }

    public synchronized void disableAll() {
        int disabled = 0;
        int failed = 0;
        Feature[] ordered = features.values().toArray(Feature[]::new);
        for (int i = ordered.length - 1; i >= 0; i--) {
            Feature feature = ordered[i];
            if (!safeIsEnabled(feature)) continue;
            TransitionResult transition = disable(feature.getId());
            if (transition.success()) disabled++; else failed++;
        }
        if (failed == 0) logger().info("✓ Disabled " + disabled + " features successfully");
        else logger().warning("Disabled " + disabled + " features (" + failed + " failed)");
    }

    /** Disables features, then drops registrations and the plugin reference for a clean reload. */
    public synchronized void shutdown() {
        disableAll();
        features.clear();
        plugin = null;
    }

    /** Alias for tests/reloads that need the same complete lifecycle reset. */
    public synchronized void reset() {
        shutdown();
    }

    private boolean safeIsEnabled(Feature feature) {
        try {
            return feature.isEnabled();
        } catch (Throwable failure) {
            if (isFatal(failure)) throw (Error) failure;
            logger().log(Level.SEVERE, "Failed to inspect feature state: " + safeName(feature), failure);
            return false;
        }
    }

    private String safeName(Feature feature) {
        try {
            return feature.getName();
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof LinkageError;
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static TransitionResult result(String id, TransitionStatus status, boolean enabled, String message) {
        return new TransitionResult(id, status, enabled, message);
    }

    private java.util.logging.Logger logger() {
        return plugin == null ? fallbackLogger : plugin.getLogger();
    }

    public synchronized Feature getFeature(String id) { return features.get(id); }
    public synchronized Map<String, Feature> getAllFeatures() { return new LinkedHashMap<>(features); }
    public synchronized int getFeatureCount() { return features.size(); }

    public enum TransitionStatus { CHANGED, ALREADY_IN_STATE, NOT_FOUND, FAILED }

    public record TransitionResult(String featureId, TransitionStatus status, boolean enabled, String message) {
        public boolean success() {
            return status == TransitionStatus.CHANGED || status == TransitionStatus.ALREADY_IN_STATE;
        }
    }
}
