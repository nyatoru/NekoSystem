package com.nyarutoru.nekoplugin.api.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility class for PersistentDataContainer operations on ItemStacks.
 */
public final class PDCUtils {

    private PDCUtils() {
    }

    // ========== Read Operations ==========

    /**
     * Read a String value from an item's PDC.
     */
    public static String readString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * Read an Integer value from an item's PDC.
     */
    public static Integer readInt(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    /**
     * Read a Double value from an item's PDC.
     */
    public static Double readDouble(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
    }

    /**
     * Read a Boolean value from an item's PDC (stored as byte).
     */
    public static Boolean readBoolean(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return null;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null ? value != 0 : null;
    }

    // ========== Check Operations ==========

    /**
     * Check if an item's PDC contains a key.
     */
    public static boolean hasKey(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(key);
    }

    /**
     * Check if an item's PDC has a key with a specific String value.
     */
    public static boolean hasValue(ItemStack item, NamespacedKey key, String expectedValue) {
        String value = readString(item, key);
        return expectedValue.equals(value);
    }

    // ========== Write Operations ==========

    /**
     * Set a String value in an item's PDC.
     * Returns the modified item (same instance).
     */
    public static ItemStack setString(ItemStack item, NamespacedKey key, String value) {
        if (item == null)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Set an Integer value in an item's PDC.
     */
    public static ItemStack setInt(ItemStack item, NamespacedKey key, int value) {
        if (item == null)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Set a Double value in an item's PDC.
     */
    public static ItemStack setDouble(ItemStack item, NamespacedKey key, double value) {
        if (item == null)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Set a Boolean value in an item's PDC (stored as byte).
     */
    public static ItemStack setBoolean(ItemStack item, NamespacedKey key, boolean value) {
        if (item == null)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        item.setItemMeta(meta);
        return item;
    }

    // ========== Remove Operations ==========

    /**
     * Remove a key from an item's PDC.
     */
    public static ItemStack remove(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta())
            return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
        return item;
    }

    // ========== Merge Operations ==========

    /**
     * Copy specific PDC values from source to target item.
     * Only copies String and Integer types.
     */
    public static ItemStack merge(ItemStack target, ItemStack source, NamespacedKey... keys) {
        if (target == null || source == null || !source.hasItemMeta())
            return target;

        ItemMeta targetMeta = target.getItemMeta();
        if (targetMeta == null)
            return target;

        PersistentDataContainer sourcePdc = source.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer targetPdc = targetMeta.getPersistentDataContainer();

        for (NamespacedKey key : keys) {
            // Try String first
            String stringValue = sourcePdc.get(key, PersistentDataType.STRING);
            if (stringValue != null) {
                targetPdc.set(key, PersistentDataType.STRING, stringValue);
                continue;
            }

            // Try Integer
            Integer intValue = sourcePdc.get(key, PersistentDataType.INTEGER);
            if (intValue != null) {
                targetPdc.set(key, PersistentDataType.INTEGER, intValue);
                continue;
            }

            // Try Double
            Double doubleValue = sourcePdc.get(key, PersistentDataType.DOUBLE);
            if (doubleValue != null) {
                targetPdc.set(key, PersistentDataType.DOUBLE, doubleValue);
                continue;
            }

            // Try Byte (boolean)
            Byte byteValue = sourcePdc.get(key, PersistentDataType.BYTE);
            if (byteValue != null) {
                targetPdc.set(key, PersistentDataType.BYTE, byteValue);
            }
        }

        target.setItemMeta(targetMeta);
        return target;
    }

    /**
     * Copy all PDC values from source to target item.
     */
    public static ItemStack mergeAll(ItemStack target, ItemStack source) {
        if (target == null || source == null || !source.hasItemMeta())
            return target;

        ItemMeta targetMeta = target.getItemMeta();
        if (targetMeta == null)
            return target;

        PersistentDataContainer sourcePdc = source.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer targetPdc = targetMeta.getPersistentDataContainer();

        for (NamespacedKey key : sourcePdc.getKeys()) {
            // Copy each key-value pair
            String stringValue = sourcePdc.get(key, PersistentDataType.STRING);
            if (stringValue != null) {
                targetPdc.set(key, PersistentDataType.STRING, stringValue);
                continue;
            }

            Integer intValue = sourcePdc.get(key, PersistentDataType.INTEGER);
            if (intValue != null) {
                targetPdc.set(key, PersistentDataType.INTEGER, intValue);
                continue;
            }

            Double doubleValue = sourcePdc.get(key, PersistentDataType.DOUBLE);
            if (doubleValue != null) {
                targetPdc.set(key, PersistentDataType.DOUBLE, doubleValue);
                continue;
            }

            Byte byteValue = sourcePdc.get(key, PersistentDataType.BYTE);
            if (byteValue != null) {
                targetPdc.set(key, PersistentDataType.BYTE, byteValue);
            }
        }

        target.setItemMeta(targetMeta);
        return target;
    }
}
