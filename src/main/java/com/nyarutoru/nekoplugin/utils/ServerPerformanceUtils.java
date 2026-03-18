package com.nyarutoru.nekoplugin.utils;

import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

/**
 * Utility class for getting server performance metrics with Folia compatibility.
 * <p>
 * On Folia, each region has its own TPS and MSPT.
 * On Paper/Spigot, uses standard Bukkit methods.
 */
public class ServerPerformanceUtils {

    private static final boolean IS_FOLIA = SchedulerUtils.isFolia();

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
            World overworld = Bukkit.getWorlds().getFirst();
            double[] regionTps = Bukkit.getServer().getRegionTPS(overworld.getSpawnLocation());
            return regionTps != null && regionTps.length > 0 ? regionTps[0] : 20.0;
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
            double[] regionTps = Bukkit.getServer().getRegionTPS(location);
            return regionTps != null && regionTps.length > 0 ? regionTps[0] : 20.0;
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
            return getFoliaRegionMSPT(location);
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
     * Gets the region-specific MSPT from Folia's internal regionizer API.
     *
     * @param location The location to check
     * @return The MSPT for that region, or 50.0 if unavailable
     */
    private static double getFoliaRegionMSPT(Location location) {
        try {
            ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;

            ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>
                    region = world.regioniser.getRegionAtSynchronised(chunkX, chunkZ);

            if (region == null) {
                return 50.0;
            }

            TickRegions.TickRegionData regionData = region.getData();
            final long currTime = System.nanoTime();
            final TickRegionScheduler.RegionScheduleHandle handle = regionData.getRegionSchedulingHandle();

            // Get 5-second average tick time for real-time data
            return handle.getTickReport5s(currTime).timePerTickData().segmentAll().average() / 1_000_000.0;

        } catch (Exception e) {
            return 50.0;
        }
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
