package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

/** Base feature implementation with owned listener and task cleanup. */
public abstract class AbstractFeature implements Feature {
    private final String id;
    private final String name;
    protected boolean enabled;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<SchedulerUtils.TaskHandle> tasks = new ArrayList<>();

    protected AbstractFeature(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        enabled = true;
    }

    /**
     * Always attempts every owned cleanup step and leaves the feature disabled.
     * The first failure is rethrown after later failures have been suppressed onto it.
     */
    @Override
    public void onDisable() {
        Throwable failure = null;
        try {
            for (Listener listener : List.copyOf(listeners)) {
                try {
                    HandlerList.unregisterAll(listener);
                } catch (Throwable throwable) {
                    failure = collect(failure, throwable);
                }
            }
            listeners.clear();

            for (SchedulerUtils.TaskHandle task : List.copyOf(tasks)) {
                try {
                    SchedulerUtils.cancelTask(task);
                } catch (Throwable throwable) {
                    failure = collect(failure, throwable);
                }
            }
            tasks.clear();

            try {
                cleanup();
            } catch (Throwable throwable) {
                failure = collect(failure, throwable);
            }
        } finally {
            enabled = false;
        }
        rethrow(failure);
    }

    protected final void registerListener(Listener listener, NekoPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Tracks an already scheduled task for disable and failed-enable rollback. */
    protected final <T extends SchedulerUtils.TaskHandle> T ownTask(T task) {
        if (task != null) tasks.add(task);
        return task;
    }

    @Deprecated
    protected void registerListenerManual(Listener listener, NekoPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    protected void cleanup() {
        // Optional feature-specific cleanup.
    }

    private static Throwable collect(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }
}
