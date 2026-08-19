package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetherCoordValidationTest {

    @Test
    void playerFeatureRegistersCoordShiftSetting() {
        SettingRegistry registry = new SettingRegistry();
        PlayerFeature feature = new PlayerFeature();
        feature.registerSettings(registry, new AdminState());

        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("coord-shift-count")));
    }

    @Test
    void overworldToNetherDividesByEight() {
        assertEquals(12, NetherCoordListener.netherX(100));
        assertEquals(25, NetherCoordListener.netherZ(200));
        assertEquals(0, NetherCoordListener.netherX(7));
        assertEquals(-12, NetherCoordListener.netherX(-100));
    }

    @Test
    void netherToOverworldMultipliesByEight() {
        assertEquals(800, NetherCoordListener.overworldX(100));
        assertEquals(40, NetherCoordListener.overworldZ(5));
        assertEquals(-800, NetherCoordListener.overworldX(-100));
    }

    @Test
    void conversionIsRoundTripConsistent() {
        int x = 12344;
        assertEquals(x, NetherCoordListener.overworldX(NetherCoordListener.netherX(x)));
        int z = -54320;
        assertEquals(z, NetherCoordListener.overworldZ(NetherCoordListener.netherZ(z)));
    }

    @Test
    void listenerHandlesSneakAndQuitEvents() throws Exception {
        assertEquals(void.class, NetherCoordListener.class.getMethod(
                "onSneak", org.bukkit.event.player.PlayerToggleSneakEvent.class).getReturnType());
        assertEquals(void.class, NetherCoordListener.class.getMethod(
                "onQuit", org.bukkit.event.player.PlayerQuitEvent.class).getReturnType());
    }
}
