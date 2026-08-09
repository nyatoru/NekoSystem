package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;
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
    void featureRegistersSafeSettingsOnly() {
        SettingRegistry registry = new SettingRegistry();
        GravesFeature feature = new GravesFeature();
        feature.registerSettings(registry, new AdminState());

        assertEquals(List.of("future-grave-lifetime-minutes", "safe-search-radius", "max-graves-per-player",
            "expiry-check-interval-seconds", "display-update-interval-seconds"),
            registry.get(feature.getId()).stream().map(descriptor -> descriptor.key()).toList());
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
    void graveBulkClaimApiExposesAtomicLifecycle() throws Exception {
        assertEquals(Grave.AllClaim.class, Grave.class.getMethod("claimAll").getReturnType());
        assertEquals(boolean.class, Grave.class.getMethod("commitAll", Grave.AllClaim.class).getReturnType());
        assertEquals(boolean.class, Grave.class.getMethod("rollbackAll", Grave.AllClaim.class).getReturnType());
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

    @Test
    void graveMarkerAppliesOwnerProfileAndUpdatesBlockState() {
        AtomicReference<ResolvableProfile> appliedProfile = new AtomicReference<>();
        AtomicBoolean updated = new AtomicBoolean();
        Skull skull = proxy(Skull.class, (proxy, method, args) -> {
            if (method.getName().equals("setProfile")) {
                appliedProfile.set((ResolvableProfile) args[0]);
                return null;
            }
            if (method.getName().equals("update") && args.length == 1) {
                updated.set((boolean) args[0]);
                return true;
            }
            return defaultValue(method.getReturnType());
        });
        ResolvableProfile profile = proxy(ResolvableProfile.class,
            (proxy, method, args) -> defaultValue(method.getReturnType()));

        assertTrue(GraveManager.applyMarkerProfile(skull, profile));
        assertSame(profile, appliedProfile.get());
        assertTrue(updated.get());
    }

    @Test
    void graveDisplayShowsOwnerItemsAndRemainingTime() {
        Grave grave = grave(List.of(), 7);

        assertEquals("Grave of Neko\n0 item stacks\nExpires in 0m 1s",
            plainText().serialize(GraveDisplayManager.text(grave, 1500L)));
    }

    @Test
    void graveDisplayRoundsRemainingTimeUp() {
        assertEquals("0m 1s", GraveDisplayManager.formatRemaining(1L));
        assertEquals("1m 1s", GraveDisplayManager.formatRemaining(60_001L));
        assertEquals("0m 0s", GraveDisplayManager.formatRemaining(-1L));
    }

    @Test
    void graveListenerProtectsEnvironmentalMutationEvents() throws Exception {
        assertEquals(void.class, GraveListener.class.getMethod("onFlow", org.bukkit.event.block.BlockFromToEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onBucketEmpty", org.bukkit.event.player.PlayerBucketEmptyEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onPistonExtend", org.bukkit.event.block.BlockPistonExtendEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onPistonRetract", org.bukkit.event.block.BlockPistonRetractEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onEntityChangeBlock", org.bukkit.event.entity.EntityChangeBlockEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onBlockDestroy", com.destroystokyo.paper.event.block.BlockDestroyEvent.class).getReturnType());
        assertEquals(void.class, GraveListener.class.getMethod("onPhysics", org.bukkit.event.block.BlockPhysicsEvent.class).getReturnType());
    }

    @Test
    void graveInventoryCapacityHelperIsNonMutating() throws Exception {
        assertEquals(boolean.class, GraveInventoryCapacity.class.getDeclaredMethod(
            "canFit", ItemStack[].class, int.class, List.class).getReturnType());
        assertEquals(ItemStack[].class, GraveInventoryCapacity.class.getDeclaredMethod(
            "cloneContents", ItemStack[].class).getReturnType());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static Grave grave(List<ItemStack> items, int experience) {
        GravePosition position = position(1);
        return new Grave(UUID.randomUUID(), OWNER, "Neko", position, position, items, experience, 1000L, 2000L);
    }

    private static GravePosition position(int x) { return new GravePosition(WORLD, "world", x, 64, -2); }
}
