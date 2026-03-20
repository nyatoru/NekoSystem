package com.nyarutoru.nekoplugin.features.drawer.data;

import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a single drawer that stores items of one type.
 */
public class Drawer implements ConfigurationSerializable {

    private static final Set<String> BLOCKED_CATEGORIES = Set.of(
            "_SWORD", "_AXE", "_HOE", "_PICKAXE", "_SHOVEL",
            "BOW", "CROSSBOW", "TRIDENT", "SHIELD",
            "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS",
            "ELYTRA", "TURTLE_HELMET");
    private final Location location;
    private Material itemType;
    private int itemCount;
    private DrawerTier tier;

    public Drawer(Location location) {
        this(location, null, 0, DrawerTier.TIER_1);
    }

    public Drawer(Location location, Material itemType, int itemCount, DrawerTier tier) {
        if (location == null) {
            throw new IllegalArgumentException("Drawer location cannot be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Drawer tier cannot be null");
        }
        this.location = location;
        this.itemType = itemType;
        this.itemCount = itemCount;
        this.tier = tier;
    }

    public static boolean isAllowedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return false;
        if (DrawerRecipes.isDrawerItem(item))
            return false;
        if (!item.getEnchantments().isEmpty())
            return false;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            return false;

        String materialName = item.getType().name();
        for (String blocked : BLOCKED_CATEGORIES) {
            if (materialName.contains(blocked))
                return false;
        }
        return true;
    }

    public static boolean isAllowedMaterial(Material material) {
        if (material == null || !material.isItem())
            return false;

        String materialName = material.name();
        for (String blocked : BLOCKED_CATEGORIES) {
            if (materialName.contains(blocked))
                return false;
        }
        return true;
    }

    public static Drawer deserialize(Map<String, Object> map, org.bukkit.Server server) {
        String worldName = (String) map.get("world");
        int x = (int) map.get("x");
        int y = (int) map.get("y");
        int z = (int) map.get("z");

        org.bukkit.World world = server.getWorld(worldName);
        if (world == null)
            return null;

        Location location = new Location(world, x, y, z);
        String itemTypeName = (String) map.get("itemType");
        Material itemType = itemTypeName != null ? Material.getMaterial(itemTypeName) : null;
        int itemCount = (int) map.get("itemCount");
        DrawerTier tier = DrawerTier.getByName((String) map.get("tier"));

        return new Drawer(location, itemType, itemCount, tier);
    }

    public Location getLocation() {
        return location;
    }

    public Material getItemType() {
        return itemType;
    }

    public int getItemCount() {
        return itemCount;
    }

    public DrawerTier getTier() {
        return tier;
    }

    public void setTier(DrawerTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("Drawer tier cannot be null");
        }
        this.tier = tier;
    }

    public int getMaxCapacity() {
        return tier != null ? tier.getMaxItems() : 256;
    }

    public int getRemainingSpace() {
        return getMaxCapacity() - itemCount;
    }

    public boolean isFull() {
        return itemCount >= getMaxCapacity();
    }

    public boolean isEmpty() {
        return itemCount == 0 || itemType == null;
    }

    public boolean canAccept(Material material) {
        if (material == null || !material.isItem())
            return false;
        if (!isAllowedMaterial(material))
            return false;
        return isEmpty() || itemType == material;
    }

    public boolean canAcceptItem(ItemStack item) {
        if (!isAllowedItem(item))
            return false;
        return canAccept(item.getType());
    }

    public int addItems(Material material, int amount) {
        if (!canAccept(material) || amount <= 0)
            return amount;
        if (isEmpty())
            this.itemType = material;

        int spaceAvailable = getRemainingSpace();
        int toAdd = Math.min(amount, spaceAvailable);
        this.itemCount += toAdd;
        return amount - toAdd;
    }

    public int addItems(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR)
            return 0;
        return addItems(itemStack.getType(), itemStack.getAmount());
    }

    public int removeItems(int amount) {
        if (amount <= 0 || isEmpty())
            return 0;

        int toRemove = Math.min(amount, itemCount);
        this.itemCount -= toRemove;

        if (this.itemCount <= 0) {
            this.itemType = null;
            this.itemCount = 0;
        }
        return toRemove;
    }

    public boolean upgrade(DrawerTier newTier) {
        if (newTier == null) {
            throw new IllegalArgumentException("New tier cannot be null");
        }
        if (newTier.getLevel() > this.tier.getLevel()) {
            this.tier = newTier;
            return true;
        }
        return false;
    }

    public double getFillPercentage() {
        int maxCapacity = getMaxCapacity();
        if (maxCapacity <= 0)
            return 0;
        return (double) itemCount / maxCapacity;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        
        // Null safety: world can be unloaded during serialization
        if (location == null || location.getWorld() == null) {
            // Return minimal valid serialization - will be cleaned up on load
            map.put("world", "unknown");
            map.put("x", 0);
            map.put("y", 0);
            map.put("z", 0);
        } else {
            map.put("world", location.getWorld().getName());
            map.put("x", location.getBlockX());
            map.put("y", location.getBlockY());
            map.put("z", location.getBlockZ());
        }
        
        map.put("itemType", itemType != null ? itemType.name() : null);
        map.put("itemCount", itemCount);
        map.put("tier", tier != null ? tier.name() : "TIER_1");
        return map;
    }

    @Override
    public String toString() {
        return String.format("Drawer[loc=%s, type=%s, count=%d, tier=%s]",
                location, itemType, itemCount, tier.getDisplayName());
    }
}
