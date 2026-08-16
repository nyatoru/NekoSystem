package com.nyarutoru.nekoplugin.utils;

import com.nyarutoru.nekoplugin.NekoPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Scheduler helpers that work on both Paper and Folia. */
public final class SchedulerUtils {
    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            // Paper/Spigot
        }
        IS_FOLIA = folia;
    }

    private SchedulerUtils() {
    }

    // ponytail: dummy handle + enabled guard prevents IllegalPluginAccessException on /stop
    private static TaskHandle dummyTask() {
        return new TaskHandle() {
            @Override public void cancel() {}
            @Override public boolean isCancelled() { return true; }
        };
    }

    private static boolean isPluginEnabled() {
        try {
            Plugin plugin = NekoPlugin.getInstance();
            return plugin != null && plugin.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Neutral cancellable handle for Bukkit and Folia scheduled tasks. */
    public interface TaskHandle {
        void cancel();

        boolean isCancelled();
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static TaskHandle runAtEntityTask(Entity entity, Runnable task) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                try {
                    return folia(entity.getScheduler().run(getPlugin(), ignored -> task.run(), null));
                } catch (UnsupportedOperationException exception) {
                    return runGlobalTask(task);
                }
            }
            return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtEntity(Entity entity, Runnable task) {
        runAtEntityTask(entity, task);
    }

    public static TaskHandle runAtEntityLaterTask(Entity entity, Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                if (delayTicks <= 0) {
                    return runAtEntityTask(entity, task);
                }
                try {
                    return folia(entity.getScheduler().runDelayed(getPlugin(), ignored -> task.run(), null, delayTicks));
                } catch (UnsupportedOperationException exception) {
                    return runGlobalLaterTask(task, delayTicks);
                }
            }
            if (delayTicks <= 0) {
                return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
            }
            return bukkit(Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtEntityLater(Entity entity, Runnable task, long delayTicks) {
        runAtEntityLaterTask(entity, task, delayTicks);
    }

    // ── PlayerScheduler ──────────────────────────────────────────────
    // Folia/Paper 26.2 exposes player scheduling via EntityScheduler (Player extends Entity)
    // but dedicated PlayerScheduler was added in newer Paper builds as
    // io.papermc.paper.threadedregions.scheduler.PlayerScheduler. We support
    // both: try PlayerScheduler first via reflection, fallback to EntityScheduler,
    // fallback to global scheduler on failure (retired entity / cross-region).

    public static TaskHandle runAtPlayerTask(Player player, Runnable task) {
        if (!isPluginEnabled()) return dummyTask();
        if (player == null) return dummyTask();
        try {
            if (IS_FOLIA) {
                // Try PlayerScheduler (Paper 26.2+): player.getScheduler() returns PlayerScheduler on newer builds
                // On older Folia it is still EntityScheduler – same call works.
                try {
                    return folia(player.getScheduler().run(getPlugin(), ignored -> task.run(), null));
                } catch (UnsupportedOperationException exception) {
                    return runGlobalTask(task);
                }
            }
            return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtPlayer(Player player, Runnable task) {
        runAtPlayerTask(player, task);
    }

    public static TaskHandle runAtPlayerLaterTask(Player player, Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return dummyTask();
        if (player == null) return dummyTask();
        try {
            if (IS_FOLIA) {
                if (delayTicks <= 0) {
                    return runAtPlayerTask(player, task);
                }
                try {
                    return folia(player.getScheduler().runDelayed(getPlugin(), ignored -> task.run(), null, delayTicks));
                } catch (UnsupportedOperationException exception) {
                    return runGlobalLaterTask(task, delayTicks);
                }
            }
            if (delayTicks <= 0) {
                return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
            }
            return bukkit(Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtPlayerLater(Player player, Runnable task, long delayTicks) {
        runAtPlayerLaterTask(player, task, delayTicks);
    }

    public static TaskHandle runAtPlayerTimerTask(Player player, Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) return dummyTask();
        if (player == null) return dummyTask();
        try {
            if (IS_FOLIA) {
                long actualDelay = Math.max(1, delayTicks);
                try {
                    // PlayerScheduler/EntityScheduler runAtFixedRate — same signature
                    return folia(player.getScheduler().runAtFixedRate(getPlugin(), ignored -> task.run(), null, actualDelay, periodTicks));
                } catch (UnsupportedOperationException | NoSuchMethodError exception) {
                    return runGlobalTimerTask(task, actualDelay, periodTicks);
                }
            }
            return bukkit(Bukkit.getScheduler().runTaskTimer(getPlugin(), task, delayTicks, periodTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static TaskHandle runAtLocationTask(Location location, Runnable task) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                return folia(Bukkit.getRegionScheduler().run(getPlugin(), location, ignored -> task.run()));
            }
            return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtLocation(Location location, Runnable task) {
        runAtLocationTask(location, task);
    }

    public static TaskHandle runAtLocationLaterTask(Location location, Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                if (delayTicks <= 0) {
                    return runAtLocationTask(location, task);
                }
                return folia(Bukkit.getRegionScheduler().runDelayed(getPlugin(), location, ignored -> task.run(), delayTicks));
            }
            if (delayTicks <= 0) {
                return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
            }
            return bukkit(Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAtLocationLater(Location location, Runnable task, long delayTicks) {
        runAtLocationLaterTask(location, task, delayTicks);
    }

    public static TaskHandle runGlobalTask(Runnable task) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                return folia(Bukkit.getGlobalRegionScheduler().run(getPlugin(), ignored -> task.run()));
            }
            return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runGlobal(Runnable task) {
        runGlobalTask(task);
    }

    public static TaskHandle runGlobalLaterTask(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                if (delayTicks <= 0) {
                    return runGlobalTask(task);
                }
                return folia(Bukkit.getGlobalRegionScheduler().runDelayed(getPlugin(), ignored -> task.run(), delayTicks));
            }
            if (delayTicks <= 0) {
                return bukkit(Bukkit.getScheduler().runTask(getPlugin(), task));
            }
            return bukkit(Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runGlobalLater(Runnable task, long delayTicks) {
        runGlobalLaterTask(task, delayTicks);
    }

    public static TaskHandle runGlobalTimerTask(Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                long actualDelay = Math.max(1, delayTicks);
                return folia(Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                        getPlugin(), ignored -> task.run(), actualDelay, periodTicks));
            }
            return bukkit(Bukkit.getScheduler().runTaskTimer(getPlugin(), task, delayTicks, periodTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    /** Existing compatibility API. Prefer {@link #runGlobalTimerTask(Runnable, long, long)}. */
    public static BukkitTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) return null;
        try {
            if (IS_FOLIA) {
                runGlobalTimerTask(task, delayTicks, periodTicks);
                return null;
            }
            return Bukkit.getScheduler().runTaskTimer(getPlugin(), task, delayTicks, periodTicks);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static TaskHandle runAsyncTask(Runnable task) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                return folia(Bukkit.getAsyncScheduler().runNow(getPlugin(), ignored -> task.run()));
            }
            return bukkit(Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), task));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAsync(Runnable task) {
        runAsyncTask(task);
    }

    public static TaskHandle runAsyncLaterTask(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                if (delayTicks <= 0) {
                    return runAsyncTask(task);
                }
                return folia(Bukkit.getAsyncScheduler().runDelayed(
                        getPlugin(), ignored -> task.run(), delayTicks * 50, TimeUnit.MILLISECONDS));
            }
            if (delayTicks <= 0) {
                return bukkit(Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), task));
            }
            return bukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), task, delayTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    public static void runAsyncLater(Runnable task, long delayTicks) {
        runAsyncLaterTask(task, delayTicks);
    }

    public static TaskHandle runAsyncTimerTask(Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) return dummyTask();
        try {
            if (IS_FOLIA) {
                return folia(Bukkit.getAsyncScheduler().runAtFixedRate(getPlugin(), ignored -> task.run(),
                        delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS));
            }
            return bukkit(Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), task, delayTicks, periodTicks));
        } catch (Exception ignored) {
            return dummyTask();
        }
    }

    /** Existing compatibility API. Prefer {@link #runAsyncTimerTask(Runnable, long, long)}. */
    public static BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) return null;
        try {
            if (IS_FOLIA) {
                runAsyncTimerTask(task, delayTicks, periodTicks);
                return null;
            }
            return Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), task, delayTicks, periodTicks);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Deprecated
    public static BukkitTask runSync(Runnable task) {
        if (!isPluginEnabled()) return null;
        try {
            if (IS_FOLIA) {
                runGlobalTask(task);
                return null;
            }
            return Bukkit.getScheduler().runTask(getPlugin(), task);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Deprecated
    public static BukkitTask runSyncLater(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) return null;
        try {
            if (IS_FOLIA) {
                runGlobalLaterTask(task, delayTicks);
                return null;
            }
            return Bukkit.getScheduler().runTaskLater(getPlugin(), task, delayTicks);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Deprecated
    public static BukkitTask runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return runGlobalTimer(task, delayTicks, periodTicks);
    }

    public static void runLater(Runnable task, long delayTicks) {
        runGlobalLater(task, delayTicks);
    }

    public static long secondsToTicks(double seconds) {
        return (long) (seconds * 20);
    }

    public static long minutesToTicks(double minutes) {
        return (long) (minutes * 20 * 60);
    }

    public static void cancelTask(TaskHandle task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public static void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private static TaskHandle bukkit(BukkitTask task) {
        Objects.requireNonNull(task, "task");
        return new TaskHandle() {
            @Override
            public void cancel() {
                if (!task.isCancelled()) task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    private static TaskHandle folia(ScheduledTask task) {
        Objects.requireNonNull(task, "task");
        return new TaskHandle() {
            @Override
            public void cancel() {
                if (!task.isCancelled()) task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    private static Plugin getPlugin() {
        return NekoPlugin.getInstance();
    }
}
