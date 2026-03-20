package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for plugin features.
 * Provides common functionality to reduce boilerplate code in feature implementations.
 * <p>
 * Features should extend this class and implement {@link #onEnable(NekoPlugin)}.
 * The {@link #onDisable()} method is already implemented to handle cleanup.
 * <p>
 * This class automatically tracks registered listeners and unregisters them on disable.
 *
 * @see Feature
 */
public abstract class AbstractFeature implements Feature {

    /**
     * The unique identifier for this feature.
     */
    private final String id;

    /**
     * The display name for this feature.
     */
    private final String name;

    /**
     * Whether this feature is currently enabled.
     */
    protected boolean enabled = false;

    /**
     * List of registered listeners for automatic cleanup.
     */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * Creates a new feature with the specified ID and name.
     *
     * @param id   the unique identifier for this feature
     * @param name the display name for this feature
     */
    protected AbstractFeature(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the enabled flag to true after successful initialization.
     * Override this method to initialize your feature.
     * Call {@code super.onEnable(plugin)} if you need custom cleanup before the parent implementation.
     */
    @Override
    public void onEnable(NekoPlugin plugin) {
        this.enabled = true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Automatically unregisters all listeners registered via {@link #registerListener(Listener)}.
     * Override this method if you need additional cleanup, but remember to call {@code super.onDisable()}.
     */
    @Override
    public void onDisable() {
        // Unregister all tracked listeners
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();

        // Perform feature-specific cleanup
        cleanup();

        this.enabled = false;
    }

    /**
     * Registers a listener and tracks it for automatic cleanup on disable.
     * <p>
     * This method automatically registers the listener with the plugin's event system.
     * All listeners registered through this method will be automatically unregistered
     * when the feature is disabled.
     *
     * @param listener the listener to register
     * @param plugin   the plugin instance for registration
     */
    protected void registerListener(Listener listener, NekoPlugin plugin) {
        listeners.add(listener);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Registers a listener without tracking.
     * <p>
     * Use this method when you want to manually manage the listener lifecycle.
     * The listener will NOT be automatically unregistered on disable.
     *
     * @param listener the listener to register
     * @param plugin   the plugin instance for registration
     * @deprecated Use {@link #registerListener(Listener, NekoPlugin)} for automatic cleanup
     */
    @Deprecated
    protected void registerListenerManual(Listener listener, NekoPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    /**
     * Called during {@link #onDisable()} after listeners are unregistered.
     * <p>
     * Override this method to perform feature-specific cleanup logic.
     * This is called before the enabled flag is set to false.
     * <p>
     * Default implementation does nothing.
     */
    protected void cleanup() {
        // Override for custom cleanup logic
    }
}
