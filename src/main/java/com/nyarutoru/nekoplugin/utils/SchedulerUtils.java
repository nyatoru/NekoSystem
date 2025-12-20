package com.nyarutoru.nekoplugin.utils;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class for scheduling tasks asynchronously and synchronously.
 * Provides helper methods for common scheduling patterns.
 */
public class SchedulerUtils {

    private SchedulerUtils() {
    }

    /**
     * Runs a task synchronously on the main thread.
     */
    public static BukkitTask runSync(Runnable task) {
        return Bukkit.getScheduler().runTask(NekoPlugin.getInstance(), task);
    }

    /**
     * Runs a task asynchronously off the main thread.
     */
    public static BukkitTask runAsync(Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(NekoPlugin.getInstance(), task);
    }

    /**
     * Runs a task synchronously after a delay.
     * 
     * @param task       The task to run
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     */
    public static BukkitTask runSyncLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(NekoPlugin.getInstance(), task, delayTicks);
    }

    /**
     * Runs a task asynchronously after a delay.
     * 
     * @param task       The task to run
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     */
    public static BukkitTask runAsyncLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(NekoPlugin.getInstance(), task, delayTicks);
    }

    /**
     * Runs a repeating task synchronously.
     * 
     * @param task        The task to run
     * @param delayTicks  Initial delay in ticks
     * @param periodTicks Period between executions in ticks
     */
    public static BukkitTask runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(NekoPlugin.getInstance(), task, delayTicks, periodTicks);
    }

    /**
     * Runs a repeating task asynchronously.
     * 
     * @param task        The task to run
     * @param delayTicks  Initial delay in ticks
     * @param periodTicks Period between executions in ticks
     */
    public static BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(NekoPlugin.getInstance(), task, delayTicks,
                periodTicks);
    }

    /**
     * Runs an async operation and then processes the result on the main thread.
     * 
     * @param asyncTask    The async task that produces a result
     * @param syncCallback The callback to process the result on the main thread
     */
    public static <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        runAsync(() -> {
            T result = asyncTask.get();
            runSync(() -> syncCallback.accept(result));
        });
    }

    /**
     * Runs an async operation and returns a CompletableFuture.
     * The future completes on the async thread.
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runAsync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Creates a BukkitRunnable that can be scheduled.
     */
    public static BukkitRunnable createRunnable(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        };
    }

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
     */
    public static void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
