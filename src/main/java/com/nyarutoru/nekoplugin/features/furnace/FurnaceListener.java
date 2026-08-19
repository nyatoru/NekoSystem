package com.nyarutoru.nekoplugin.features.furnace;

import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles Upgrade Furnace placement and breaking.
 */
public class FurnaceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild())
            return;
        ItemStack item = event.getItemInHand();
        FurnaceTier tier = FurnaceRecipes.getTierFromItem(item);
        if (tier == null)
            return;

        Block block = event.getBlock();
        if (block.getType() != org.bukkit.Material.FURNACE)
            return;
        if (!(block.getState() instanceof Furnace furnace))
            return;

        furnace.getPersistentDataContainer().set(FurnaceRecipes.getTierKey(), PersistentDataType.STRING, tier.name());
        furnace.customName(tier.getDisplayNameComponent());
        furnace.update();

        FurnaceManager.getInstance().track(block.getLocation(), tier.getLevel());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Furnace furnace
                && furnace.getPersistentDataContainer().get(FurnaceRecipes.getTierKey(), PersistentDataType.STRING) != null) {
            FurnaceManager.getInstance().untrack(block.getLocation());
        }
    }
}
