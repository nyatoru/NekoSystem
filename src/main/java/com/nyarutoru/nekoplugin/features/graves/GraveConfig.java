package com.nyarutoru.nekoplugin.features.graves;

import java.util.concurrent.TimeUnit;

/**
 * Hardcoded configuration for Graves feature.
 * All values can be adjusted here without external config files.
 */
public class GraveConfig {

    // ========== GRAVE LIFETIME ==========
    /**
     * How long graves last before items drop on ground.
     * Default: 20 minutes
     */
    public static final long GRAVE_LIFETIME_MS = TimeUnit.MINUTES.toMillis(20);

    /**
     * How often to check for expired graves.
     * Default: 1 minute (1200 ticks)
     */
    public static final long GRAVE_CHECK_INTERVAL_TICKS = 1200;

    // ========== GRAVE LIMITS ==========
    /**
     * Maximum graves per player.
     * If exceeded, oldest grave drops items.
     */
    public static final int MAX_GRAVES_PER_PLAYER = 3;

    // ========== GRAVE PROTECTION ==========
    /**
     * Are graves indestructible?
     * True = players can't break grave heads
     */
    public static final boolean INDESTRUCTIBLE_GRAVES = true;

    /**
     * Can OPs access other players' graves?
     */
    public static final boolean OPS_BYPASS_PROTECTION = true;

    // ========== GRAVE COSMETICS ==========
    /**
     * Spawn particles when grave is created?
     */
    public static final boolean SPAWN_PARTICLES_ON_CREATE = true;

    /**
     * Play sound when grave is created?
     */
    public static final boolean PLAY_SOUND_ON_CREATE = true;

    /**
     * Spawn particles when items are retrieved?
     */
    public static final boolean SPAWN_PARTICLES_ON_RETRIEVE = true;

    /**
     * Play sound when items are retrieved?
     */
    public static final boolean PLAY_SOUND_ON_RETRIEVE = true;

    // ========== DEATH MESSAGE ==========
    /**
     * Show death coordinates to deceased player?
     */
    public static final boolean SHOW_DEATH_COORDINATES = true;

    /**
     * Broadcast death messages to all players?
     */
    public static final boolean BROADCAST_DEATH_MESSAGES = false;

    // ========== SAFE LOCATION ==========
    /**
     * Maximum distance to search for safe location.
     * If no safe spot found, grave spawns at death location anyway.
     */
    public static final int MAX_SAFE_LOCATION_SEARCH_RADIUS = 10;

    // ========== WORLDS ==========
    /**
     * Enable graves in all worlds?
     * If false, only worlds in GRAVE_ENABLED_WORLDS will work.
     */
    public static final boolean GRAVES_IN_ALL_WORLDS = true;

    /**
     * World blacklist (graves disabled in these worlds).
     * Only used if GRAVES_IN_ALL_WORLDS = true
     */
    public static final String[] GRAVE_DISABLED_WORLDS = {
        // Add world names here to disable graves
        // Example: "world_nether", "world_the_end"
    };

    // ========== DATABASE ==========
    /**
     * Persist graves across server restarts?
     */
    public static final boolean PERSIST_GRAVES = true;

    private GraveConfig() {
        // Private constructor to prevent instantiation
    }
}
