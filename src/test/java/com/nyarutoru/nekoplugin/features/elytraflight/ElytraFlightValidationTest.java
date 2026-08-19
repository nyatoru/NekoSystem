package com.nyarutoru.nekoplugin.features.elytraflight;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraFlightValidationTest {

    @Test
    void featureRegistersFlightSettings() {
        SettingRegistry registry = new SettingRegistry();
        ElytraFlightFeature feature = new ElytraFlightFeature();
        feature.registerSettings(registry, new AdminState());

        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("pearl-flight-seconds")));
        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("elytra-durability-multiplier")));
        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("flight-shift-count")));
    }

    @Test
    void listenerHandlesSneakAndQuitEvents() throws Exception {
        assertEquals(void.class, ElytraFlightListener.class.getMethod(
                "onSneak", org.bukkit.event.player.PlayerToggleSneakEvent.class).getReturnType());
        assertEquals(void.class, ElytraFlightListener.class.getMethod(
                "onQuit", org.bukkit.event.player.PlayerQuitEvent.class).getReturnType());
    }

    @Test
    void defaultSettingsMatchSpec() {
        ElytraFlightFeature feature = new ElytraFlightFeature();
        SettingRegistry registry = new SettingRegistry();
        feature.registerSettings(registry, new AdminState());

        assertEquals(6, registry.get(feature.getId()).stream()
                .filter(descriptor -> descriptor.key().equals("pearl-flight-seconds"))
                .findFirst().orElseThrow().defaultValue());
        assertEquals(3, registry.get(feature.getId()).stream()
                .filter(descriptor -> descriptor.key().equals("elytra-durability-multiplier"))
                .findFirst().orElseThrow().defaultValue());
    }
}
