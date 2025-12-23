package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Lightweight block position record for efficient BFS traversal.
 * Replaces Location objects to reduce memory allocation and GC pressure.
 */
public record BlockPos(int x, int y, int z) {

    /**
     * Creates a new BlockPos offset from this position.
     */
    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /**
     * Gets the block at this position in the given world.
     */
    public Block getBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    /**
     * Converts this BlockPos to a Location.
     */
    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    /**
     * Creates a BlockPos from a Location.
     */
    public static BlockPos from(Location loc) {
        return new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Calculates squared distance to another position (avoids sqrt for
     * performance).
     */
    public double distanceSquared(BlockPos other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
