package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import com.nyarutoru.nekoplugin.features.drawer.gui.DrawerGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles drawer block placement, interaction, breaking, and hopper
 * integration.
 */
public class DrawerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        DrawerTier tier = DrawerRecipes.getTierFromItem(item);
        if (tier == null)
            return;

        Location location = event.getBlock().getLocation();
        Drawer drawer = DrawerManager.getInstance().createDrawer(location, tier);

        if (drawer != null) {
            Material storedType = DrawerRecipes.getStoredItemType(item);
            int storedCount = DrawerRecipes.getStoredItemCount(item);

            if (storedType != null && storedCount > 0) {
                drawer.addItems(storedType, storedCount);
                DrawerManager.getInstance().markDirty();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getClickedBlock() == null)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getPlayer().isSneaking())
            return;

        Block block = event.getClickedBlock();
        Location location = block.getLocation();

        Drawer drawer = DrawerManager.getInstance().getDrawer(location);
        if (drawer == null)
            return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        DrawerGUI gui = new DrawerGUI(drawer);
        gui.open(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        Drawer drawer = DrawerManager.getInstance().getDrawer(location);
        if (drawer == null)
            return;

        event.setDropItems(false);
        DrawerManager.getInstance().removeDrawer(location);

        ItemStack drawerItem;
        if (!drawer.isEmpty()) {
            drawerItem = DrawerRecipes.createDrawerItemWithContents(
                    drawer.getTier(),
                    drawer.getItemType(),
                    drawer.getItemCount());
        } else {
            drawerItem = DrawerRecipes.createDrawerItem(drawer.getTier());
        }

        location.getWorld().dropItemNaturally(location, drawerItem);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        Inventory destination = event.getDestination();
        ItemStack item = event.getItem();

        // Hopper pushing INTO drawer
        if (destination.getLocation() != null) {
            Drawer drawer = DrawerManager.getInstance().getDrawer(destination.getLocation());
            if (drawer != null) {
                if (!Drawer.isAllowedItem(item) || !drawer.canAcceptItem(item)) {
                    event.setCancelled(true);
                    return;
                }

                int overflow = drawer.addItems(item.getType(), item.getAmount());
                if (overflow == item.getAmount()) {
                    event.setCancelled(true);
                } else if (overflow > 0) {
                    event.setCancelled(true);
                } else {
                    DrawerManager.getInstance().markDirty();
                    DrawerGUI.refreshAllViewers(drawer);
                }
                return;
            }
        }

        // Hopper pulling FROM drawer
        if (source.getLocation() != null) {
            Drawer drawer = DrawerManager.getInstance().getDrawer(source.getLocation());
            if (drawer != null) {
                event.setCancelled(true);

                if (drawer.isEmpty())
                    return;

                Material itemType = drawer.getItemType();
                ItemStack extracted = new ItemStack(itemType, 1);
                var leftover = destination.addItem(extracted);

                if (leftover.isEmpty()) {
                    drawer.removeItems(1);
                    DrawerManager.getInstance().markDirty();
                    DrawerGUI.refreshAllViewers(drawer);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        // Handles hoppers picking up dropped items - not needed for drawer interaction
    }
}
