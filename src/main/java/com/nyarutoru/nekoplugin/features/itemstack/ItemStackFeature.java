package com.nyarutoru.nekoplugin.features.itemstack;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.Feature;
import com.nyarutoru.nekoplugin.features.itemstack.data.ItemStackDatabase;
import com.nyarutoru.nekoplugin.features.itemstack.data.StackedItemEntity;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.HandlerList;

import java.util.Map;
import java.util.UUID;

/**
 * Item Stack Feature - unlimited item stacking with visual display.
 */
public class ItemStackFeature implements Feature {

    public static final String ID = "item_stack";
    public static final String NAME = "Item Stack";

    private static final long AUTO_SAVE_INTERVAL_TICKS = 6000L; // 5 minutes

    private boolean enabled = false;
    private ItemStackDatabase database;
    private ItemDisplayManager displayManager;
    private ItemMergeListener mergeListener;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        // Initialize database
        ItemStackDatabase.initialize(plugin);
        database = ItemStackDatabase.getInstance();

        // Load existing stacks
        Map<UUID, StackedItemEntity> stacks = database.loadAll();

        // Spawn entities for loaded stacks
        SchedulerUtils.runGlobalLater(() -> respawnStackEntities(stacks), 20L); // 1 second delay

        // Initialize display manager
        displayManager = new ItemDisplayManager();
        displayManager.start();

        // Create displays for loaded stacks
        for (StackedItemEntity stack : stacks.values()) {
            displayManager.createDisplay(stack);
        }

        // Register listener
        mergeListener = new ItemMergeListener(database, displayManager);
        plugin.getServer().getPluginManager().registerEvents(mergeListener, plugin);

        // Start auto-save task
        SchedulerUtils.runAsyncTimer(database::saveAll, AUTO_SAVE_INTERVAL_TICKS, AUTO_SAVE_INTERVAL_TICKS);

        this.enabled = true;
    }

    @Override
    public void onDisable() {
        if (mergeListener != null) {
            HandlerList.unregisterAll(mergeListener);
        }

        if (displayManager != null) {
            displayManager.shutdown();
        }

        if (database != null) {
            database.shutdown();
        }

        this.enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Respawn item entities for loaded stacks.
     */
    private void respawnStackEntities(Map<UUID, StackedItemEntity> stacks) {
        for (StackedItemEntity stack : stacks.values()) {
            World world = stack.getLocation().getWorld();
            if (world == null) {
                continue;
            }

            // Spawn item entity
            Item item = world.dropItem(stack.getLocation(), stack.getItemTemplate());
            item.setPickupDelay(20); // Small delay to prevent immediate pickup
            stack.setEntityReference(item);
            stack.updateEntity();
        }
    }
}
