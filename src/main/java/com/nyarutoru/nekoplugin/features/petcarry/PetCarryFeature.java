package com.nyarutoru.nekoplugin.features.petcarry;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import org.bukkit.event.HandlerList;

public class PetCarryFeature implements Feature {

    public static final String ID = "pet_carry";
    public static final String NAME = "Pet Carry System";

    private boolean enabled = false;
    private PetCarryListener listener;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        listener = new PetCarryListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
