package com.nyarutoru.nekoplugin.features.furnace;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Upgrade Furnace placement and breaking.
 */
public class FurnaceListener implements Listener {

    // Vanilla does not preserve block PDC on break, so the plain furnace drop
    // would lose its tier data (unusable for upgrades). Carry tier to the drop event.
    private final Map<Location, FurnaceTier> breakingTiers = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild())
            return;
        ItemStack item = event.getItemInHand();
        FurnaceTier tier = FurnaceRecipes.getTierFromItem(item);
        if (tier == null)
            return;

        Block block = event.getBlock();
        if (block.getType() != Material.FURNACE)
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
        if (block.getState() instanceof Furnace furnace) {
            String tierName = furnace.getPersistentDataContainer().get(FurnaceRecipes.getTierKey(), PersistentDataType.STRING);
            if (tierName == null)
                return;
            FurnaceManager.getInstance().untrack(block.getLocation());
            if (event.isDropItems()) {
                breakingTiers.put(block.getLocation(), FurnaceTier.getByName(tierName));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        FurnaceTier tier = breakingTiers.remove(event.getBlock().getLocation());
        if (tier == null)
            return;
        for (Item drop : event.getItems()) {
            if (drop.getItemStack().getType() == Material.FURNACE) {
                drop.setItemStack(FurnaceRecipes.createFurnaceItem(tier));
                return;
            }
        }
    }
}
