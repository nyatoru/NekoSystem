package com.nyarutoru.nekoplugin.utils;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for scheduling tasks with Folia and Paper/Spigot compatibility.
 * Automatically detects the server type and uses the appropriate scheduler.
 */
public class SchedulerUtils {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            // Not Folia
        }
        IS_FOLIA = folia;
    }

    private SchedulerUtils() {
    }

    /**
     * Check if running on Folia server.
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    // ==================== Entity-based scheduling ====================

    /**
     * Run a task for an entity (uses Entity scheduler on Folia).
     * This is the preferred method for player-related tasks.
     */
    public static void runAtEntity(Entity entity, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(getPlugin(), t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(getPlugin(), task);
        }
    }

    /**
     * Run a task for an entity after a delay.
     */
    public static void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(getPlugin(), t -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks);
        }
    }

    // ==================== Location-based scheduling ====================

    /**
     * Run a task at a specific location (uses Region scheduler on Folia).
     * This is the preferred method for block-related tasks.
     */
    public static void runAtLocation(Location location, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().run(getPlugin(), location, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(getPlugin(), task);
        }
    }

    /**
     * Run a task at a specific location after a delay.
     */
    public static void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(getPlugin(), location, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks);
        }
    }

    // ==================== Global scheduling ====================

    /**
     * Run a task on the global region (for non-location-specific tasks on Folia).
     */
    public static void runGlobal(Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(getPlugin(), t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(getPlugin(), task);
        }
    }

    /**
     * Run a task on the global region after a delay.
     */
    public static void runGlobalLater(Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(getPlugin(), t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks);
        }
    }

    /**
     * Run a repeating task on the global region.
     */
    public static void runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(getPlugin(), t -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(getPlugin(), task, delayTicks, periodTicks);
        }
    }

    // ==================== Async scheduling ====================

    /**
     * Run a task asynchronously.
     */
    public static void runAsync(Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(getPlugin(), t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), task);
        }
    }

    /**
     * Run a task asynchronously after a delay.
     */
    public static void runAsyncLater(Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            long delayMs = delayTicks * 50; // 1 tick = 50ms
            Bukkit.getAsyncScheduler().runDelayed(getPlugin(), t -> task.run(), delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), task, delayTicks);
        }
    }

    /**
     * Run an async repeating task. Returns BukkitTask on Paper, null on Folia.
     */
    public static BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            long delayMs = delayTicks * 50;
            long periodMs = periodTicks * 50;
            Bukkit.getAsyncScheduler().runAtFixedRate(getPlugin(), t -> task.run(), delayMs, periodMs,
                    TimeUnit.MILLISECONDS);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), task, delayTicks, periodTicks);
        }
    }

    // ==================== Legacy compatibility methods (return BukkitTask)
    // ====================
    // These maintain backward compatibility with existing code that expects
    // BukkitTask return

    /**
     * Run a task synchronously. Returns BukkitTask on Paper, null on Folia.
     *
     * @deprecated Use runGlobal() or runAtEntity() instead
     */
    @Deprecated
    public static BukkitTask runSync(Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(getPlugin(), t -> task.run());
            return null;
        } else {
            return Bukkit.getScheduler().runTask(getPlugin(), task);
        }
    }

    /**
     * Run a task synchronously after a delay. Returns BukkitTask on Paper, null on
     * Folia.
     *
     * @deprecated Use runGlobalLater() or runAtEntityLater() instead
     */
    @Deprecated
    public static BukkitTask runSyncLater(Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(getPlugin(), t -> task.run(), delayTicks);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks);
        }
    }

    /**
     * Run a repeating task synchronously. Returns BukkitTask on Paper, null on
     * Folia.
     *
     * @deprecated Use runGlobalTimer() instead
     */
    @Deprecated
    public static BukkitTask runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(getPlugin(), t -> task.run(), delayTicks, periodTicks);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(getPlugin(), task, delayTicks, periodTicks);
        }
    }

    /**
     * Alias for backward compatibility.
     */
    public static void runLater(Runnable task, long delayTicks) {
        runGlobalLater(task, delayTicks);
    }

    // ==================== Utility methods ====================

    /**
     * Converts seconds to ticks.
     */
    public static long secondsToTicks(double seconds) {
        return (long) (seconds * 20);
    }

    /**
     * Converts minutes to ticks.
     */
    public static long minutesToTicks(double minutes) {
        return (long) (minutes * 20 * 60);
    }

    /**
     * Cancels a task safely (null-check included).
     * Note: On Folia, BukkitTask is not used, so this will be a no-op for Folia
     * tasks.
     */
    public static void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private static Plugin getPlugin() {
        return NekoPlugin.getInstance();
    }
}
