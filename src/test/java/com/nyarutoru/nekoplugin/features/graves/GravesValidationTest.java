package com.nyarutoru.nekoplugin.features.graves;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Graves feature validation.
 * Tests avoid direct Material/Location usage to prevent Bukkit initialization requirements.
 */
class GravesValidationTest {

    @Test
    void testFeatureConstants() {
        GravesFeature feature = new GravesFeature();
        assertEquals("graves", feature.getId());
        assertEquals("Graves", feature.getName());
    }

    @Test
    void testFeatureClassExists() {
        assertNotNull(GravesFeature.class);
    }

    @Test
    void testGraveClassExists() {
        assertNotNull(Grave.class);
    }

    @Test
    void testGraveManagerClassExists() {
        assertNotNull(GraveManager.class);
    }

    @Test
    void testGraveListenerClassExists() {
        assertNotNull(GraveListener.class);
    }

    @Test
    void testGraveCommandsClassExists() {
        assertNotNull(GraveCommands.class);
    }

    @Test
    void testGraveConfigExists() {
        assertNotNull(GraveConfig.class);
    }

    @Test
    void testFeatureImplementation() {
        GravesFeature feature = new GravesFeature();
        
        assertEquals("graves", feature.getId());
        assertEquals("Graves", feature.getName());
        assertFalse(feature.isEnabled()); // Should be false before enable
    }

    @Test
    void testGraveLifetime() {
        // Verify grave lifetime is 20 minutes
        assertEquals(20 * 60 * 1000, GraveConfig.GRAVE_LIFETIME_MS);
        assertEquals(TimeUnit.MINUTES.toMillis(20), GraveConfig.GRAVE_LIFETIME_MS);
    }

    @Test
    void testGraveCheckInterval() {
        // Verify check interval is 1 minute (1200 ticks)
        assertEquals(1200, GraveConfig.GRAVE_CHECK_INTERVAL_TICKS);
    }

    @Test
    void testMaxGravesPerPlayer() {
        // Verify max graves per player is 3
        assertEquals(3, GraveConfig.MAX_GRAVES_PER_PLAYER);
    }

    @Test
    void testIndestructibleGraves() {
        // Verify graves are indestructible by default
        assertTrue(GraveConfig.INDESTRUCTIBLE_GRAVES);
    }

    @Test
    void testOPBypassProtection() {
        // Verify OPs can bypass grave protection
        assertTrue(GraveConfig.OPS_BYPASS_PROTECTION);
    }

    @Test
    void testGraveCosmetics() {
        // Verify cosmetics are enabled
        assertTrue(GraveConfig.SPAWN_PARTICLES_ON_CREATE);
        assertTrue(GraveConfig.PLAY_SOUND_ON_CREATE);
        assertTrue(GraveConfig.SPAWN_PARTICLES_ON_RETRIEVE);
        assertTrue(GraveConfig.PLAY_SOUND_ON_RETRIEVE);
    }

    @Test
    void testDeathMessageConfig() {
        // Verify death message settings
        assertTrue(GraveConfig.SHOW_DEATH_COORDINATES);
        assertFalse(GraveConfig.BROADCAST_DEATH_MESSAGES);
    }

    @Test
    void testSafeLocationSearchRadius() {
        // Verify safe location search radius
        assertEquals(10, GraveConfig.MAX_SAFE_LOCATION_SEARCH_RADIUS);
    }

    @Test
    void testWorldConfig() {
        // Verify world settings
        assertTrue(GraveConfig.GRAVES_IN_ALL_WORLDS);
        assertNotNull(GraveConfig.GRAVE_DISABLED_WORLDS);
    }

    @Test
    void testDatabasePersistence() {
        // Verify database persistence is enabled
        assertTrue(GraveConfig.PERSIST_GRAVES);
    }

    @Test
    void testFeatureInterfaceImplementation() {
        // Verify GravesFeature implements Feature interface
        assertTrue(com.nyarutoru.nekoplugin.core.Feature.class.isAssignableFrom(GravesFeature.class));
    }

    @Test
    void testGraveCommandsImplementsInterfaces() {
        // Verify GraveCommands implements required interfaces
        assertTrue(org.bukkit.command.CommandExecutor.class.isAssignableFrom(GraveCommands.class));
        assertTrue(org.bukkit.command.TabCompleter.class.isAssignableFrom(GraveCommands.class));
    }

    @Test
    void testGraveListenerImplementsListener() {
        // Verify GraveListener implements Listener interface
        assertTrue(org.bukkit.event.Listener.class.isAssignableFrom(GraveListener.class));
    }

    @Test
    void testTimeUnitConversion() {
        // Verify time unit conversions are correct
        assertEquals(1200000, TimeUnit.MINUTES.toMillis(20));
        assertEquals(60000, TimeUnit.MINUTES.toMillis(1));
        assertEquals(1000, TimeUnit.SECONDS.toMillis(1));
    }

    @Test
    void testGraveConfigurationValues() {
        // Verify all configuration values are reasonable
        assertTrue(GraveConfig.GRAVE_LIFETIME_MS > 0, "Grave lifetime should be positive");
        assertTrue(GraveConfig.GRAVE_CHECK_INTERVAL_TICKS > 0, "Check interval should be positive");
        assertTrue(GraveConfig.MAX_GRAVES_PER_PLAYER > 0, "Max graves should be positive");
        assertTrue(GraveConfig.MAX_SAFE_LOCATION_SEARCH_RADIUS > 0, "Search radius should be positive");
    }

    @Test
    void testFeatureLifecycle() {
        GravesFeature feature = new GravesFeature();
        
        // Initial state
        assertFalse(feature.isEnabled());
        
        // Note: We can't test onEnable/onDisable without a plugin instance
        // But we verify the feature object can be created
        assertNotNull(feature);
    }

    @Test
    void testGraveFeaturesSummary() {
        // Summary test to verify all grave features are present
        GravesFeature feature = new GravesFeature();
        
        assertEquals("graves", feature.getId());
        assertEquals("Graves", feature.getName());
        
        // Verify config is accessible
        assertNotNull(GraveConfig.GRAVE_LIFETIME_MS);
        assertNotNull(GraveConfig.MAX_GRAVES_PER_PLAYER);
    }
}
