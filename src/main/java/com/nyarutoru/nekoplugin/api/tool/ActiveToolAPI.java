package com.nyarutoru.nekoplugin.api.tool;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.ComponentUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Manages active tool activation state and all associated scheduled tasks. */
public class ActiveToolAPI {
    private static final int DEFAULT_SHIFTS_REQUIRED = 10;
    private static final int DEFAULT_SHIFT_TIMEOUT_SECONDS = 5;
    private static final long DEFAULT_ACTION_BAR_REFRESH_TICKS = 20L;
    private static volatile ActiveToolAPI instance;

    private volatile int shiftsRequired = DEFAULT_SHIFTS_REQUIRED;
    private volatile int shiftTimeoutSeconds = DEFAULT_SHIFT_TIMEOUT_SECONDS;
    private volatile long actionBarRefreshTicks = DEFAULT_ACTION_BAR_REFRESH_TICKS;

    /**
     * Registers activation settings for a feature that uses this shared API.
     * Values affect future activation attempts; already scheduled timeout tasks retain
     * their original delay and are cancelled by the normal player cleanup paths.
     */
    public synchronized void registerSettings(SettingRegistry registry, AdminState state, String featureId) {
        SettingDescriptor<Integer> shifts = SettingDescriptor.integer(
                "activation-shifts-required", "Activation shifts required", DEFAULT_SHIFTS_REQUIRED,
                1, 100, ApplySemantics.FUTURE_ONLY, this::setShiftsRequired);
        SettingDescriptor<Integer> timeout = SettingDescriptor.integer(
                "activation-timeout-seconds", "Activation timeout (seconds)", DEFAULT_SHIFT_TIMEOUT_SECONDS,
                1, 60, ApplySemantics.FUTURE_ONLY, this::setShiftTimeoutSeconds);
        SettingDescriptor<Long> refresh = SettingDescriptor.longValue(
                "activation-action-bar-refresh-ticks", "Action bar refresh (ticks)", DEFAULT_ACTION_BAR_REFRESH_TICKS,
                1, 200, ApplySemantics.FUTURE_ONLY, this::setActionBarRefreshTicks);
        registry.register(featureId, shifts);
        registry.register(featureId, timeout);
        registry.register(featureId, refresh);
        applyStored(state, featureId, shifts);
        applyStored(state, featureId, timeout);
        applyStored(state, featureId, refresh);
    }

    public int getShiftsRequired() { return shiftsRequired; }

    public void setShiftsRequired(int value) { shiftsRequired = value; }

    public int getShiftTimeoutSeconds() { return shiftTimeoutSeconds; }

    public void setShiftTimeoutSeconds(int value) { shiftTimeoutSeconds = value; }

    public long getActionBarRefreshTicks() { return actionBarRefreshTicks; }

    public void setActionBarRefreshTicks(long value) { actionBarRefreshTicks = value; }

    private static <T> void applyStored(AdminState state, String featureId, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue(featureId, descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    private final Map<String, Integer> shiftCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastShiftTime = new ConcurrentHashMap<>();
    private final Map<String, SchedulerUtils.TaskHandle> shiftTimeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveToolState> activeTools = new ConcurrentHashMap<>();
    private final Map<UUID, ActionBarTask> actionBarTasks = new ConcurrentHashMap<>();

    private ActiveToolAPI() {
    }

    public static ActiveToolAPI getInstance() {
        if (instance == null) {
            synchronized (ActiveToolAPI.class) {
                if (instance == null) instance = new ActiveToolAPI();
            }
        }
        return instance;
    }

    public void onShift(Player player, String toolName, Predicate<Player> canActivate, Runnable onActivate) {
        UUID uuid = player.getUniqueId();
        String key = key(uuid, toolName);
        if (isActive(player, toolName) || hasActiveTool(player) || !canActivate.test(player)) return;

        long now = System.currentTimeMillis();
        Long lastTime = lastShiftTime.get(key);
        if (lastTime != null && now - lastTime > shiftTimeoutSeconds * 1000L) resetShiftCount(key);

        int count = shiftCount.getOrDefault(key, 0) + 1;
        shiftCount.put(key, count);
        lastShiftTime.put(key, now);
        int required = shiftsRequired;
        player.sendActionBar(ComponentUtils.progressBar(Math.min(count, required), required, '█', '░'));
        startShiftTimeout(player, key);

        if (count >= required) {
            resetShiftCount(key);
            activate(player, toolName, onActivate);
        }
    }

    private void startShiftTimeout(Player player, String key) {
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));
        SchedulerUtils.TaskHandle timeout = SchedulerUtils.runAtEntityLaterTask(player, () -> {
            shiftTimeoutTasks.remove(key);
            if (shiftCount.containsKey(key)) {
                resetShiftCount(key);
                if (player.isOnline()) player.sendActionBar(ComponentUtils.timeoutMessage());
            }
        }, SchedulerUtils.secondsToTicks(shiftTimeoutSeconds));
        shiftTimeoutTasks.put(key, timeout);
    }

    private void resetShiftCount(String key) {
        shiftCount.remove(key);
        lastShiftTime.remove(key);
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));
    }

    public void activate(Player player, String toolName, Runnable onActivate) {
        UUID uuid = player.getUniqueId();
        cleanupTool(uuid, toolName);
        activeTools.put(uuid, new ActiveToolState(toolName, player.getInventory().getItemInMainHand().clone()));
        cancelActionBarTask(actionBarTasks.remove(uuid));
        ActionBarTask actionBarTask = new ActionBarTask();
        actionBarTasks.put(uuid, actionBarTask);
        scheduleActionBarRefresh(player, uuid, toolName, actionBarTask, 0L, actionBarRefreshTicks);

        player.sendActionBar(ComponentUtils.activeStatus(toolName));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);
        if (onActivate != null) onActivate.run();
    }

    private void scheduleActionBarRefresh(Player player, UUID uuid, String toolName, ActionBarTask actionBarTask,
                                           long delayTicks, long periodTicks) {
        synchronized (actionBarTask) {
            if (actionBarTask.cancelled || actionBarTasks.get(uuid) != actionBarTask
                    || !player.isOnline() || !isActive(player, toolName)) {
                return;
            }
            actionBarTask.handle = SchedulerUtils.runAtEntityLaterTask(player, () -> {
                if (actionBarTasks.get(uuid) != actionBarTask || !player.isOnline() || !isActive(player, toolName)) {
                    return;
                }
                player.sendActionBar(ComponentUtils.activeStatus(toolName));
                scheduleActionBarRefresh(player, uuid, toolName, actionBarTask, periodTicks, periodTicks);
            }, delayTicks);
        }
    }

    private static void cancelActionBarTask(ActionBarTask actionBarTask) {
        if (actionBarTask == null) return;
        synchronized (actionBarTask) {
            actionBarTask.cancelled = true;
            SchedulerUtils.cancelTask(actionBarTask.handle);
        }
    }

    public void deactivate(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        ActiveToolState state = activeTools.remove(uuid);
        if (state == null) return;
        cancelActionBarTask(actionBarTasks.remove(uuid));
        cleanupTool(uuid, state.toolName);
        player.sendActionBar(ComponentUtils.disabledStatus(state.toolName, reason));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    /** Clears pending and active state for one tool without disturbing other tools. */
    public void cleanupTool(Player player, String toolName) {
        UUID uuid = player.getUniqueId();
        ActiveToolState state = activeTools.get(uuid);
        if (state != null && state.toolName.equals(toolName)) {
            activeTools.remove(uuid, state);
            cancelActionBarTask(actionBarTasks.remove(uuid));
        }
        cleanupTool(uuid, toolName);
    }

    private void cleanupTool(UUID uuid, String toolName) {
        resetShiftCount(key(uuid, toolName));
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
        return state == null ? null : state.toolName;
    }

    public void onItemSwitch(Player player) { if (hasActiveTool(player)) deactivate(player, "item switched"); }
    public void onDeath(Player player) { if (hasActiveTool(player)) deactivate(player, "died"); }
    public void onTeleport(Player player) { if (hasActiveTool(player)) deactivate(player, "teleported"); }
    public void onQuit(Player player) { cleanup(player); }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        String prefix = uuid + ":";
        shiftCount.keySet().removeIf(key -> key.startsWith(prefix));
        lastShiftTime.keySet().removeIf(key -> key.startsWith(prefix));
        shiftTimeoutTasks.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) return false;
            SchedulerUtils.cancelTask(entry.getValue());
            return true;
        });
        cancelActionBarTask(actionBarTasks.remove(uuid));
        activeTools.remove(uuid);
    }

    public void shutdown() {
        shiftTimeoutTasks.values().forEach(SchedulerUtils::cancelTask);
        actionBarTasks.values().forEach(ActiveToolAPI::cancelActionBarTask);
        shiftTimeoutTasks.clear();
        actionBarTasks.clear();
        shiftCount.clear();
        lastShiftTime.clear();
        activeTools.clear();
    }

    private static String key(UUID uuid, String toolName) {
        return uuid + ":" + toolName;
    }

    private static final class ActionBarTask {
        private SchedulerUtils.TaskHandle handle;
        private boolean cancelled;
    }

    private record ActiveToolState(String toolName, ItemStack originalItem) {
    }
}
