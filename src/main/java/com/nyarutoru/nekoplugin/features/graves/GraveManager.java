package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages grave storage, retrieval, and database operations.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class GraveManager {

    private static GraveManager instance;
    
    private final NekoPlugin plugin;
    private final Map<String, Grave> gravesByLocation;
    private final Map<UUID, List<UUID>> gravesByPlayer;
    private NamespacedKey graveKey;
    private boolean databaseInitialized = false;

    private GraveManager(NekoPlugin plugin) {
        this.plugin = plugin;
        this.gravesByLocation = new ConcurrentHashMap<>();
        this.gravesByPlayer = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the GraveManager instance.
     *
     * @param plugin The plugin instance
     * @return The GraveManager instance
     */
    public static GraveManager init(NekoPlugin plugin) {
        instance = new GraveManager(plugin);
        instance.graveKey = new NamespacedKey(plugin, "grave_id");
        return instance;
    }

    /**
     * Gets the GraveManager instance.
     *
     * @return The GraveManager instance
     */

    /**
     * Creates a unique key for a location.
     * Replaces deprecated toBlockKey() method.
     *
     * @param location The location
     * @return A unique string key for the location
     */
    private String getLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + ":" +
               location.getBlockX() + ":" +
               location.getBlockY() + ":" +
               location.getBlockZ();
    }

    public static GraveManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("GraveManager not initialized");
        }
        return instance;
    }

    /**
     * Starts the grave manager and loads existing graves from database.
     */
    public void start() {
        if (GraveConfig.PERSIST_GRAVES) {
            loadGravesFromDatabase();
        }
        
        // Start periodic grave expiry check
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            (task) -> this.checkExpiredGraves(),
            GraveConfig.GRAVE_CHECK_INTERVAL_TICKS,
            GraveConfig.GRAVE_CHECK_INTERVAL_TICKS
        );
        
        plugin.getLogger().info("GraveManager started with " + gravesByLocation.size() + " active graves");
    }

    /**
     * Stops the grave manager and saves all graves to database.
     */
    public void stop() {
        if (GraveConfig.PERSIST_GRAVES) {
            saveAllGravesToDatabase();
        }
        gravesByLocation.clear();
        gravesByPlayer.clear();
        plugin.getLogger().info("GraveManager stopped");
    }

    /**
     * Creates a grave for a deceased player.
     *
     * @param player The deceased player
     * @param deathLocation Where the player died
     * @param items Items to store in the grave
     * @return The created grave, or null if creation failed
     */
    public Grave createGrave(OfflinePlayer player, Location deathLocation, List<ItemStack> items) {
        if (deathLocation == null || deathLocation.getWorld() == null) {
            return null;
        }

        // Find safe location for grave
        Location safeLocation = findSafeLocation(deathLocation);
        if (safeLocation == null) {
            plugin.getLogger().warning("Could not find safe location for grave at " + deathLocation);
            return null;
        }

        // Create the grave
        Grave grave = new Grave(player, deathLocation, safeLocation, items);
        
        // Remove oldest grave if player exceeds limit
        enforceGraveLimit(player.getUniqueId());
        
        // Store the grave
        gravesByLocation.put(getLocationKey(safeLocation), grave);
        gravesByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(grave.getGraveId());
        
        // Place grave block (player head)
        placeGraveBlock(safeLocation, player);
        
        // Save to database if persistence enabled
        if (GraveConfig.PERSIST_GRAVES) {
            saveGraveToDatabase(grave);
        }
        
        return grave;
    }

    /**
     * Gets a grave by its location.
     *
     * @param location The grave location
     * @return The grave, or null if not found
     */
    public Grave getGrave(Location location) {
        if (location == null) {
            return null;
        }
        return gravesByLocation.get(getLocationKey(location));
    }

    /**
     * Gets all graves for a player.
     *
     * @param playerUuid The player's UUID
     * @return List of graves (may be empty)
     */
    public List<Grave> getPlayerGraves(UUID playerUuid) {
        List<UUID> graveIds = gravesByPlayer.get(playerUuid);
        if (graveIds == null) {
            return Collections.emptyList();
        }
        
        List<Grave> playerGraves = new ArrayList<>();
        for (UUID graveId : graveIds) {
            for (Grave grave : gravesByLocation.values()) {
                if (grave.getGraveId().equals(graveId)) {
                    playerGraves.add(grave);
                    break;
                }
            }
        }
        return playerGraves;
    }

    /**
     * Gets the total number of active graves.
     *
     * @return Grave count
     */
    public int getGraveCount() {
        return gravesByLocation.size();
    }

    /**
     * Gets the total number of graves for a player.
     *
     * @param playerUuid The player's UUID
     * @return Grave count
     */
    public int getPlayerGraveCount(UUID playerUuid) {
        List<UUID> graves = gravesByPlayer.get(playerUuid);
        return graves != null ? graves.size() : 0;
    }

    /**
     * Removes a grave and drops its items.
     *
     * @param grave The grave to remove
     * @param dropItems Whether to drop items on ground
     */
    public void removeGrave(Grave grave, boolean dropItems) {
        if (grave == null) {
            return;
        }
        
        Location graveLocation = grave.getGraveLocation();
        World world = graveLocation.getWorld();
        
        if (world != null && dropItems && !grave.isEmpty()) {
            // Drop items at grave location
            for (ItemStack item : grave.getItems()) {
                if (item != null && !item.getType().isAir()) {
                    world.dropItemNaturally(graveLocation.clone().add(0.5, 0.5, 0.5), item);
                }
            }
        }
        
        // Remove grave block
        graveLocation.getBlock().setType(org.bukkit.Material.AIR);
        
        // Remove from storage
        gravesByLocation.remove(getLocationKey(graveLocation));
        
        List<UUID> playerGraves = gravesByPlayer.get(grave.getPlayerUuid());
        if (playerGraves != null) {
            playerGraves.remove(grave.getGraveId());
            if (playerGraves.isEmpty()) {
                gravesByPlayer.remove(grave.getPlayerUuid());
            }
        }
        
        // Remove from database
        if (GraveConfig.PERSIST_GRAVES) {
            deleteGraveFromDatabase(grave.getGraveId());
        }
    }

    /**
     * Checks if a player can access a grave.
     *
     * @param player The player trying to access
     * @param grave The grave to access
     * @return true if access is allowed, false otherwise
     */
    public boolean canAccessGrave(Player player, Grave grave) {
        if (player == null || grave == null) {
            return false;
        }
        
        // Player can access their own grave
        if (player.getUniqueId().equals(grave.getPlayerUuid())) {
            return true;
        }
        
        // OPs can access if configured
        if (GraveConfig.OPS_BYPASS_PROTECTION && player.isOp()) {
            return true;
        }
        
        return false;
    }

    /**
     * Gets all active graves.
     *
     * @return Collection of all graves
     */
    public Collection<Grave> getAllGraves() {
        return Collections.unmodifiableCollection(gravesByLocation.values());
    }

    /**
     * Finds a safe location for placing a grave.
     *
     * @param deathLocation The death location
     * @return Safe location, or null if none found
     */
    private Location findSafeLocation(Location deathLocation) {
        World world = deathLocation.getWorld();
        if (world == null) {
            return null;
        }

        int x = deathLocation.getBlockX();
        int y = deathLocation.getBlockY();
        int z = deathLocation.getBlockZ();

        // Check if death location is safe
        if (isSafeLocation(world, x, y, z)) {
            return new Location(world, x + 0.5, y, z + 0.5, deathLocation.getYaw(), deathLocation.getPitch());
        }

        // Search for safe location in expanding radius
        int radius = GraveConfig.MAX_SAFE_LOCATION_SEARCH_RADIUS;
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -r; dy <= r; dy++) {
                        if (isSafeLocation(world, x + dx, y + dy, z + dz)) {
                            return new Location(world, x + dx + 0.5, y + dy, z + dz, deathLocation.getYaw(), deathLocation.getPitch());
                        }
                    }
                }
            }
        }

        // No safe location found, return original anyway
        return new Location(world, x + 0.5, y, z + 0.5, deathLocation.getYaw(), deathLocation.getPitch());
    }

    /**
     * Checks if a location is safe for grave placement.
     */
    private boolean isSafeLocation(World world, int x, int y, int z) {
        // Must be in loaded chunk
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }

        Block block = world.getBlockAt(x, y, z);
        Block above = world.getBlockAt(x, y + 1, z);

        // Block must be air or replaceable
        if (!block.getType().isAir() && !block.isPassable()) {
            return false;
        }

        // Space above must be clear
        if (!above.getType().isAir() && !above.isPassable()) {
            return false;
        }

        // Not in lava or fire
        if (block.getType() == org.bukkit.Material.LAVA || block.getType() == org.bukkit.Material.FIRE) {
            return false;
        }

        return true;
    }

    /**
     * Places a player head at the grave location.
     */
    private void placeGraveBlock(Location location, OfflinePlayer player) {
        Block block = location.getBlock();
        
        // Set to player head
        block.setType(org.bukkit.Material.PLAYER_HEAD);
        
        // Set head texture to player's skin
        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            directional.setFacing(org.bukkit.block.BlockFace.NORTH);
            block.setBlockData(directional);
        }
        
        // Store grave ID in PDC for retrieval
        if (location.getWorld() != null) {
            // Note: PDC on blocks requires Paper 1.20.5+
            // This is a placeholder for future implementation
        }
    }

    /**
     * Enforces the maximum grave limit per player.
     */
    private void enforceGraveLimit(UUID playerUuid) {
        List<UUID> playerGraveIds = gravesByPlayer.get(playerUuid);
        if (playerGraveIds == null || playerGraveIds.size() < GraveConfig.MAX_GRAVES_PER_PLAYER) {
            return;
        }

        // Remove oldest grave (first in list)
        UUID oldestGraveId = playerGraveIds.get(0);
        for (Grave grave : gravesByLocation.values()) {
            if (grave.getGraveId().equals(oldestGraveId)) {
                removeGrave(grave, true); // Drop items
                break;
            }
        }
    }

    /**
     * Checks and removes expired graves.
     */
    private void checkExpiredGraves() {
        List<Grave> expiredGraves = new ArrayList<>();
        
        for (Grave grave : gravesByLocation.values()) {
            if (grave.isExpired()) {
                expiredGraves.add(grave);
            }
        }
        
        for (Grave grave : expiredGraves) {
            plugin.getLogger().info("Grave for " + grave.getPlayerName() + " has expired, dropping items");
            removeGrave(grave, true);
        }
        
        if (!expiredGraves.isEmpty()) {
            plugin.getLogger().fine("Removed " + expiredGraves.size() + " expired graves");
        }
    }

    // ========== DATABASE METHODS ==========

    private void loadGravesFromDatabase() {
        // Database implementation would go here
        // For now, this is a placeholder
        plugin.getLogger().fine("Loading graves from database (not yet implemented)");
    }

    private void saveGraveToDatabase(Grave grave) {
        // Database implementation would go here
        plugin.getLogger().fine("Saving grave " + grave.getGraveId() + " to database");
    }

    private void saveAllGravesToDatabase() {
        for (Grave grave : gravesByLocation.values()) {
            saveGraveToDatabase(grave);
        }
    }

    private void deleteGraveFromDatabase(UUID graveId) {
        // Database implementation would go here
        plugin.getLogger().fine("Deleting grave " + graveId + " from database");
    }
}
