package com.nyarutoru.nekoplugin.features.shutup;

import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks placed "Shut Up" furnaces (33x33 area each) and keeps the targeted
 * mobs silent while they are inside. Mobs are un-silenced once they leave
 * every zone or the zones are removed.
 */
public final class ShutUpManager {

    private static final ShutUpManager INSTANCE = new ShutUpManager();

    /** 16 blocks around the block center = 33x33x33 area. */
    static final int RADIUS = 16;
    /** Scan period for silencing mobs that wander into a zone. */
    private static final long SCAN_PERIOD = 20L;
    /** Desilence pass runs every 2 ticks of the timer (every 40 ticks). */
    private static final int DESILENCE_EVERY = 2;

    private final Map<Location, ShutUpType> zones = new ConcurrentHashMap<>();
    private final Map<UUID, SilencedEntry> silenced = new ConcurrentHashMap<>();
    private int ticks;

    private ShutUpManager() {
    }

    public static ShutUpManager getInstance() {
        return INSTANCE;
    }

    /** Records a freshly placed zone. Called from the region thread. */
    public void track(Location blockLocation, ShutUpType type) {
        zones.put(normalize(blockLocation), type);
    }

    /** Forgets a removed zone; mobs inside are un-silenced by the next desilence pass. */
    public void untrack(Location blockLocation) {
        zones.remove(normalize(blockLocation));
    }

    /** Silences a matching mob on spawn if it spawned inside a zone. Called from the region thread. */
    public void silenceIfInZone(Entity entity) {
        if (entity.isSilent()) return;
        Location location = entity.getLocation();
        for (Map.Entry<Location, ShutUpType> zone : zones.entrySet()) {
            if (zone.getValue().matches(entity) && inBounds(location, zone.getKey())) {
                entity.setSilent(true);
                silenced.put(entity.getUniqueId(), new SilencedEntry(location));
                return;
            }
        }
    }

    public void tick() {
        if (!zones.isEmpty()) {
            for (Map.Entry<Location, ShutUpType> zone : zones.entrySet()) {
                Location center = zone.getKey();
                ShutUpType type = zone.getValue();
                SchedulerUtils.runAtLocation(center, () -> scanZone(center, type));
            }
        }
        if (++ticks % DESILENCE_EVERY == 0) {
            desilencePass();
        }
    }

    private void scanZone(Location center, ShutUpType type) {
        try {
            World world = center.getWorld();
            if (world == null) return;
            for (Entity entity : world.getNearbyEntities(center, RADIUS, RADIUS, RADIUS, type::matches)) {
                SilencedEntry entry = silenced.get(entity.getUniqueId());
                if (entry != null) {
                    entry.lastLoc = entity.getLocation();
                }
                if (!entity.isSilent()) {
                    entity.setSilent(true);
                    if (entry == null) {
                        silenced.put(entity.getUniqueId(), new SilencedEntry(entity.getLocation()));
                    }
                }
            }
        } catch (Throwable ignored) {
            // chunk unloaded or Folia region race
        }
    }

    private void desilencePass() {
        if (silenced.isEmpty()) return;
        for (Map.Entry<UUID, SilencedEntry> entry : List.copyOf(silenced.entrySet())) {
            desilence(entry.getKey(), entry.getValue());
        }
    }

    /** Un-silences a tracked mob unless it is still covered by some zone. */
    private void desilence(UUID id, SilencedEntry entry) {
        silenced.remove(id);
        try {
            if (SchedulerUtils.isFolia()) {
                Location last = entry.lastLoc;
                if (last == null || last.getWorld() == null) return;
                SchedulerUtils.runAtLocation(last, () -> {
                    try {
                        Entity entity = last.getWorld().getEntity(id);
                        if (entity != null && !isCovered(entity)) {
                            entity.setSilent(false);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } else {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && !isCovered(entity)) {
                    entity.setSilent(false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isCovered(Entity entity) {
        if (zones.isEmpty()) return false;
        Location location = entity.getLocation();
        for (Map.Entry<Location, ShutUpType> zone : zones.entrySet()) {
            if (zone.getValue().matches(entity) && inBounds(location, zone.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBounds(Location location, Location center) {
        return Math.abs(location.getX() - center.getX()) <= RADIUS
                && Math.abs(location.getY() - center.getY()) <= RADIUS
                && Math.abs(location.getZ() - center.getZ()) <= RADIUS;
    }

    /** Block location -> block center so the 33x33 cube is centered on the placed block. */
    private static Location normalize(Location blockLocation) {
        return new Location(blockLocation.getWorld(),
                blockLocation.getBlockX() + 0.5,
                blockLocation.getBlockY() + 0.5,
                blockLocation.getBlockZ() + 0.5);
    }

    /** Un-silences everything tracked by us. Called on feature disable. */
    public void shutdown() {
        zones.clear();
        for (Map.Entry<UUID, SilencedEntry> entry : List.copyOf(silenced.entrySet())) {
            desilence(entry.getKey(), entry.getValue());
        }
        silenced.clear();
        ticks = 0;
    }

    private static final class SilencedEntry {
        volatile Location lastLoc;

        SilencedEntry(Location lastLoc) {
            this.lastLoc = lastLoc;
        }
    }
}
