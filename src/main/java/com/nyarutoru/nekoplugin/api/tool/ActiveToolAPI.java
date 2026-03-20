package com.nyarutoru.nekoplugin.api.tool;

import com.nyarutoru.nekoplugin.utils.ComponentUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * API for managing active tool states.
 * Handles shift-activation, persistent action bars, and proper cancellation.
 * <p>
 * Features:
 * - Rapid shift activation (10 shifts within 3 seconds)
 * - Prevents re-activation while already active
 * - Persistent action bar while active
 * - Cancels on: item swap, tool break, death, teleport, logout
 * - Automatic cleanup of timer tasks to prevent memory leaks
 */
public class ActiveToolAPI {

    private static final int SHIFTS_REQUIRED = 10;
    private static final long SHIFT_TIMEOUT_MS = 3000;
    private static final long ACTION_BAR_REFRESH_TICKS = 20L;
    private static ActiveToolAPI instance;
    // Player activation state (keyed by UUID:toolName for tool-specific tracking)
    private final Map<String, Integer> shiftCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastShiftTime = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> shiftTimeoutTasks = new ConcurrentHashMap<>();
    // Active tool state
    private final Map<UUID, ActiveToolState> activeTools = new ConcurrentHashMap<>();
    // Action bar timer tasks per player (for cleanup on deactivate)
    private final Map<UUID, BukkitTask> actionBarTasks = new ConcurrentHashMap<>();

    private ActiveToolAPI() {
    }

    public static ActiveToolAPI getInstance() {
        if (instance == null) {
            instance = new ActiveToolAPI();
        }
        return instance;
    }

    /**
     * Called when a player presses shift.
     */
    public void onShift(Player player, String toolName, Predicate<Player> canActivate, Runnable onActivate) {
        UUID uuid = player.getUniqueId();
        String key = uuid + ":" + toolName;

        if (isActive(player, toolName)) {
            return;
        }

        // If player already has a different tool active, ignore this shift
        if (hasActiveTool(player)) {
            return;
        }

        if (!canActivate.test(player)) {
            // Only reset this specific tool's count, not all tools
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastShiftTime.get(key);

        if (lastTime != null && (now - lastTime) > SHIFT_TIMEOUT_MS) {
            resetShiftCount(key);
        }

        int count = shiftCount.getOrDefault(key, 0) + 1;
        shiftCount.put(key, count);
        lastShiftTime.put(key, now);

        showActivationProgress(player, count);
        startShiftTimeout(player, key);

        if (count >= SHIFTS_REQUIRED) {
            resetShiftCount(key);
            activate(player, toolName, onActivate);
        }
    }

    private void showActivationProgress(Player player, int count) {
        int progress = Math.min(count, SHIFTS_REQUIRED);
        player.sendActionBar(ComponentUtils.progressBar(progress, SHIFTS_REQUIRED, '█', '░'));
    }

    private void startShiftTimeout(Player player, String key) {
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));

        SchedulerUtils.runAtEntityLater(player, () -> {
            if (shiftCount.containsKey(key)) {
                resetShiftCount(key);
                if (player.isOnline()) {
                    player.sendActionBar(ComponentUtils.timeoutMessage());
                }
            }
        }, SchedulerUtils.secondsToTicks(3));
    }

    private void resetShiftCount(String key) {
        shiftCount.remove(key);
        lastShiftTime.remove(key);
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));
    }

    /**
     * Activates a tool for the player.
     * Starts a persistent action bar display that refreshes every second.
     */
    public void activate(Player player, String toolName, Runnable onActivate) {
        UUID uuid = player.getUniqueId();

        ActiveToolState state = new ActiveToolState(toolName, player.getInventory().getItemInMainHand().clone());
        activeTools.put(uuid, state);

        // Cancel any existing action bar task for this player (safety check)
        BukkitTask existingTask = actionBarTasks.remove(uuid);
        SchedulerUtils.cancelTask(existingTask);

        // Start new action bar refresh timer and track it
        BukkitTask timerTask = SchedulerUtils.runGlobalTimer(() -> {
            if (player.isOnline() && isActive(player, toolName)) {
                player.sendActionBar(ComponentUtils.activeStatus(toolName));
            }
        }, 0, ACTION_BAR_REFRESH_TICKS);

        // Track the timer task for cleanup (null on Folia, which is fine)
        actionBarTasks.put(uuid, timerTask);

        player.sendActionBar(ComponentUtils.activeStatus(toolName));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);

        if (onActivate != null) {
            onActivate.run();
        }
    }

    /**
     * Deactivates the tool for a player.
     * Cancels the action bar timer and cleans up all associated state.
     */
    public void deactivate(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        ActiveToolState state = activeTools.remove(uuid);

        if (state != null) {
            player.sendActionBar(ComponentUtils.disabledStatus(state.toolName, reason));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

            // Cancel the action bar timer task
            BukkitTask timerTask = actionBarTasks.remove(uuid);
            SchedulerUtils.cancelTask(timerTask);

            // Reset shift count for this specific tool
            String key = uuid + ":" + state.toolName;
            resetShiftCount(key);
        }
    }

    public boolean isActive(Player player, String toolName) {
        ActiveToolState state = activeTools.get(player.getUniqueId());
        return state != null && state.toolName.equals(toolName);
    }

    public boolean hasActiveTool(Player player) {
        return activeTools.containsKey(player.getUniqueId());
    }

    public String getActiveToolName(Player player) {
        ActiveToolState state = activeTools.get(player.getUniqueId());
        return state != null ? state.toolName : null;
    }

    public void onItemSwitch(Player player) {
        if (hasActiveTool(player)) {
            deactivate(player, "item switched");
        }
    }

    public void onDeath(Player player) {
        if (hasActiveTool(player)) {
            deactivate(player, "died");
        }
    }

    public void onTeleport(Player player) {
        if (hasActiveTool(player)) {
            deactivate(player, "teleported");
        }
    }

    public void onQuit(Player player) {
        cleanup(player);
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        String uuidStr = uuid.toString();

        // Remove all shift counts for this player (all tools)
        shiftCount.keySet().removeIf(key -> key.startsWith(uuidStr));
        lastShiftTime.keySet().removeIf(key -> key.startsWith(uuidStr));
        shiftTimeoutTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(uuidStr)) {
                SchedulerUtils.cancelTask(entry.getValue());
                return true;
            }
            return false;
        });

        // Cancel action bar timer task
        BukkitTask timerTask = actionBarTasks.remove(uuid);
        SchedulerUtils.cancelTask(timerTask);

        activeTools.remove(uuid);
    }

    public void shutdown() {
        // Cancel all shift timeout tasks
        for (BukkitTask task : shiftTimeoutTasks.values()) {
            task.cancel();
        }
        shiftTimeoutTasks.clear();

        // Cancel all action bar timer tasks
        for (BukkitTask task : actionBarTasks.values()) {
            task.cancel();
        }
        actionBarTasks.clear();

        shiftCount.clear();
        lastShiftTime.clear();
        activeTools.clear();
    }

    private record ActiveToolState(String toolName, ItemStack originalItem) {
    }
}
