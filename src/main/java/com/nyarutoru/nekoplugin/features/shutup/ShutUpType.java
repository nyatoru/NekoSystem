package com.nyarutoru.nekoplugin.features.shutup;

import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.IronGolem;

/**
 * The three "Shut Up" items and the mob types each one silences.
 */
public enum ShutUpType {

    ENDERMAN("Shut Up Enderman", "endermen", 5001, Enderman.class),
    GUARDIAN("Shut Up Guardian", "guardians", 5002, Guardian.class, ElderGuardian.class),
    IRON_GOLEM("Shut Up Iron Golem", "iron golems", 5003, IronGolem.class);

    private final String displayName;
    private final String targetName;
    private final int customModelData;
    private final Class<?>[] mobs;

    ShutUpType(String displayName, String targetName, int customModelData, Class<?>... mobs) {
        this.displayName = displayName;
        this.targetName = targetName;
        this.customModelData = customModelData;
        this.mobs = mobs;
    }

    public String displayName() {
        return displayName;
    }

    public String targetName() {
        return targetName;
    }

    public int customModelData() {
        return customModelData;
    }

    public boolean matches(Entity entity) {
        for (Class<?> mob : mobs) {
            if (mob.isInstance(entity)) return true;
        }
        return false;
    }

    public static boolean matchesAny(Entity entity) {
        for (ShutUpType type : values()) {
            if (type.matches(entity)) return true;
        }
        return false;
    }

    public static ShutUpType getByName(String name) {
        for (ShutUpType type : values()) {
            if (type.name().equals(name)) return type;
        }
        return null;
    }
}
