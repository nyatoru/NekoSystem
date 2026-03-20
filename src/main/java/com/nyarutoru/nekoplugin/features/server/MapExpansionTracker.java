package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
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
    
    // Constants
    private static final long TIME_WINDOW_MS = 10 * 1000; // 10 seconds
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes
    private static final int PLAYER_TRACKING_RADIUS = 128; // blocks
    private static final long DATA_RETENTION_MS = 60 * 1000; // 60 seconds
    
    // Track radius squared for performance
    private static final int PLAYER_TRACKING_RADIUS_SQUARED = PLAYER_TRACKING_RADIUS * PLAYER_TRACKING_RADIUS;

    public MapExpansionTracker(NekoPlugin plugin) {
        this.plugin = plugin;
        startCleanupTimer();
    }

    /**
     * Records a chunk generation event and attributes it to nearby players.
     */
    public void recordChunkGeneration(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        
        long currentWindow = System.currentTimeMillis() / TIME_WINDOW_MS * TIME_WINDOW_MS;
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
        
        long currentWindow = System.currentTimeMillis() / TIME_WINDOW_MS * TIME_WINDOW_MS;
        
        // Get chunks from current and previous window (to catch recent generation)
        List<ChunkLocation> recentChunks = new ArrayList<>();
        recentChunks.addAll(chunksByTimeWindow.getOrDefault(currentWindow, Collections.emptyList()));
        recentChunks.addAll(chunksByTimeWindow.getOrDefault(currentWindow - TIME_WINDOW_MS, Collections.emptyList()));
        
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
                if (distanceSquared <= PLAYER_TRACKING_RADIUS_SQUARED) {
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
        if (tps >= 18.0) {
            return false;
        }
        
        // Check player count threshold
        if (onlinePlayers <= 2) {
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
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    /**
     * Checks if the cooldown has elapsed.
     */
    public boolean isCooldownElapsed() {
        return System.currentTimeMillis() - lastNotificationTime >= COOLDOWN_MS;
    }

    /**
     * Gets the total number of chunks generated in the current time window.
     */
    public int getRecentChunkCount() {
        long currentWindow = System.currentTimeMillis() / TIME_WINDOW_MS * TIME_WINDOW_MS;
        
        int count = 0;
        count += chunksByTimeWindow.getOrDefault(currentWindow, Collections.emptyList()).size();
        count += chunksByTimeWindow.getOrDefault(currentWindow - TIME_WINDOW_MS, Collections.emptyList()).size();
        
        return count;
    }

    /**
     * Cleans up old chunk data to prevent memory leaks.
     */
    public void cleanup() {
        long currentWindow = System.currentTimeMillis() / TIME_WINDOW_MS * TIME_WINDOW_MS;
        long cutoffWindow = currentWindow - DATA_RETENTION_MS;
        
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
        // Run cleanup every 30 seconds
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            task -> cleanup(),
            600L, // 30 seconds initial delay
            600L  // 30 seconds interval
        );
    }

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
