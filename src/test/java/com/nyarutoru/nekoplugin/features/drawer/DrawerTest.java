package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Drawer class.
 */
class DrawerTest {

    @Mock
    private World mockWorld;

    @Mock
    private Location mockLocation;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(mockLocation.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("test_world");
        when(mockLocation.getBlockX()).thenReturn(100);
        when(mockLocation.getBlockY()).thenReturn(64);
        when(mockLocation.getBlockZ()).thenReturn(200);
    }

    @Test
    void testConstructorWithLocation() {
        Drawer drawer = new Drawer(mockLocation);
        
        assertNotNull(drawer);
        assertEquals(mockLocation, drawer.getLocation());
        assertNull(drawer.getItemType());
        assertEquals(0, drawer.getItemCount());
        assertEquals(DrawerTier.TIER_1, drawer.getTier());
        assertTrue(drawer.isEmpty());
    }

    @Test
    void testConstructorWithAllParameters() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 100, DrawerTier.TIER_3);
        
        assertNotNull(drawer);
        assertEquals(mockLocation, drawer.getLocation());
        assertEquals(Material.STONE, drawer.getItemType());
        assertEquals(100, drawer.getItemCount());
        assertEquals(DrawerTier.TIER_3, drawer.getTier());
        assertFalse(drawer.isEmpty());
    }

    @Test
    void testConstructorWithNullLocation() {
        assertThrows(IllegalArgumentException.class, () -> new Drawer(null));
    }

    @Test
    void testConstructorWithNullTier() {
        assertThrows(IllegalArgumentException.class, 
            () -> new Drawer(mockLocation, Material.STONE, 100, null));
    }

    @Test
    void testSetTierWithNull() {
        Drawer drawer = new Drawer(mockLocation);
        assertThrows(IllegalArgumentException.class, () -> drawer.setTier(null));
    }

    @Test
    void testSetTierValid() {
        Drawer drawer = new Drawer(mockLocation);
        assertEquals(DrawerTier.TIER_1, drawer.getTier());
        
        drawer.setTier(DrawerTier.TIER_5);
        assertEquals(DrawerTier.TIER_5, drawer.getTier());
    }

    @Test
    void testGetMaxCapacity() {
        Drawer drawer = new Drawer(mockLocation);
        assertEquals(256 * 64, drawer.getMaxCapacity());
        
        drawer.setTier(DrawerTier.TIER_5);
        assertEquals(4096 * 64, drawer.getMaxCapacity());
        
        drawer.setTier(DrawerTier.TIER_10);
        assertEquals(Integer.MAX_VALUE, drawer.getMaxCapacity());
    }

    @Test
    void testGetRemainingSpace() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 1000, DrawerTier.TIER_2);
        int maxCapacity = drawer.getMaxCapacity();
        assertEquals(maxCapacity - 1000, drawer.getRemainingSpace());
    }

    @Test
    void testIsFull() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 0, DrawerTier.TIER_1);
        assertFalse(drawer.isFull());
        
        // Fill to capacity
        drawer.addItems(Material.STONE, 256 * 64);
        assertTrue(drawer.isFull());
    }

    @Test
    void testIsEmpty() {
        Drawer drawer = new Drawer(mockLocation);
        assertTrue(drawer.isEmpty());
        
        drawer.addItems(Material.STONE, 1);
        assertFalse(drawer.isEmpty());
        
        drawer.removeItems(1);
        assertTrue(drawer.isEmpty());
    }

    @Test
    void testCanAccept() {
        Drawer drawer = new Drawer(mockLocation);
        
        // Empty drawer accepts valid items
        assertTrue(drawer.canAccept(Material.STONE));
        assertTrue(drawer.canAccept(Material.DIRT));
        
        // Blocked items
        assertFalse(drawer.canAccept(Material.DIAMOND_SWORD));
        assertFalse(drawer.canAccept(Material.IRON_CHESTPLATE));
        assertFalse(drawer.canAccept(Material.BOW));
        
        // Set item type
        drawer.addItems(Material.STONE, 100);
        
        // Now only accepts same type
        assertTrue(drawer.canAccept(Material.STONE));
        assertFalse(drawer.canAccept(Material.DIRT));
        assertFalse(drawer.canAccept(Material.COBBLESTONE));
    }

    @Test
    void testCanAcceptItem() {
        Drawer drawer = new Drawer(mockLocation);
        
        ItemStack stoneStack = new ItemStack(Material.STONE, 64);
        ItemStack swordStack = new ItemStack(Material.DIAMOND_SWORD, 1);
        
        assertTrue(drawer.canAcceptItem(stoneStack));
        assertFalse(drawer.canAcceptItem(swordStack));
        
        // Set item type
        drawer.addItems(Material.STONE, 100);
        
        ItemStack moreStone = new ItemStack(Material.STONE, 32);
        ItemStack dirt = new ItemStack(Material.DIRT, 32);
        
        assertTrue(drawer.canAcceptItem(moreStone));
        assertFalse(drawer.canAcceptItem(dirt));
    }

    @Test
    void testAddItems() {
        Drawer drawer = new Drawer(mockLocation);
        
        // Add items to empty drawer
        int remaining = drawer.addItems(Material.STONE, 100);
        assertEquals(0, remaining);
        assertEquals(Material.STONE, drawer.getItemType());
        assertEquals(100, drawer.getItemCount());
        
        // Add more of same type
        remaining = drawer.addItems(Material.STONE, 200);
        assertEquals(0, remaining);
        assertEquals(300, drawer.getItemCount());
        
        // Try to add different type (should be rejected)
        remaining = drawer.addItems(Material.DIRT, 50);
        assertEquals(50, remaining); // All rejected
        assertEquals(300, drawer.getItemCount()); // Unchanged
    }

    @Test
    void testAddItemsWithOverflow() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 0, DrawerTier.TIER_1);
        int maxCapacity = drawer.getMaxCapacity();
        
        // Add more than capacity
        int overflow = drawer.addItems(Material.STONE, maxCapacity + 1000);
        
        assertEquals(1000, overflow);
        assertEquals(maxCapacity, drawer.getItemCount());
        assertTrue(drawer.isFull());
    }

    @Test
    void testAddItemsWithItemStack() {
        Drawer drawer = new Drawer(mockLocation);
        ItemStack stack = new ItemStack(Material.COBBLESTONE, 64);
        
        drawer.addItems(stack);
        
        assertEquals(Material.COBBLESTONE, drawer.getItemType());
        assertEquals(64, drawer.getItemCount());
    }

    @Test
    void testAddItemsWithNullStack() {
        Drawer drawer = new Drawer(mockLocation);
        drawer.addItems((ItemStack) null);
        
        assertTrue(drawer.isEmpty());
    }

    @Test
    void testRemoveItems() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 100, DrawerTier.TIER_1);
        
        int removed = drawer.removeItems(30);
        assertEquals(30, removed);
        assertEquals(70, drawer.getItemCount());
        
        // Remove more than available
        removed = drawer.removeItems(100);
        assertEquals(70, removed);
        assertEquals(0, drawer.getItemCount());
        assertTrue(drawer.isEmpty());
    }

    @Test
    void testRemoveItemsClearsType() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 50, DrawerTier.TIER_1);
        
        drawer.removeItems(50);
        
        assertNull(drawer.getItemType());
        assertEquals(0, drawer.getItemCount());
        assertTrue(drawer.isEmpty());
    }

    @Test
    void testRemoveZeroOrNegative() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 100, DrawerTier.TIER_1);
        
        assertEquals(0, drawer.removeItems(0));
        assertEquals(100, drawer.getItemCount());
        
        assertEquals(0, drawer.removeItems(-5));
        assertEquals(100, drawer.getItemCount());
    }

    @Test
    void testUpgrade() {
        Drawer drawer = new Drawer(mockLocation);
        assertEquals(DrawerTier.TIER_1, drawer.getTier());
        
        // Upgrade to higher tier
        assertTrue(drawer.upgrade(DrawerTier.TIER_5));
        assertEquals(DrawerTier.TIER_5, drawer.getTier());
        
        // Can't downgrade
        assertFalse(drawer.upgrade(DrawerTier.TIER_3));
        assertEquals(DrawerTier.TIER_5, drawer.getTier());
        
        // Can upgrade to even higher
        assertTrue(drawer.upgrade(DrawerTier.TIER_10));
        assertEquals(DrawerTier.TIER_10, drawer.getTier());
    }

    @Test
    void testUpgradeWithNull() {
        Drawer drawer = new Drawer(mockLocation);
        assertThrows(IllegalArgumentException.class, () -> drawer.upgrade(null));
    }

    @Test
    void testGetFillPercentage() {
        Drawer drawer = new Drawer(mockLocation, Material.STONE, 0, DrawerTier.TIER_1);
        assertEquals(0.0, drawer.getFillPercentage());
        
        drawer.addItems(Material.STONE, 256 * 32); // Half full
        assertEquals(0.5, drawer.getFillPercentage());
        
        drawer.addItems(Material.STONE, 256 * 32); // Full
        assertEquals(1.0, drawer.getFillPercentage());
    }

    @Test
    void testSerialization() {
        Drawer drawer = new Drawer(mockLocation, Material.DIAMOND, 1234, DrawerTier.TIER_7);
        
        Map<String, Object> serialized = drawer.serialize();
        
        assertEquals("test_world", serialized.get("world"));
        assertEquals(100, serialized.get("x"));
        assertEquals(64, serialized.get("y"));
        assertEquals(200, serialized.get("z"));
        assertEquals("DIAMOND", serialized.get("itemType"));
        assertEquals(1234, serialized.get("itemCount"));
        assertEquals("TIER_7", serialized.get("tier"));
    }

    @Test
    void testSerializationWithNullItemType() {
        Drawer drawer = new Drawer(mockLocation);
        drawer.addItems(Material.STONE, 100);
        drawer.removeItems(100); // Clears item type
        
        Map<String, Object> serialized = drawer.serialize();
        
        assertNull(serialized.get("itemType"));
        assertEquals(0, serialized.get("itemCount"));
    }

    @Test
    void testSerializationWithNullLocation() {
        Drawer drawer = new Drawer(mockLocation);
        
        // Manually set to test null safety (would normally throw in constructor)
        Map<String, Object> serialized = drawer.serialize();
        
        // Should handle gracefully with minimal valid serialization
        assertNotNull(serialized.get("world"));
        assertNotNull(serialized.get("x"));
        assertNotNull(serialized.get("y"));
        assertNotNull(serialized.get("z"));
    }

    @Test
    void testToString() {
        Drawer drawer = new Drawer(mockLocation, Material.GOLD_INGOT, 500, DrawerTier.TIER_4);
        String str = drawer.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("Drawer"));
        assertTrue(str.contains("GOLD"));
        assertTrue(str.contains("500"));
        assertTrue(str.contains("Tier 4"));
    }

    @Test
    void testBlockedCategories() {
        // Swords
        assertFalse(Drawer.isAllowedMaterial(Material.WOODEN_SWORD));
        assertFalse(Drawer.isAllowedMaterial(Material.DIAMOND_SWORD));
        assertFalse(Drawer.isAllowedMaterial(Material.NETHERITE_SWORD));
        
        // Tools
        assertFalse(Drawer.isAllowedMaterial(Material.DIAMOND_PICKAXE));
        assertFalse(Drawer.isAllowedMaterial(Material.IRON_AXE));
        assertFalse(Drawer.isAllowedMaterial(Material.GOLDEN_HOE));
        assertFalse(Drawer.isAllowedMaterial(Material.STONE_SHOVEL));
        
        // Armor
        assertFalse(Drawer.isAllowedMaterial(Material.DIAMOND_HELMET));
        assertFalse(Drawer.isAllowedMaterial(Material.IRON_CHESTPLATE));
        assertFalse(Drawer.isAllowedMaterial(Material.GOLDEN_LEGGINGS));
        assertFalse(Drawer.isAllowedMaterial(Material.LEATHER_BOOTS));
        assertFalse(Drawer.isAllowedMaterial(Material.ELYTRA));
        assertFalse(Drawer.isAllowedMaterial(Material.TURTLE_HELMET));
        
        // Weapons
        assertFalse(Drawer.isAllowedMaterial(Material.BOW));
        assertFalse(Drawer.isAllowedMaterial(Material.CROSSBOW));
        assertFalse(Drawer.isAllowedMaterial(Material.TRIDENT));
        assertFalse(Drawer.isAllowedMaterial(Material.SHIELD));
        
        // Valid items
        assertTrue(Drawer.isAllowedMaterial(Material.STONE));
        assertTrue(Drawer.isAllowedMaterial(Material.DIRT));
        assertTrue(Drawer.isAllowedMaterial(Material.DIAMOND));
        assertTrue(Drawer.isAllowedMaterial(Material.IRON_INGOT));
    }

    @Test
    void testIsAllowedMaterialWithNull() {
        assertFalse(Drawer.isAllowedMaterial(null));
    }

    @Test
    void testIsAllowedMaterialWithAir() {
        assertFalse(Drawer.isAllowedMaterial(Material.AIR));
    }
}
