package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pillager;

import java.util.*;

/**
 * Manages Pillager clusters to prevent lag from ignored/stacked pillagers.
 * Removes pillagers when there are too many in a small area.
 */
public class PillagerManager {

    private final NekoPlugin plugin;
    // Note: No BukkitTask reference needed for Folia-compatible scheduling

    private static final int MAX_PILLAGERS_PER_CHUNK = 8;
    private static final int MAX_PILLAGERS_CLUSTER = 20;
    private static final double CLUSTER_RADIUS = 32.0;
    private static final long CHECK_INTERVAL_TICKS = 20 * 60 * 5; // 5 minutes

    public PillagerManager(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        SchedulerUtils.runGlobalTimer(this::cleanupPillagers,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        plugin.getLogger().info("Pillager manager started (checking every 5 minutes).");
    }

    public void stop() {
        // Task cleanup is handled by SchedulerUtils
    }

    private void cleanupPillagers() {
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            totalRemoved += cleanupWorld(world);
        }

        if (totalRemoved > 0) {
            plugin.getLogger().info("Cleaned up " + totalRemoved + " excess pillagers.");
        }
    }

    private int cleanupWorld(World world) {
        List<Pillager> allPillagers = new ArrayList<>();

        // Collect all pillagers
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == EntityType.PILLAGER && entity instanceof Pillager pillager) {
                allPillagers.add(pillager);
            }
        }

        if (allPillagers.isEmpty())
            return 0;

        Set<Pillager> toRemove = new HashSet<>();

        // Check for chunk overcrowding
        Map<Chunk, List<Pillager>> byChunk = new HashMap<>();
        for (Pillager pillager : allPillagers) {
            byChunk.computeIfAbsent(pillager.getChunk(), k -> new ArrayList<>()).add(pillager);
        }

        for (Map.Entry<Chunk, List<Pillager>> entry : byChunk.entrySet()) {
            List<Pillager> chunkPillagers = entry.getValue();
            if (chunkPillagers.size() > MAX_PILLAGERS_PER_CHUNK) {
                // Remove excess, keeping the newest ones
                chunkPillagers.sort(Comparator.comparingInt(Entity::getTicksLived));
                for (int i = 0; i < chunkPillagers.size() - MAX_PILLAGERS_PER_CHUNK; i++) {
                    toRemove.add(chunkPillagers.get(i));
                }
            }
        }

        // Check for clusters (many pillagers in small radius)
        Set<Pillager> processed = new HashSet<>();
        for (Pillager pillager : allPillagers) {
            if (processed.contains(pillager) || toRemove.contains(pillager))
                continue;

            List<Pillager> cluster = new ArrayList<>();
            cluster.add(pillager);
            processed.add(pillager);

            // Find nearby pillagers
            for (Pillager other : allPillagers) {
                if (processed.contains(other) || toRemove.contains(other))
                    continue;
                if (pillager.getLocation().distance(other.getLocation()) <= CLUSTER_RADIUS) {
                    cluster.add(other);
                    processed.add(other);
                }
            }

            // If cluster is too large, remove oldest
            if (cluster.size() > MAX_PILLAGERS_CLUSTER) {
                cluster.sort(Comparator.comparingInt(Entity::getTicksLived));
                for (int i = 0; i < cluster.size() - MAX_PILLAGERS_CLUSTER; i++) {
                    toRemove.add(cluster.get(i));
                }
            }
        }

        // Remove marked pillagers
        for (Pillager pillager : toRemove) {
            pillager.remove();
        }

        return toRemove.size();
    }

    /**
     * Force cleanup now (can be called from command)
     */
    public int forceCleanup() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += cleanupWorld(world);
        }
        return total;
    }
}
