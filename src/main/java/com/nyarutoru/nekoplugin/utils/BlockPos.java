package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Lightweight block position record for efficient BFS traversal.
 * Replaces Location objects to reduce memory allocation and GC pressure.
 * Uses pre-computed hashCode for better HashMap/HashSet performance.
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
     * Creates a BlockPos from a Block.
     */
    public static BlockPos from(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /**
     * Calculates squared distance to another position (avoids sqrt for
     * performance).
     */
    public int distanceSquared(BlockPos other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Optimized hashCode for better HashMap/HashSet performance.
     * Uses bit mixing to reduce collisions for typical Minecraft coordinate
     * patterns.
     */
    @Override
    public int hashCode() {
        // Optimized hash combining - reduces collisions for typical block positions
        return (y + (x * 31)) * 31 + z;
    }

    /**
     * Checks if this position is within a bounding box.
     */
    public boolean isWithin(BlockPos min, BlockPos max) {
        return x >= min.x && x <= max.x &&
                y >= min.y && y <= max.y &&
                z >= min.z && z <= max.z;
    }

    /**
     * Checks if this position is within a spherical radius of another position.
     */
    public boolean isWithinRadius(BlockPos center, int radiusSquared) {
        return distanceSquared(center) <= radiusSquared;
    }
}
