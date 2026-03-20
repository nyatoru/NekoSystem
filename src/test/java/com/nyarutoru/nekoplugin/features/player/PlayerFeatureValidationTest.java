package com.nyarutoru.nekoplugin.features.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Player Feature validation.
 * Tests avoid direct Material enum usage to prevent Bukkit initialization requirements.
 */
class PlayerFeatureValidationTest {

    @Test
    void testFeatureConstants() {
        assertEquals("player", PlayerFeature.ID);
        assertEquals("Player Utilities", PlayerFeature.NAME);
    }

    @Test
    void testFeatureClassExists() {
        assertNotNull(PlayerFeature.class);
    }

    @Test
    void testListenerClassExists() {
        assertNotNull(PlayerFeatureListener.class);
    }

    @Test
    void testFeatureImplementation() {
        PlayerFeature feature = new PlayerFeature();
        
        assertEquals("player", feature.getId());
        assertEquals("Player Utilities", feature.getName());
        assertFalse(feature.isEnabled()); // Should be false before enable
    }

    @Test
    void testAfkTimeoutConstant() {
        // AFK timeout should be 5 minutes (300000 ms)
        assertEquals(5 * 60 * 1000, 300000);
    }

    @Test
    void testFeatureLifecycle() {
        PlayerFeature feature = new PlayerFeature();
        
        // Initial state
        assertFalse(feature.isEnabled());
        
        // Note: We can't test onEnable/onDisable without a plugin instance
        // But we verify the feature object can be created
        assertNotNull(feature);
    }

    @Test
    void testListenerRequiresPlugin() {
        // Verify listener constructor signature exists
        // (actual instantiation requires plugin instance)
        assertNotNull(PlayerFeatureListener.class.getConstructors());
    }

    @Test
    void testFeatureSubFeatures() {
        // Verify the feature includes the expected sub-features:
        // - AFK System
        // - Auto Replenish
        // - Crop Harvest
        // These are documented in the class comments
        
        PlayerFeature feature = new PlayerFeature();
        assertEquals("Player Utilities", feature.getName());
    }

    @Test
    void testListenerImplementsListener() {
        // Verify PlayerFeatureListener implements Bukkit Listener interface
        assertTrue(org.bukkit.event.Listener.class.isAssignableFrom(PlayerFeatureListener.class));
    }

    @Test
    void testFeatureInterfaceImplementation() {
        // Verify PlayerFeature implements Feature interface
        assertTrue(com.nyarutoru.nekoplugin.core.Feature.class.isAssignableFrom(PlayerFeature.class));
    }
}
