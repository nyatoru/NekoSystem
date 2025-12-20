package com.nyarutoru.nekoplugin.api.tool;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * API for managing active tool states.
 * Handles shift-activation, persistent action bars, and proper cancellation.
 * 
 * Features:
 * - Rapid shift activation (10 shifts within 3 seconds)
 * - Prevents re-activation while already active
 * - Persistent action bar while active
 * - Cancels on: item swap, tool break, death, teleport, logout
 */
public class ActiveToolAPI {

    private static ActiveToolAPI instance;

    // Player activation state (keyed by UUID:toolName for tool-specific tracking)
    private final Map<String, Integer> shiftCount = new HashMap<>();
    private final Map<String, Long> lastShiftTime = new HashMap<>();
    private final Map<String, BukkitTask> shiftTimeoutTasks = new HashMap<>();

    // Active tool state
    private final Map<UUID, ActiveToolState> activeTools = new HashMap<>();

    private static final int SHIFTS_REQUIRED = 10;
    private static final long SHIFT_TIMEOUT_MS = 3000;
    private static final long ACTION_BAR_REFRESH_TICKS = 20L;

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
        StringBuilder bar = new StringBuilder("§6§l⚡ §e[");
        for (int i = 0; i < SHIFTS_REQUIRED; i++) {
            bar.append(i < progress ? "§a█" : "§8░");
        }
        bar.append("§e] §f").append(progress).append("§7/§f").append(SHIFTS_REQUIRED);
        player.sendActionBar(Component.text(bar.toString()));
    }

    private void startShiftTimeout(Player player, String key) {
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));

        BukkitTask task = SchedulerUtils.runSyncLater(() -> {
            if (shiftCount.containsKey(key)) {
                resetShiftCount(key);
                if (player.isOnline()) {
                    player.sendActionBar(Component.text("§c§l✖ §7Activation cancelled §8(timeout)"));
                }
            }
        }, SchedulerUtils.secondsToTicks(3));

        shiftTimeoutTasks.put(key, task);
    }

    private void resetShiftCount(String key) {
        shiftCount.remove(key);
        lastShiftTime.remove(key);
        SchedulerUtils.cancelTask(shiftTimeoutTasks.remove(key));
    }

    /**
     * Activates a tool for the player.
     */
    public void activate(Player player, String toolName, Runnable onActivate) {
        UUID uuid = player.getUniqueId();

        ActiveToolState state = new ActiveToolState(toolName, player.getInventory().getItemInMainHand().clone());
        activeTools.put(uuid, state);

        state.actionBarTask = SchedulerUtils.runSyncTimer(() -> {
            if (player.isOnline() && isActive(player, toolName)) {
                player.sendActionBar(Component.text("§a§l✔ §f" + toolName + " §a§lACTIVE"));
            }
        }, 0, ACTION_BAR_REFRESH_TICKS);

        player.sendActionBar(Component.text("§a§l✔ §f" + toolName + " §a§lACTIVE"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 2.0f);

        NekoPlugin.getInstance().getLogger().info(player.getName() + " activated " + toolName);

        if (onActivate != null) {
            onActivate.run();
        }
    }

    /**
     * Deactivates the tool for a player.
     */
    public void deactivate(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        ActiveToolState state = activeTools.remove(uuid);

        if (state != null) {
            SchedulerUtils.cancelTask(state.actionBarTask);
            player.sendActionBar(Component.text("§c§l✖ §f" + state.toolName + " §7disabled §8(" + reason + ")"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

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

        ActiveToolState state = activeTools.remove(uuid);
        if (state != null) {
            SchedulerUtils.cancelTask(state.actionBarTask);
        }
    }

    public void shutdown() {
        for (BukkitTask task : shiftTimeoutTasks.values()) {
            task.cancel();
        }
        for (ActiveToolState state : activeTools.values()) {
            SchedulerUtils.cancelTask(state.actionBarTask);
        }
        shiftTimeoutTasks.clear();
        shiftCount.clear();
        lastShiftTime.clear();
        activeTools.clear();
    }

    private static class ActiveToolState {
        final String toolName;
        final ItemStack originalItem;
        BukkitTask actionBarTask;

        ActiveToolState(String toolName, ItemStack originalItem) {
            this.toolName = toolName;
            this.originalItem = originalItem;
        }
    }
}
