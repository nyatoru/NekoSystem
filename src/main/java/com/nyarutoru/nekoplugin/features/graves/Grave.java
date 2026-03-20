package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a player's grave containing their death items.
 * Thread-safe data structure for grave information.
 */
public class Grave {

    private final UUID graveId;
    private final UUID playerUuid;
    private final String playerName;
    private final Location deathLocation;
    private final Location graveLocation;
    private final List<ItemStack> items;
    private final long deathTime;
    private long expiryTime;
    private boolean accessed;

    /**
     * Creates a new grave for a deceased player.
     *
     * @param player The deceased player
     * @param deathLocation Where the player died
     * @param graveLocation Where the grave was placed (may differ if death location was unsafe)
     * @param items Items to store in the grave
     */
    public Grave(OfflinePlayer player, Location deathLocation, Location graveLocation, List<ItemStack> items) {
        this.graveId = UUID.randomUUID();
        this.playerUuid = player.getUniqueId();
        this.playerName = player.getName() != null ? player.getName() : "Unknown";
        this.deathLocation = deathLocation.clone();
        this.graveLocation = graveLocation.clone();
        this.items = new ArrayList<>(items);
        this.deathTime = System.currentTimeMillis();
        this.expiryTime = this.deathTime + GraveConfig.GRAVE_LIFETIME_MS;
        this.accessed = false;
    }

    /**
     * Gets the unique grave identifier.
     *
     * @return Grave UUID
     */
    public UUID getGraveId() {
        return graveId;
    }

    /**
     * Gets the deceased player's UUID.
     *
     * @return Player UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Gets the deceased player's name.
     *
     * @return Player name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets the location where the player died.
     *
     * @return Death location
     */
    public Location getDeathLocation() {
        return deathLocation.clone();
    }

    /**
     * Gets the location where the grave was placed.
     *
     * @return Grave location
     */
    public Location getGraveLocation() {
        return graveLocation.clone();
    }

    /**
     * Gets a copy of all items in the grave.
     *
     * @return List of item stacks
     */
    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Gets the time of death.
     *
     * @return Death time in milliseconds since epoch
     */
    public long getDeathTime() {
        return deathTime;
    }

    /**
     * Gets the time when this grave will expire.
     *
     * @return Expiry time in milliseconds since epoch
     */
    public long getExpiryTime() {
        return expiryTime;
    }

    /**
     * Checks if this grave has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTime;
    }

    /**
     * Checks if this grave has been accessed.
     *
     * @return true if accessed, false otherwise
     */
    public boolean isAccessed() {
        return accessed;
    }

    /**
     * Marks this grave as accessed.
     */
    public void markAccessed() {
        this.accessed = true;
    }

    /**
     * Removes an item from the grave.
     *
     * @param index The index of the item to remove
     * @return The removed item stack, or null if index was invalid
     */
    public ItemStack removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.remove(index);
        }
        return null;
    }

    /**
     * Gets the number of items in this grave.
     *
     * @return Item count
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Checks if this grave is empty.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Gets the remaining time until this grave expires.
     *
     * @return Remaining time in milliseconds
     */
    public long getRemainingTime() {
        return Math.max(0, expiryTime - System.currentTimeMillis());
    }

    /**
     * Gets a formatted string of the remaining time.
     *
     * @return Formatted time string (e.g., "15m 30s")
     */
    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        long minutes = remaining / 60000;
        long seconds = (remaining % 60000) / 1000;
        
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Creates a copy of this grave.
     *
     * @return A deep copy of this grave
     */
    public Grave copy() {
        Grave copy = new Grave(
            org.bukkit.Bukkit.getOfflinePlayer(playerUuid),
            deathLocation,
            graveLocation,
            items
        );
        copy.accessed = this.accessed;
        return copy;
    }

    @Override
    public String toString() {
        return String.format("Grave{player=%s, location=%s, items=%d, expires=%s}",
            playerName,
            graveLocation.getBlock().getType().name(),
            items.size(),
            getFormattedRemainingTime());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Grave grave = (Grave) obj;
        return graveId.equals(grave.graveId);
    }

    @Override
    public int hashCode() {
        return graveId.hashCode();
    }
}
