package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GravesValidationTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void featureUsesStableIdAndSingularName() {
        GravesFeature feature = new GravesFeature();
        assertEquals("graves", feature.getId());
        assertEquals("Grave", feature.getName());
        assertFalse(feature.isEnabled());
    }

    @Test
    void commandsUsePaperBasicCommandApi() throws Exception {
        assertTrue(io.papermc.paper.command.brigadier.BasicCommand.class
            .isAssignableFrom(GraveCommands.class.getDeclaredMethod("playerCommand").getReturnType()));
        assertTrue(io.papermc.paper.command.brigadier.BasicCommand.class
            .isAssignableFrom(GraveCommands.class.getDeclaredMethod("adminCommand").getReturnType()));
        assertArrayEquals(new Class<?>[]{String.class, String.class, java.util.Collection.class,
                io.papermc.paper.command.brigadier.BasicCommand.class},
            java.util.Arrays.stream(com.nyarutoru.nekoplugin.NekoPlugin.class.getMethod("registerCommand",
                    String.class, String.class, java.util.Collection.class,
                    io.papermc.paper.command.brigadier.BasicCommand.class).getParameterTypes()).toArray());
    }

    @Test
    void locationReservationsAreAtomicAndReleasable() {
        GraveLocationReservations reservations = new GraveLocationReservations();
        GravePosition position = position(1);
        assertTrue(reservations.reserve(position));
        assertFalse(reservations.reserve(position));
        reservations.release(position);
        assertTrue(reservations.reserve(position));
    }

    @Test
    void actualGraveRejectsClaimsWhileRemovalIsPending() {
        Grave grave = grave(List.of(), 7);
        assertTrue(grave.beginRemoval(Grave.Disposition.DROP));
        assertNull(grave.claimItem(0));
        grave.cancelRemoval();
        assertEquals(Grave.State.ACTIVE, grave.getState());
    }

    @Test
    void graveClaimApiExposesExplicitCommitAndRollback() throws Exception {
        assertEquals(boolean.class, Grave.class.getMethod("commitClaim", Grave.ItemClaim.class).getReturnType());
        assertEquals(boolean.class, Grave.class.getMethod("rollbackClaim", Grave.ItemClaim.class).getReturnType());
        assertEquals(boolean.class, Grave.class.getMethod("hasPendingClaim").getReturnType());
    }

    @Test
    void removalStateRecordsDispositionAndPhase() {
        Grave grave = grave(List.of(), 7);
        assertTrue(grave.beginRemoval(Grave.Disposition.DROP));
        assertEquals(Grave.State.REMOVING, grave.getState());
        assertEquals(Grave.Disposition.DROP, grave.getDisposition());
        grave.markDisposed();
        assertEquals(Grave.State.DISPOSED, grave.getState());
    }

    @Test
    void experienceConsumptionIsIdempotentAndRestorable() {
        Grave grave = grave(List.of(), 13);
        assertEquals(13, grave.consumeExperience());
        assertEquals(0, grave.consumeExperience());
        grave.restoreExperience(13);
        assertEquals(13, grave.getExperience());
    }

    @Test
    void positionKeyUsesWorldIdentityAndBlockCoordinates() {
        GravePosition first = new GravePosition(WORLD, "world", 1, 64, -2);
        GravePosition renamed = new GravePosition(WORLD, "renamed", 1, 64, -2);
        assertEquals(first.key(), renamed.key());
        assertNotEquals(first.key(), position(2).key());
    }

    @Test
    void accessPolicyRequiresUsePermissionForOwnerOrAdminPermission() {
        UUID stranger = UUID.randomUUID();
        assertTrue(GraveAccessPolicy.canAccess(OWNER, OWNER, true, false));
        assertFalse(GraveAccessPolicy.canAccess(OWNER, OWNER, false, false));
        assertFalse(GraveAccessPolicy.canAccess(OWNER, stranger, true, false));
        assertTrue(GraveAccessPolicy.canAccess(OWNER, stranger, false, true));
    }

    @Test
    void snapshotCopiesEncodedPayload() {
        byte[] payload = {1, 2, 3};
        GraveSnapshot snapshot = new GraveSnapshot(UUID.randomUUID(), OWNER, "Neko", position(1), position(1),
            payload, 2, 3, 4, Grave.State.ACTIVE, Grave.Disposition.NONE);
        payload[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, snapshot.items());
        byte[] returned = snapshot.items();
        returned[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, snapshot.items());
    }

    @Test
    void itemCodecUsesPaperFullFidelityByteApis() throws Exception {
        Method encode = GraveItemCodec.class.getDeclaredMethod("encode", List.class);
        Method decode = GraveItemCodec.class.getDeclaredMethod("decode", byte[].class);
        assertEquals(byte[].class, encode.getReturnType());
        assertEquals(List.class, decode.getReturnType());
        assertEquals(byte[].class, ItemStack.class.getMethod("serializeItemsAsBytes", java.util.Collection.class).getReturnType());
    }

    private static Grave grave(List<ItemStack> items, int experience) {
        GravePosition position = position(1);
        return new Grave(UUID.randomUUID(), OWNER, "Neko", position, position, items, experience, 1000L, 2000L);
    }

    private static GravePosition position(int x) { return new GravePosition(WORLD, "world", x, 64, -2); }
}
