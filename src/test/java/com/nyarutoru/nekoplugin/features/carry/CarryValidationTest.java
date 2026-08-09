package com.nyarutoru.nekoplugin.features.carry;

import io.papermc.paper.block.TileStateInventoryHolder;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CarryValidationTest {
    @Test
    void featureUsesStableIdentity() {
        CarryFeature feature = new CarryFeature();
        assertEquals("carry", feature.getId());
        assertEquals("Carry", feature.getName());
        assertFalse(feature.isEnabled());
    }

    @Test
    void animalCategoryAllowsSafeAnimals() {
        Animals animal = proxy(Animals.class, (proxy, method, args) -> switch (method.getName()) {
            case "isValid" -> true;
            case "isDead", "isInsideVehicle", "isLeashed" -> false;
            case "getPassengers" -> List.of();
            default -> defaultValue(method.getReturnType());
        });
        assertTrue(CarryPolicy.isCarryableMob(animal));
    }

    @Test
    void policyRejectsPlayersAndOccupiedAnimals() {
        Player player = proxy(Player.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        assertFalse(CarryPolicy.isCarryableMob(player));

        Animals occupied = proxy(Animals.class, (proxy, method, args) -> switch (method.getName()) {
            case "isValid" -> true;
            case "isDead", "isInsideVehicle", "isLeashed" -> false;
            case "getPassengers" -> List.of(proxy(Entity.class,
                (entity, entityMethod, entityArgs) -> defaultValue(entityMethod.getReturnType())));
            default -> defaultValue(method.getReturnType());
        });
        assertFalse(CarryPolicy.isCarryableMob(occupied));
    }

    @Test
    void supportedBlockCategoriesAreHardcoded() {
        assertTrue(CarryPolicy.isCarryableBlockCategory(true, false, false));
        assertTrue(CarryPolicy.isCarryableBlockCategory(false, true, false));
        assertTrue(CarryPolicy.isCarryableBlockCategory(false, false, true));
        assertFalse(CarryPolicy.isCarryableBlockCategory(false, false, false));
    }

    @Test
    void pickupClearsLiveInventoryWithoutClearingSnapshot() {
        AtomicBoolean liveCleared = new AtomicBoolean();
        AtomicBoolean snapshotCleared = new AtomicBoolean();
        Inventory live = inventory(liveCleared);
        Inventory snapshot = inventory(snapshotCleared);
        BlockState state = proxy(TileStateInventoryHolder.class, (proxy, method, args) -> switch (method.getName()) {
            case "getInventory" -> live;
            case "getSnapshotInventory" -> snapshot;
            default -> defaultValue(method.getReturnType());
        });

        CarryManager.clearLiveInventory(state);

        assertTrue(liveCleared.get());
        assertFalse(snapshotCleared.get());
    }

    @Test
    void listenerHandlesRequiredCleanupEvents() throws Exception {
        assertEquals(void.class, CarryListener.class.getMethod("onPlayerQuit",
            org.bukkit.event.player.PlayerQuitEvent.class).getReturnType());
        assertEquals(void.class, CarryListener.class.getMethod("onPlayerDeath",
            org.bukkit.event.entity.PlayerDeathEvent.class).getReturnType());
        assertEquals(void.class, CarryListener.class.getMethod("onPlayerTeleport",
            org.bukkit.event.player.PlayerTeleportEvent.class).getReturnType());
        assertEquals(void.class, CarryListener.class.getMethod("onEntityDismount",
            org.bukkit.event.entity.EntityDismountEvent.class).getReturnType());
        assertEquals(void.class, CarryManager.class.getDeclaredMethod("releasePassenger",
            Entity.class, org.bukkit.Location.class).getReturnType());
    }

    private static Inventory inventory(AtomicBoolean cleared) {
        return proxy(Inventory.class, (proxy, method, args) -> {
            if (method.getName().equals("clear") && method.getParameterCount() == 0) {
                cleared.set(true);
            }
            return defaultValue(method.getReturnType());
        });
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
}
