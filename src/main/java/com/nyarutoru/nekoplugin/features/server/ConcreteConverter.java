package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Collection;

/**
 * Converts Concrete Powder dropped in water into solid Concrete after 10
 * seconds.
 */
public class ConcreteConverter {

    private static final long CONVERT_TIME_MS = 10 * 1000; // 10 seconds
    // Note: No BukkitTask reference needed for Folia-compatible scheduling
    private static final long CHECK_INTERVAL_TICKS = 20; // 1 second
    // Map concrete powder to solid concrete
    private static final Map<Material, Material> POWDER_TO_CONCRETE = Map.ofEntries(
            Map.entry(Material.WHITE_CONCRETE_POWDER, Material.WHITE_CONCRETE),
            Map.entry(Material.ORANGE_CONCRETE_POWDER, Material.ORANGE_CONCRETE),
            Map.entry(Material.MAGENTA_CONCRETE_POWDER, Material.MAGENTA_CONCRETE),
            Map.entry(Material.LIGHT_BLUE_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE),
            Map.entry(Material.YELLOW_CONCRETE_POWDER, Material.YELLOW_CONCRETE),
            Map.entry(Material.LIME_CONCRETE_POWDER, Material.LIME_CONCRETE),
            Map.entry(Material.PINK_CONCRETE_POWDER, Material.PINK_CONCRETE),
            Map.entry(Material.GRAY_CONCRETE_POWDER, Material.GRAY_CONCRETE),
            Map.entry(Material.LIGHT_GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE),
            Map.entry(Material.CYAN_CONCRETE_POWDER, Material.CYAN_CONCRETE),
            Map.entry(Material.PURPLE_CONCRETE_POWDER, Material.PURPLE_CONCRETE),
            Map.entry(Material.BLUE_CONCRETE_POWDER, Material.BLUE_CONCRETE),
            Map.entry(Material.BROWN_CONCRETE_POWDER, Material.BROWN_CONCRETE),
            Map.entry(Material.GREEN_CONCRETE_POWDER, Material.GREEN_CONCRETE),
            Map.entry(Material.RED_CONCRETE_POWDER, Material.RED_CONCRETE),
            Map.entry(Material.BLACK_CONCRETE_POWDER, Material.BLACK_CONCRETE));
    // Track items in water: Item UUID -> time entered water
    private final Map<UUID, Long> itemsInWater = new HashMap<>();

    public void start() {
        // Schedule timer to check all worlds for items
        SchedulerUtils.runGlobalTimer(this::checkAllWorlds, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        // Task cleanup is handled by SchedulerUtils
        itemsInWater.clear();
    }

    /**
     * Called on global region - schedules individual entity checks
     */
    private void checkAllWorlds() {
        long now = System.currentTimeMillis();

        // Clean up dead items first (safe to do on global thread)
        itemsInWater.keySet().removeIf(uuid -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEntity(uuid) != null)
                    return false;
            }
            return true;
        });

        // For each world, schedule entity checks on their respective regions
        for (World world : Bukkit.getWorlds()) {
            // Get all items in the world (this is safe on any thread)
            Collection<Item> items = world.getEntitiesByClass(Item.class);

            // Schedule a check for each item on its own region
            for (Item item : items) {
                SchedulerUtils.runAtEntity(item, () -> checkItem(item, now));
            }
        }
    }

    /**
     * Check a single item - must be called on the item's owning thread
     */
    private void checkItem(Item item, long now) {
        // Verify item is still valid
        if (item.isDead() || !item.isValid()) {
            itemsInWater.remove(item.getUniqueId());
            return;
        }

        ItemStack stack = item.getItemStack();
        Material type = stack.getType();

        // Check if it's concrete powder
        if (!POWDER_TO_CONCRETE.containsKey(type)) {
            itemsInWater.remove(item.getUniqueId());
            return;
        }

        // Check if in water
        Material blockType = item.getLocation().getBlock().getType();
        boolean inWater = blockType == Material.WATER;

        if (inWater) {
            // Track when it entered water
            if (!itemsInWater.containsKey(item.getUniqueId())) {
                itemsInWater.put(item.getUniqueId(), now);
            } else {
                // Check if 10 seconds have passed
                long enteredTime = itemsInWater.get(item.getUniqueId());
                if (now - enteredTime >= CONVERT_TIME_MS) {
                    // Convert to solid concrete
                    Material concrete = POWDER_TO_CONCRETE.get(type);
                    int amount = stack.getAmount();

                    item.setItemStack(new ItemStack(concrete, amount));
                    itemsInWater.remove(item.getUniqueId());
                }
            }
        } else {
            // Not in water anymore, reset timer
            itemsInWater.remove(item.getUniqueId());
        }
    }
}
