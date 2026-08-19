package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PlayerHeadValidationTest {

    @Test
    void playerFeatureRegistersPumpkinHeadSettings() {
        SettingRegistry registry = new SettingRegistry();
        PlayerFeature feature = new PlayerFeature();
        feature.registerSettings(registry, new AdminState());

        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("pumpkin-cost-levels")));
        assertTrue(registry.get(feature.getId()).stream()
                .anyMatch(descriptor -> descriptor.key().equals("pumpkin-shift-count")));
    }

    @Test
    void onlyMinecraftTextureUrlsAreAccepted() {
        assertTrue(PlayerHeadListener.isTextureUrl("https://textures.minecraft.net/texture/abc123"));
        assertTrue(PlayerHeadListener.isTextureUrl("http://textures.minecraft.net/texture/abc123"));
        assertFalse(PlayerHeadListener.isTextureUrl("https://evil.example.com/texture/abc123"));
        assertFalse(PlayerHeadListener.isTextureUrl("ftp://textures.minecraft.net/texture/abc123"));
        assertFalse(PlayerHeadListener.isTextureUrl("not a url"));
    }

    @Test
    void urlTexturesValueEncodesTheSkinUrl() {
        String value = PlayerHeadListener.texturesValueFromUrl("https://textures.minecraft.net/texture/abc123");
        String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"url\":\"https://textures.minecraft.net/texture/abc123\""));
    }

    @Test
    void jsonStringExtractsOnlyTheRequestedKey() {
        assertEquals("1234567890abcdef",
                PlayerHeadListener.parseJsonString("{\"id\":\"1234567890abcdef\",\"name\":\"Neko\"}", "id"));
        assertNull(PlayerHeadListener.parseJsonString("{\"name\":\"Neko\"}", "id"));
    }

    @Test
    void playerNamesFollowMinecraftRules() {
        assertTrue(PlayerHeadListener.isValidName("Notch"));
        assertTrue(PlayerHeadListener.isValidName("Neko_Chan"));
        assertFalse(PlayerHeadListener.isValidName("ab"));
        assertFalse(PlayerHeadListener.isValidName("a".repeat(17)));
        assertFalse(PlayerHeadListener.isValidName("bad name!"));
    }

    @Test
    void xpCostMatchesVanillaTotalXpCurve() {
        assertEquals(0, PlayerHeadListener.xpToReachLevel(0));
        assertEquals(160, PlayerHeadListener.xpToReachLevel(10));
        assertEquals(352, PlayerHeadListener.xpToReachLevel(16));
        assertEquals(394, PlayerHeadListener.xpToReachLevel(17));
        assertEquals(1507, PlayerHeadListener.xpToReachLevel(31));
        assertEquals(1628, PlayerHeadListener.xpToReachLevel(32));
    }

    @Test
    void namemcLinksResolveToPlayerNameOrUuid() {
        assertEquals("Notch", PlayerHeadListener.namemcKey("https://namemc.com/profile/Notch.1"));
        assertEquals("Notch", PlayerHeadListener.namemcKey("https://namemc.com/skin/Notch.1"));
        assertEquals("Notch", PlayerHeadListener.namemcKey("https://www.namemc.com/profile/Notch.1/100"));
        assertEquals("5b3a1f7f-8b8a-4b7e-9e4a-2c9f5b6a7c8d",
                PlayerHeadListener.namemcKey("https://namemc.com/profile/5b3a1f7f-8b8a-4b7e-9e4a-2c9f5b6a7c8d"));
        assertNull(PlayerHeadListener.namemcKey("https://evil.com/profile/Notch.1"));
        assertNull(PlayerHeadListener.namemcKey("not a url"));
        assertNull(PlayerHeadListener.namemcKey("https://namemc.com/"));
    }

    @Test
    void uuidDetectionMatchesDashedUuidOnly() {
        assertTrue(PlayerHeadListener.isUuid("5b3a1f7f-8b8a-4b7e-9e4a-2c9f5b6a7c8d"));
        assertFalse(PlayerHeadListener.isUuid("Notch"));
        assertFalse(PlayerHeadListener.isUuid("abc"));
    }

    @Test
    void listenerHandlesSneakAndQuitEvents() throws Exception {
        assertEquals(void.class, PlayerHeadListener.class.getMethod(
                "onSneak", org.bukkit.event.player.PlayerToggleSneakEvent.class).getReturnType());
        assertEquals(void.class, PlayerHeadListener.class.getMethod(
                "onQuit", org.bukkit.event.player.PlayerQuitEvent.class).getReturnType());
    }
}
