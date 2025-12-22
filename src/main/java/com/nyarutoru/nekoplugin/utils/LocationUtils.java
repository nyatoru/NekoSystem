package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Utility class for location-related operations.
 */
public class LocationUtils {

    private LocationUtils() {
    }

    /**
     * Generates a simple location key string without hashing.
     *
     * @param location The location
     * @return A string key in format "world_x_y_z"
     */
    public static String getSimpleLocationKey(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    /**
     * Generates a UUID-based location key using SHA-1 hashing.
     *
     * @param location The location
     * @return A UUID string generated from the location coordinates
     */
    public static String getLocationKey(Location location) {
        String rawKey = location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
        return UUID.nameUUIDFromBytes(rawKey.getBytes()).toString();
    }
}
