package com.nyarutoru.nekoplugin.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;
import java.util.Set;

/**
 * Utility class for item-related operations.
 */
public class ItemUtils {

    private static final Random RANDOM = new Random();

    // Tool material sets
    public static final Set<Material> PICKAXES = Set.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);

    public static final Set<Material> AXES = Set.of(
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE);

    public static final Set<Material> SHOVELS = Set.of(
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL);

    public static final Set<Material> HOES = Set.of(
            Material.WOODEN_HOE, Material.STONE_HOE,
            Material.IRON_HOE, Material.GOLDEN_HOE,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE);

    public static final Set<Material> SWORDS = Set.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);

    private ItemUtils() {
    }

    /**
     * Checks if the item is a pickaxe.
     */
    public static boolean isPickaxe(ItemStack item) {
        return item != null && PICKAXES.contains(item.getType());
    }

    /**
     * Checks if the item is an axe.
     */
    public static boolean isAxe(ItemStack item) {
        return item != null && AXES.contains(item.getType());
    }

    /**
     * Checks if the item is any type of tool.
     */
    public static boolean isTool(ItemStack item) {
        if (item == null)
            return false;
        Material type = item.getType();
        return PICKAXES.contains(type) || AXES.contains(type) ||
                SHOVELS.contains(type) || HOES.contains(type) || SWORDS.contains(type);
    }

    /**
     * Checks if the item is unbreakable.
     */
    public static boolean isUnbreakable(ItemStack item) {
        if (item == null)
            return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.isUnbreakable();
    }

    /**
     * Gets the Unbreaking enchantment level of an item.
     */
    public static int getUnbreakingLevel(ItemStack item) {
        if (item == null)
            return 0;
        return item.getEnchantmentLevel(Enchantment.UNBREAKING);
    }

    /**
     * Checks if durability should be applied based on Unbreaking enchantment.
     * Unbreaking gives a chance to NOT consume durability.
     * Formula: 100 / (unbreakingLevel + 1) % chance to consume durability
     * 
     * @param unbreakingLevel The Unbreaking enchantment level
     * @return true if durability should be consumed, false if saved by Unbreaking
     */
    public static boolean shouldConsumeDurability(int unbreakingLevel) {
        if (unbreakingLevel <= 0)
            return true;
        return RANDOM.nextInt(unbreakingLevel + 1) == 0;
    }

    /**
     * Gets the current durability (damage) of an item.
     * 
     * @return -1 if item is not damageable
     */
    public static int getDurability(ItemStack item) {
        if (item == null)
            return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return -1;
    }

    /**
     * Gets the remaining durability of an item.
     * 
     * @return -1 if item is not damageable
     */
    public static int getRemainingDurability(ItemStack item) {
        if (item == null)
            return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return item.getType().getMaxDurability() - damageable.getDamage();
        }
        return -1;
    }

    /**
     * Checks if the item would break if damaged by the given amount.
     */
    public static boolean wouldBreakFromDamage(ItemStack item, int damageAmount) {
        if (item == null || isUnbreakable(item))
            return false;
        int remaining = getRemainingDurability(item);
        return remaining != -1 && remaining <= damageAmount;
    }

    /**
     * Applies durability damage to an item, respecting Unbreaking and Unbreakable.
     * 
     * @param item   The item to damage
     * @param amount The base damage amount
     * @return true if damage was applied, false if item is unbreakable or saved by
     *         Unbreaking
     */
    public static boolean applyDurabilityDamage(ItemStack item, int amount) {
        if (item == null)
            return false;
        if (isUnbreakable(item))
            return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable))
            return false;

        int unbreakingLevel = getUnbreakingLevel(item);
        int actualDamage = 0;

        for (int i = 0; i < amount; i++) {
            if (shouldConsumeDurability(unbreakingLevel)) {
                actualDamage++;
            }
        }

        if (actualDamage > 0) {
            damageable.setDamage(damageable.getDamage() + actualDamage);
            item.setItemMeta(meta);
            return true;
        }

        return false;
    }

    /**
     * Safely damages an item, stopping before it breaks.
     * 
     * @param item   The item to damage
     * @param amount The base damage amount
     * @return The actual damage applied (may be less if stopped to prevent
     *         breaking)
     */
    public static int safeDamageItem(ItemStack item, int amount) {
        if (item == null || isUnbreakable(item))
            return 0;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable))
            return 0;

        int maxDurability = item.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        int remaining = maxDurability - currentDamage - 1; // Leave 1 durability

        int unbreakingLevel = getUnbreakingLevel(item);
        int actualDamage = 0;

        for (int i = 0; i < amount && actualDamage < remaining; i++) {
            if (shouldConsumeDurability(unbreakingLevel)) {
                actualDamage++;
            }
        }

        if (actualDamage > 0) {
            damageable.setDamage(currentDamage + actualDamage);
            item.setItemMeta(meta);
        }

        return actualDamage;
    }
}
