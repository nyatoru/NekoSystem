package com.nyarutoru.nekoplugin.features.graves;

import java.util.concurrent.TimeUnit;

public final class GraveConfig {
    public static final long DEFAULT_GRAVE_LIFETIME_MS = TimeUnit.MINUTES.toMillis(20);
    public static final long DEFAULT_GRAVE_CHECK_INTERVAL_TICKS = 1200L;
    public static final int DEFAULT_MAX_GRAVES_PER_PLAYER = 3;
    public static final int DEFAULT_MAX_SAFE_LOCATION_SEARCH_RADIUS = 10;
    public static final long DEFAULT_DISPLAY_UPDATE_INTERVAL_TICKS = 20L;

    private GraveConfig() {}
}
