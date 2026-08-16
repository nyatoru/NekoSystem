package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import com.nyarutoru.nekoplugin.utils.ServerPerformanceUtils;
import com.sun.management.OperatingSystemMXBean;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Updates a BossBar with TPS, MSPT, and CPU usage for OP players.
 * This class is scheduled via SchedulerUtils for Folia compatibility.
 * <p>
 * On Folia: Both TPS and MSPT are region-specific per player.
 * On Paper/Spigot: Uses standard Bukkit.getTPS() and Bukkit.getAverageTickTime().
 */
public class TPSBossBarTask {

    private final BossBar bossBar;
    private final OperatingSystemMXBean osBean;
    private volatile boolean enabled = true;
    private volatile double tpsGoodThreshold = 18.0;
    private volatile double tpsWarningThreshold = 15.0;
    private volatile double msptGoodThreshold = 40.0;
    private volatile double msptWarningThreshold = 50.0;
    private volatile double cpuGoodThreshold = 60.0;
    private volatile double cpuWarningThreshold = 80.0;
    private final AtomicLong generation = new AtomicLong();
    private final Set<SchedulerUtils.TaskHandle> ownedTasks = ConcurrentHashMap.newKeySet();

    public TPSBossBarTask() {
        this.bossBar = BossBar.bossBar(
                Component.text("Loading Server Stats..."),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS);
        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    }

    public void run() {
        if (!enabled) return;
        // Gather global stats with fallback for unavailable CPU load
        double cpu = osBean.getProcessCpuLoad();
        // getProcessCpuLoad() can return -1.0 if not available, use 0.0 as fallback
        if (cpu < 0) {
            cpu = 0.0;
        }
        cpu *= 100;
        final double cpuPercent = cpu;

        long expectedGeneration = generation.get();
        // Update each OP player with their region-specific TPS and MSPT.
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayer(player, expectedGeneration, () -> {
                if (!player.isOp()) {
                    player.hideBossBar(bossBar);
                    return;
                }
                // Get TPS and MSPT for this player's current region (Folia-aware)
                double tps = ServerPerformanceUtils.getTPS(player);
                double mspt = ServerPerformanceUtils.getMSPT(player);

                // Format stats
                Component title = Component.text("TPS: ")
                        .append(Component.text(String.format("%.1f", tps), getTpsColor(tps)))
                        .append(Component.text(" | MSPT: "))
                        .append(Component.text(String.format("%.1f", mspt), getMsptColor(mspt)))
                        .append(Component.text("ms | CPU: "))
                        .append(Component.text(String.format("%.1f%%", cpuPercent), getCpuColor(cpuPercent)));

                bossBar.name(title);
                // Progress bar synced to MSPT: 0ms = 0%, 50ms = 100%
                bossBar.progress(Math.min(1.0f, Math.max(0.0f, (float) (mspt / 50.0))));
                bossBar.color(getBarColorByMspt(mspt));
                player.showBossBar(bossBar);
            });
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        long expectedGeneration = generation.incrementAndGet();
        if (!value) {
            cancelOwnedTasks();
            for (Player player : Bukkit.getOnlinePlayers()) {
                schedulePlayer(player, expectedGeneration, () -> player.hideBossBar(bossBar), true);
            }
        }
    }

    public void configure(double tpsGood, double tpsWarning, double msptGood, double msptWarning,
                          double cpuGood, double cpuWarning) {
        if (!Double.isFinite(tpsGood) || !Double.isFinite(tpsWarning) || tpsWarning > tpsGood
                || tpsWarning < 1.0 || tpsGood > 20.0) throw new IllegalArgumentException("Invalid TPS thresholds");
        if (!Double.isFinite(msptGood) || !Double.isFinite(msptWarning) || msptGood < 1.0 || msptWarning < msptGood || msptWarning > 1000.0) throw new IllegalArgumentException("Invalid MSPT thresholds");
        if (!Double.isFinite(cpuGood) || !Double.isFinite(cpuWarning) || cpuGood < 1.0 || cpuWarning < cpuGood || cpuWarning > 100.0) throw new IllegalArgumentException("Invalid CPU thresholds");
        tpsGoodThreshold = tpsGood;
        tpsWarningThreshold = tpsWarning;
        msptGoodThreshold = msptGood;
        msptWarningThreshold = msptWarning;
        cpuGoodThreshold = cpuGood;
        cpuWarningThreshold = cpuWarning;
    }

    public void cleanup() {
        enabled = false;
        long expectedGeneration = generation.incrementAndGet();
        cancelOwnedTasks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayer(player, expectedGeneration, () -> player.hideBossBar(bossBar));
        }
    }

    private void cancelOwnedTasks() {
        for (SchedulerUtils.TaskHandle task : ownedTasks) {
            SchedulerUtils.cancelTask(task);
        }
        ownedTasks.clear();
    }

    private void schedulePlayer(Player player, long expectedGeneration, Runnable action) {
        schedulePlayer(player, expectedGeneration, action, false);
    }

    private void schedulePlayer(Player player, long expectedGeneration, Runnable action, boolean cleanup) {
        SchedulerUtils.TaskHandle[] holder = new SchedulerUtils.TaskHandle[1];
        holder[0] = SchedulerUtils.runAtPlayerTask(player, () -> {
            ownedTasks.remove(holder[0]);
            if (generation.get() == expectedGeneration && (enabled || cleanup)) {
                action.run();
            }
        });
        ownedTasks.add(holder[0]);
    }

    private NamedTextColor getTpsColor(double tps) {
        if (tps >= tpsGoodThreshold)
            return NamedTextColor.GREEN;
        if (tps >= tpsWarningThreshold)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getMsptColor(double mspt) {
        if (mspt <= msptGoodThreshold)
            return NamedTextColor.GREEN;
        if (mspt <= msptWarningThreshold)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getCpuColor(double cpu) {
        if (cpu <= cpuGoodThreshold)
            return NamedTextColor.GREEN;
        if (cpu <= cpuWarningThreshold)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private BossBar.Color getBarColorByMspt(double mspt) {
        if (mspt <= msptGoodThreshold)
            return BossBar.Color.GREEN;
        if (mspt <= msptWarningThreshold)
            return BossBar.Color.YELLOW;
        return BossBar.Color.RED;
    }
}
