package com.nyarutoru.nekoplugin.features.itemstack.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Represents a stacked item entity with unlimited capacity.
 */
public class StackedItemEntity {

    private final UUID id;
    private Location location;
    private final Material itemType;
    private int stackSize;
    private final ItemStack itemTemplate;
    private Item entityReference;

    public StackedItemEntity(UUID id, Location location, ItemStack itemTemplate, int stackSize) {
        this.id = id;
        this.location = location;
        this.itemType = itemTemplate.getType();
        this.stackSize = stackSize;
        this.itemTemplate = itemTemplate.clone();
        this.itemTemplate.setAmount(1); // Template is always 1 item
    }

    /**
     * Check if this stack can merge with the given ItemStack.
     */
    public boolean canMergeWith(ItemStack item) {
        if (item == null || item.getType() != itemType) {
            return false;
        }

        // Check if item is special (should not merge special items)
        if (isSpecialItem(item)) {
            return false;
        }

        // Exclude damageable items (tools, armor) - they shouldn't stack
        if (item.getType().getMaxDurability() > 0) {
            return false;
        }

        // Check if template matches
        return itemTemplate.isSimilar(item);
    }

    /**
     * Check if an item is special (enchanted, renamed, has PDC, has lore).
     */
    private boolean isSpecialItem(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // Exclude enchanted items
        if (!meta.getEnchants().isEmpty()) {
            return true;
        }

        // Exclude renamed items
        if (meta.hasDisplayName()) {
            return true;
        }

        // Exclude items with PDC
        if (!meta.getPersistentDataContainer().isEmpty()) {
            return true;
        }

        // Exclude items with lore
        if (meta.hasLore()) {
            return true;
        }

        return false;
    }

    /**
     * Merge the given amount into this stack.
     */
    public void merge(int amount) {
        this.stackSize += amount;
    }

    /**
     * Take the specified amount from this stack.
     * Returns the actual amount taken (may be less if stack is smaller).
     */
    public int take(int amount) {
        int actualAmount = Math.min(amount, stackSize);
        this.stackSize -= actualAmount;
        return actualAmount;
    }

    /**
     * Get the remaining stack size.
     */
    public int getStackSize() {
        return stackSize;
    }

    /**
     * Check if this stack is empty.
     */
    public boolean isEmpty() {
        return stackSize <= 0;
    }

    /**
     * Get an ItemStack with the specified amount.
     */
    public ItemStack getItemStack(int amount) {
        ItemStack result = itemTemplate.clone();
        result.setAmount(amount);
        return result;
    }

    /**
     * Update the Bukkit entity reference.
     */
    public void setEntityReference(Item entity) {
        this.entityReference = entity;
        if (entity != null) {
            this.location = entity.getLocation();
        }
    }

    /**
     * Update the entity's ItemStack to reflect current state.
     */
    public void updateEntity() {
        if (entityReference != null && !entityReference.isDead()) {
            // Keep entity as single item visually
            ItemStack displayStack = itemTemplate.clone();
            displayStack.setAmount(1);
            entityReference.setItemStack(displayStack);
        }
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public Material getItemType() {
        return itemType;
    }

    public ItemStack getItemTemplate() {
        return itemTemplate.clone();
    }

    public Item getEntityReference() {
        return entityReference;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }
}
