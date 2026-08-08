package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public record GravePosition(UUID worldId, String worldName, int x, int y, int z) {

    public GravePosition {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }

    public static GravePosition from(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new GravePosition(world.getUID(), world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location resolve(Server server) {
        World world = server.getWorld(worldId);
        if (world == null) {
            world = server.getWorld(worldName);
        }
        return world == null ? null : new Location(world, x, y, z);
    }

    public String key() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
