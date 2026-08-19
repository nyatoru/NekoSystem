package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.function.DoubleConsumer;

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
                // Old Folia: getRegionMSPT(Location); PaperMC/Folia#391 (open): getRegionAverageTickTimes(Location).
                getRegionMSPTMethod = serverClass.getMethod("getRegionMSPT", Location.class);
            } catch (NoSuchMethodException e) {
                try {
                    Class<?> serverClass = Bukkit.getServer().getClass();
                    getRegionMSPTMethod = serverClass.getMethod("getRegionAverageTickTimes", Location.class);
                } catch (NoSuchMethodException e2) {
                    getRegionMSPTMethod = null;
                }
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
     * On Folia: Returns the real measured region MSPT when called on the region's
     * tick thread (as entity-scheduler tasks are), else the best available
     * approximation for the region containing the location
     * On Paper/Spigot: Returns global MSPT (location is ignored)
     *
     * @param location The location to get MSPT for
     * @return The current MSPT
     */
    public static double getMSPT(Location location) {
        return getRegionMSPT(location);
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
     * Gets the average tick time (MSPT) for the region owning a location.
     * <p>
     * Mirrors Folia's pending {@code getRegionAverageTickTimes} API
     * (PaperMC/Folia#391) so the plugin does not depend on that PR being merged.
     * The 5-second average is returned, matching the real-time reading used for TPS.
     * <p>
     * On Folia this returns the true measured value when called on the region's tick
     * thread; off-thread it falls back to an approximation derived from region TPS.
     * Use {@link #getRegionMSPT(Location, DoubleConsumer)} for a correct reading from
     * any thread.
     *
     * @param location The location to get MSPT for
     * @return The current MSPT for the owning region
     */
    public static double getRegionMSPT(Location location) {
        if (!IS_FOLIA) {
            return Bukkit.getAverageTickTime();
        }
        if (getRegionMSPTMethod != null) {
            try {
                Object result = getRegionMSPTMethod.invoke(Bukkit.getServer(), location);
                if (result instanceof double[] array && array.length > 0) {
                    return array[0];
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        // Folia 26.2: real per-region MSPT is only readable on the region's tick
        // thread (throws UnsupportedOperationException otherwise)
        try {
            return Bukkit.getAverageTickTime();
        } catch (UnsupportedOperationException e) {
            double tps = getTPS(location);
            return tps > 0.0 ? 1000.0 / tps : 50.0;
        }
    }

    /**
     * Gets the real region MSPT for a location from any thread.
     * <p>
     * On Folia this schedules onto the region owning the location and reads the
     * measured value there, so it is correct even off-thread (unlike
     * {@link #getRegionMSPT(Location)}). On Paper/Spigot the global MSPT is
     * delivered directly.
     *
     * @param location The location to get MSPT for
     * @param consumer Receives the MSPT value
     */
    public static void getRegionMSPT(Location location, DoubleConsumer consumer) {
        if (!IS_FOLIA) {
            consumer.accept(Bukkit.getAverageTickTime());
            return;
        }
        SchedulerUtils.runAtLocationTask(location, () -> consumer.accept(getRegionMSPT(location)));
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
     * Checks if the server is running on Folia.
     *
     * @return true if Folia, false if Paper/Spigot
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
}
