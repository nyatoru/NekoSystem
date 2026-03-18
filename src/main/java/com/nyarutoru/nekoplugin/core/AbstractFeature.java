package com.nyarutoru.nekoplugin.core;

/**
 * Abstract base class for features.
 * Provides common implementation for getId(), getName(), and isEnabled().
 * Subclasses only need to implement onEnable() and onDisable().
 */
public abstract class AbstractFeature implements Feature {

    private final String id;
    private final String name;
    private boolean enabled = false;

    protected AbstractFeature(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onEnable(com.nyarutoru.nekoplugin.NekoPlugin plugin) {
        onEnable();
        enabled = true;
    }

    /**
     * Called when the feature is enabled.
     * @param plugin The plugin instance
     */
    protected abstract void onEnable();

    @Override
    public void onDisable() {
        enabled = false;
    }
}
