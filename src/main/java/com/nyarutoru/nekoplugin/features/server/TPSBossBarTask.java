package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.utils.ServerPerformanceUtils;
import com.sun.management.OperatingSystemMXBean;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;

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

    public TPSBossBarTask() {
        this.bossBar = BossBar.bossBar(
                Component.text("Loading Server Stats..."),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS);
        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    }

    public void run() {
        // Gather global stats
        double cpu = osBean.getProcessCpuLoad() * 100;

        // Update each OP player with their region-specific TPS and MSPT
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                // Get TPS and MSPT for this player's current region (Folia-aware)
                double tps = ServerPerformanceUtils.getTPS(player);
                double mspt = ServerPerformanceUtils.getMSPT(player);

                // Format stats
                Component title = Component.text("TPS: ")
                        .append(Component.text(String.format("%.1f", tps), getTpsColor(tps)))
                        .append(Component.text(" | MSPT: "))
                        .append(Component.text(String.format("%.1f", mspt), getMsptColor(mspt)))
                        .append(Component.text("ms | CPU: "))
                        .append(Component.text(String.format("%.1f%%", cpu), getCpuColor(cpu)));

                bossBar.name(title);
                // Progress bar synced to MSPT: 0ms = 0%, 50ms = 100%
                bossBar.progress(Math.min(1.0f, Math.max(0.0f, (float) (mspt / 50.0))));
                bossBar.color(getBarColorByMspt(mspt));

                player.showBossBar(bossBar);
            } else {
                player.hideBossBar(bossBar);
            }
        }
    }

    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
    }

    private NamedTextColor getTpsColor(double tps) {
        if (tps >= 18.0)
            return NamedTextColor.GREEN;
        if (tps >= 15.0)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getMsptColor(double mspt) {
        if (mspt <= 40.0)
            return NamedTextColor.GREEN;
        if (mspt <= 50.0)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getCpuColor(double cpu) {
        if (cpu <= 60.0)
            return NamedTextColor.GREEN;
        if (cpu <= 80.0)
            return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private BossBar.Color getBarColorByMspt(double mspt) {
        if (mspt < 15.0)
            return BossBar.Color.GREEN;
        if (mspt <= 45.0)
            return BossBar.Color.YELLOW;
        if (mspt <= 50.0)
            return BossBar.Color.PINK; // Using PINK as closest to orange
        return BossBar.Color.RED;
    }
}
