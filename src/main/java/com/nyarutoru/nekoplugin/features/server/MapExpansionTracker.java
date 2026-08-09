package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks map expansion and attributes chunk generation to nearby players.
 * Used for detecting when player exploration is causing server lag.
 * 
 * Thread-safe implementation using ConcurrentHashMap for Folia/Paper compatibility.
 */
public class MapExpansionTracker {

    private final NekoPlugin plugin;
    
    // Track chunks generated per 10-second time window (window timestamp -> chunk locations)
    private final Map<Long, List<ChunkLocation>> chunksByTimeWindow = new ConcurrentHashMap<>();
    
    // Track player locations for attribution (updated periodically)
    private final Map<UUID, PlayerLocation> playerLocations = new ConcurrentHashMap<>();
    
    // Cooldown tracking
    private volatile long lastNotificationTime = 0;
    
    // Defaults are intentionally bounded to keep tracking work predictable.
    private static final long DEFAULT_TIME_WINDOW_MS = 10 * 1000;
    private static final long DEFAULT_COOLDOWN_MS = 5 * 60 * 1000;
    private static final int DEFAULT_PLAYER_TRACKING_RADIUS = 128;
    private static final long DEFAULT_DATA_RETENTION_MS = 60 * 1000;
    private static final long MAX_TIME_WINDOW_MS = 60 * 1000;
    private static final long MAX_COOLDOWN_MS = 60 * 60 * 1000;
    private static final int MAX_PLAYER_TRACKING_RADIUS = 512;
    private static final long MAX_DATA_RETENTION_MS = 60 * 60 * 1000;

    private volatile long timeWindowMs = DEFAULT_TIME_WINDOW_MS;
    private volatile long cooldownMs = DEFAULT_COOLDOWN_MS;
    private volatile int playerTrackingRadius = DEFAULT_PLAYER_TRACKING_RADIUS;
    private volatile long dataRetentionMs = DEFAULT_DATA_RETENTION_MS;
    private volatile double tpsWarningThreshold = 18.0;
    private volatile int minPlayersForWarning = 3;
    private volatile SchedulerUtils.TaskHandle cleanupTask;
    private volatile boolean running = true;
    public MapExpansionTracker(NekoPlugin plugin) {
        this.plugin = plugin;
        startCleanupTimer();
    }

    /**
     * Records a chunk generation event and attributes it to nearby players.
     */
    public void recordChunkGeneration(Chunk chunk) {
        if (!running || chunk == null) {
            return;
        }
        
        long currentWindow = System.currentTimeMillis() / timeWindowMs * timeWindowMs;
        Location chunkCenter = getChunkCenterLocation(chunk);
        
        if (chunkCenter == null) {
            return;
        }
        
        ChunkLocation chunkLoc = new ChunkLocation(
            chunkCenter.getWorld().getName(),
            chunkCenter.getBlockX(),
            chunkCenter.getBlockY(),
            chunkCenter.getBlockZ()
        );
        
        chunksByTimeWindow.computeIfAbsent(currentWindow, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(chunkLoc);
        
        // Update player locations for attribution
        updatePlayerLocations(chunk.getWorld());
    }

    /**
     * Gets all players who contributed to chunk generation in the current time window.
     * Returns a map of player UUID to chunk count.
     */
    public Map<UUID, Integer> getContributingPlayers() {
        Map<UUID, Integer> contributingPlayers = new ConcurrentHashMap<>();
        
        long currentWindow = System.currentTimeMillis() / timeWindowMs * timeWindowMs;
        
        // Only include the configured time window; including the previous bucket would
        // make a nominal 10-second window represent nearly 20 seconds of data.
        List<ChunkLocation> recentChunks = new ArrayList<>(
                chunksByTimeWindow.getOrDefault(currentWindow, Collections.emptyList()));
        
        // For each chunk, find all players within tracking radius
        for (ChunkLocation chunkLoc : recentChunks) {
            World world = plugin.getServer().getWorld(chunkLoc.worldName);
            if (world == null) {
                continue;
            }
            
            Location chunkCenter = new Location(world, chunkLoc.x, chunkLoc.y, chunkLoc.z);
            
            // Check all players in the world
            for (Player player : world.getPlayers()) {
                UUID playerId = player.getUniqueId();
                PlayerLocation playerLoc = playerLocations.get(playerId);
                
                if (playerLoc == null || !playerLoc.worldName.equals(chunkLoc.worldName)) {
                    continue;
                }
                
                // Calculate distance squared (avoid sqrt for performance)
                int dx = playerLoc.x - chunkLoc.x;
                int dy = playerLoc.y - chunkLoc.y;
                int dz = playerLoc.z - chunkLoc.z;
                int distanceSquared = dx * dx + dy * dy + dz * dz;
                
                // Attribute chunk to player if within radius
                if (distanceSquared <= playerTrackingRadius * playerTrackingRadius) {
                    contributingPlayers.merge(playerId, 1, Integer::sum);
                }
            }
        }
        
        return contributingPlayers;
    }

    /**
     * Checks if a notification should be sent based on current conditions.
     */
    public boolean shouldNotify(double tps, int onlinePlayers) {
        // Check cooldown
        if (!isCooldownElapsed()) {
            return false;
        }
        
        // Check TPS threshold
        if (tps >= tpsWarningThreshold) {
            return false;
        }
        
        // Check player count threshold
        if (onlinePlayers < minPlayersForWarning) {
            return false;
        }
        
        // Check if there are any contributing players
        Map<UUID, Integer> contributors = getContributingPlayers();
        if (contributors.isEmpty()) {
            return false;
        }
        
        return true;
    }

    /**
     * Resets the cooldown timer.
     */
    public void resetCooldown() {
        lastNotificationTime = System.currentTimeMillis();
    }

    /**
     * Gets the time until the next notification can be sent.
     */
    public long getTimeUntilNextNotification() {
        long elapsed = System.currentTimeMillis() - lastNotificationTime;
        return Math.max(0, cooldownMs - elapsed);
    }

    /**
     * Checks if the cooldown has elapsed.
     */
    public boolean isCooldownElapsed() {
        return System.currentTimeMillis() - lastNotificationTime >= cooldownMs;
    }

    /**
     * Gets the total number of chunks generated in the current time window.
     */
    public int getRecentChunkCount() {
        long currentWindow = System.currentTimeMillis() / timeWindowMs * timeWindowMs;
        
        return chunksByTimeWindow.getOrDefault(currentWindow, Collections.emptyList()).size();
    }

    /**
     * Cleans up old chunk data to prevent memory leaks.
     */
    public void cleanup() {
        long currentWindow = System.currentTimeMillis() / timeWindowMs * timeWindowMs;
        long cutoffWindow = currentWindow - dataRetentionMs;
        
        // Remove old time windows
        chunksByTimeWindow.keySet().removeIf(window -> window < cutoffWindow);
        
        // Clean up player locations for offline players
        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                onlinePlayerIds.add(player.getUniqueId());
            }
        }
        playerLocations.keySet().removeIf(uuid -> !onlinePlayerIds.contains(uuid));
    }

    /**
     * Updates player locations for a specific world.
     */
    private void updatePlayerLocations(World world) {
        if (world == null) {
            return;
        }
        
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            playerLocations.put(player.getUniqueId(), new PlayerLocation(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
            ));
        }
    }

    /**
     * Gets the center location of a chunk.
     */
    private Location getChunkCenterLocation(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return null;
        }
        
        int centerX = chunk.getX() * 16 + 8;
        int centerZ = chunk.getZ() * 16 + 8;
        int centerY = 64; // Approximate center Y
        
        return new Location(chunk.getWorld(), centerX, centerY, centerZ);
    }

    /**
     * Starts the cleanup timer to remove old data.
     */
    private void startCleanupTimer() {
        cleanupTask = SchedulerUtils.runGlobalTimerTask(() -> {
            if (running) cleanup();
        }, 600L, 600L);
    }

    public void stop() {
        running = false;
        SchedulerUtils.cancelTask(cleanupTask);
        cleanupTask = null;
        chunksByTimeWindow.clear();
        playerLocations.clear();
    }

    public void setTimeWindowSeconds(long seconds) {
        if (seconds < 1 || seconds > TimeUnit.MILLISECONDS.toSeconds(MAX_TIME_WINDOW_MS)) {
            throw new IllegalArgumentException("Map lag window must be between 1 and 60 seconds");
        }
        timeWindowMs = TimeUnit.SECONDS.toMillis(seconds);
    }

    public void setCooldownMinutes(long minutes) {
        if (minutes < 1 || minutes > TimeUnit.MILLISECONDS.toMinutes(MAX_COOLDOWN_MS)) {
            throw new IllegalArgumentException("Map lag cooldown must be between 1 and 60 minutes");
        }
        cooldownMs = TimeUnit.MINUTES.toMillis(minutes);
    }

    public void setTrackingRadius(int radius) {
        if (radius < 1 || radius > MAX_PLAYER_TRACKING_RADIUS) {
            throw new IllegalArgumentException("Map lag tracking radius must be between 1 and 512 blocks");
        }
        playerTrackingRadius = radius;
    }

    public void setDataRetentionMinutes(long minutes) {
        if (minutes < 1 || minutes > TimeUnit.MILLISECONDS.toMinutes(MAX_DATA_RETENTION_MS)) {
            throw new IllegalArgumentException("Map lag data retention must be between 1 and 60 minutes");
        }
        dataRetentionMs = TimeUnit.MINUTES.toMillis(minutes);
    }

    public void setTpsWarningThreshold(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 1.0 || threshold > 20.0) {
            throw new IllegalArgumentException("Map lag TPS threshold must be between 1 and 20");
        }
        tpsWarningThreshold = threshold;
    }

    public void setMinPlayersForWarning(int players) {
        if (players < 1 || players > 100) {
            throw new IllegalArgumentException("Map lag player threshold must be between 1 and 100");
        }
        minPlayersForWarning = players;
    }

    public double getTpsWarningThreshold() { return tpsWarningThreshold; }
    public int getMinPlayersForWarning() { return minPlayersForWarning; }
    public long getCooldownMinutes() { return TimeUnit.MILLISECONDS.toMinutes(cooldownMs); }
    public long getTimeWindowSeconds() { return TimeUnit.MILLISECONDS.toSeconds(timeWindowMs); }
    public int getTrackingRadius() { return playerTrackingRadius; }
    public long getDataRetentionMinutes() { return TimeUnit.MILLISECONDS.toMinutes(dataRetentionMs); }

    /**
     * Simple data class for chunk location.
     */
    private static class ChunkLocation {
        final String worldName;
        final int x, y, z;
        
        ChunkLocation(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Simple data class for player location.
     */
    private static class PlayerLocation {
        final String worldName;
        final int x, y, z;
        
        PlayerLocation(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
