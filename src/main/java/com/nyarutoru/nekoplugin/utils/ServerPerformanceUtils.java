package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Utility class for getting server performance metrics with Folia and Paper compatibility.
 * <p>
 * On Folia: Uses region-specific TPS and MSPT APIs
 * On Paper/Spigot: Uses standard Bukkit methods
 */
public class ServerPerformanceUtils {

    private static final boolean IS_FOLIA = SchedulerUtils.isFolia();
    private static Method getRegionTPSMethod = null;
    private static Method getRegionMSPTMethod = null;

    static {
        // Try to get Folia-specific methods via reflection
        if (IS_FOLIA) {
            try {
                Class<?> serverClass = Bukkit.getServer().getClass();
                getRegionTPSMethod = serverClass.getMethod("getRegionTPS", Location.class);
                getRegionMSPTMethod = serverClass.getMethod("getRegionMSPT", Location.class);
            } catch (NoSuchMethodException e) {
                // Methods not available, will use fallbacks
                getRegionTPSMethod = null;
                getRegionMSPTMethod = null;
            }
        }
    }

    private ServerPerformanceUtils() {
    }

    /**
     * Gets the current TPS (Ticks Per Second).
     * <p>
     * On Folia: Returns the 5-second average region TPS for the overworld spawn (real-time)
     * On Paper/Spigot: Returns actual TPS from last 1 minute average
     *
     * @return The current TPS
     */
    public static double getTPS() {
        if (IS_FOLIA) {
            // Get region TPS for overworld spawn (index 0 = 5 second average for real-time data)
            try {
                var overworld = Bukkit.getWorlds().getFirst();
                double[] regionTps = getRegionTPS(overworld.getSpawnLocation());
                return regionTps != null && regionTps.length > 0 ? regionTps[0] : 20.0;
            } catch (Exception e) {
                return 20.0;
            }
        } else {
            // Paper/Spigot: Use standard TPS array (0 = 1min, 1 = 5min, 2 = 15min)
            return Bukkit.getTPS()[0];
        }
    }

    /**
     * Gets the current TPS for a specific location.
     * <p>
     * On Folia: Returns the 5-second average region TPS for the region containing the location (real-time)
     * On Paper/Spigot: Returns global TPS (location is ignored)
     *
     * @param location The location to get TPS for (only used on Folia)
     * @return The current TPS
     */
    public static double getTPS(Location location) {
        if (IS_FOLIA) {
            // Index 0 = 5 second average for real-time data
            try {
                double[] regionTps = getRegionTPS(location);
                return regionTps != null && regionTps.length > 0 ? regionTps[0] : 20.0;
            } catch (Exception e) {
                return 20.0;
            }
        } else {
            return Bukkit.getTPS()[0];
        }
    }

    /**
     * Gets the current TPS for a player's current region.
     * <p>
     * On Folia: Returns the 1-minute average region TPS for the player's current region
     * On Paper/Spigot: Returns global TPS
     *
     * @param player The player
     * @return The current TPS for the player's region
     */
    public static double getTPS(Player player) {
        return getTPS(player.getLocation());
    }

    /**
     * Gets the current MSPT (Milliseconds Per Tick) for a specific location.
     * <p>
     * On Folia: Returns the region-specific MSPT for the region containing the location
     * On Paper/Spigot: Returns global MSPT (location is ignored)
     *
     * @param location The location to get MSPT for
     * @return The current MSPT
     */
    public static double getMSPT(Location location) {
        if (IS_FOLIA) {
            try {
                return getRegionMSPT(location);
            } catch (Exception e) {
                return Bukkit.getAverageTickTime();
            }
        } else {
            return Bukkit.getAverageTickTime();
        }
    }

    /**
     * Gets the current MSPT for a player's current region.
     * <p>
     * On Folia: Returns the region-specific MSPT for the player's current region
     * On Paper/Spigot: Returns global MSPT
     *
     * @param player The player
     * @return The current MSPT for the player's region
     */
    public static double getMSPT(Player player) {
        return getMSPT(player.getLocation());
    }

    /**
     * Gets region TPS using reflection for Folia compatibility.
     *
     * @param location The location to get TPS for
     * @return TPS array or null if not available
     */
    private static double[] getRegionTPS(Location location) {
        if (getRegionTPSMethod != null) {
            try {
                return (double[]) getRegionTPSMethod.invoke(Bukkit.getServer(), location);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Gets region MSPT using reflection for Folia compatibility.
     *
     * @param location The location to get MSPT for
     * @return MSPT value or default if not available
     */
    private static double getRegionMSPT(Location location) {
        if (getRegionMSPTMethod != null) {
            try {
                Object result = getRegionMSPTMethod.invoke(Bukkit.getServer(), location);
                if (result instanceof Number) {
                    return ((Number) result).doubleValue();
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return 50.0;
    }

    /**
     * Checks if the server is running on Folia.
     *
     * @return true if Folia, false if Paper/Spigot
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
}
