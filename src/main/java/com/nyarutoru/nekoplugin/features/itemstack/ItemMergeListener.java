package com.nyarutoru.nekoplugin.features.itemstack;

import com.nyarutoru.nekoplugin.features.itemstack.data.ItemStackDatabase;
import com.nyarutoru.nekoplugin.features.itemstack.data.StackedItemEntity;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Handles item merging logic and pickup events.
 */
public class ItemMergeListener implements Listener {

    private static final double MERGE_RADIUS = 2.5;
    private final ItemStackDatabase database;
    private final ItemDisplayManager displayManager;

    public ItemMergeListener(ItemStackDatabase database, ItemDisplayManager displayManager) {
        this.database = database;
        this.displayManager = displayManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();

        // Schedule merge check slightly delayed to allow item to settle
        SchedulerUtils.runAtLocationLater(item.getLocation(), () -> {
            if (item.isDead() || !item.isValid()) {
                return;
            }

            // Check if item should be excluded from stacking
            if (isSpecialItem(itemStack)) {
                return;
            }

            // Find nearby stacks
            List<StackedItemEntity> nearbyStacks = database.findNearby(item.getLocation(), MERGE_RADIUS);

            // Try to merge with existing stack
            for (StackedItemEntity stack : nearbyStacks) {
                if (stack.canMergeWith(itemStack)) {
                    // Merge into existing stack
                    stack.merge(itemStack.getAmount());
                    database.saveStack(stack);
                    displayManager.updateDisplay(stack);

                    // Remove the item entity
                    item.remove();
                    return;
                }
            }

            // No existing stack found, create new one
            StackedItemEntity newStack = new StackedItemEntity(
                    UUID.randomUUID(),
                    item.getLocation(),
                    itemStack,
                    itemStack.getAmount());
            newStack.setEntityReference(item);
            newStack.updateEntity(); // Set to 1 item visually

            database.saveStack(newStack);
            displayManager.createDisplay(newStack);
        }, 2L); // 2 ticks delay
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item item = event.getItem();
        StackedItemEntity stack = getStackByEntity(item);

        if (stack == null) {
            return; // Not a stacked item
        }

        event.setCancelled(true); // Cancel default pickup

        ItemStack itemStack = stack.getItemTemplate();
        int availableSpace = getInventorySpace(player, itemStack);

        if (availableSpace <= 0) {
            return; // No space
        }

        int amountToTake = Math.min(stack.getStackSize(), availableSpace);
        stack.take(amountToTake);

        // Give items to player
        giveItemsToPlayer(player, itemStack, amountToTake);

        if (stack.isEmpty()) {
            // Remove stack completely
            item.remove();
            displayManager.removeDisplay(stack.getId());
            database.removeStack(stack.getId());
        } else {
            // Update stack
            database.saveStack(stack);
            displayManager.updateDisplay(stack);
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        StackedItemEntity stack = getStackByEntity(item);

        if (stack != null) {
            // Remove from database
            displayManager.removeDisplay(stack.getId());
            database.removeStack(stack.getId());
        }
    }

    /**
     * Get stacked entity by Bukkit Item entity.
     */
    private StackedItemEntity getStackByEntity(Item item) {
        for (StackedItemEntity stack : database.getCache().values()) {
            if (stack.getEntityReference() != null &&
                    stack.getEntityReference().getUniqueId().equals(item.getUniqueId())) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Check if item is special (should not be stacked).
     */
    private boolean isSpecialItem(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }

        var meta = item.getItemMeta();

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
     * Calculate available inventory space for the given item type.
     */
    private int getInventorySpace(Player player, ItemStack item) {
        int space = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (ItemStack slot : contents) {
            if (slot == null || slot.getType().isAir()) {
                space += item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                space += (item.getMaxStackSize() - slot.getAmount());
            }
        }

        return space;
    }

    /**
     * Give items to player, splitting into max stack sizes.
     */
    private void giveItemsToPlayer(Player player, ItemStack template, int amount) {
        int maxStackSize = template.getMaxStackSize();

        while (amount > 0) {
            int giveAmount = Math.min(amount, maxStackSize);
            ItemStack toGive = template.clone();
            toGive.setAmount(giveAmount);

            player.getInventory().addItem(toGive);
            amount -= giveAmount;
        }
    }
}
