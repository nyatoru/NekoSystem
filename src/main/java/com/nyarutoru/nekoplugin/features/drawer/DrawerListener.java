package com.nyarutoru.nekoplugin.features.drawer;

import com.nyarutoru.nekoplugin.features.drawer.crafting.DrawerRecipes;
import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import com.nyarutoru.nekoplugin.features.drawer.gui.DrawerGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles drawer block placement, interaction, breaking, and hopper
 * integration.
 */
public class DrawerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) {
            return;
        }

        DrawerTier tier = DrawerRecipes.getTierFromItem(item);
        if (tier == null)
            return;

        Block block = event.getBlock();
        if (block == null) {
            return;
        }

        // Validate that the placed block is actually a barrel
        if (block.getType() != Material.BARREL) {
            return;
        }

        Location location = block.getLocation();
        Drawer drawer = DrawerManager.getInstance().createDrawer(location, tier);

        if (drawer != null) {
            Material storedType = DrawerRecipes.getStoredItemType(item);
            int storedCount = DrawerRecipes.getStoredItemCount(item);

            if (storedType != null && storedCount > 0) {
                drawer.addItems(storedType, storedCount);
                DrawerManager.getInstance().markDirty();
                DrawerGUI.updateBarrelInventory(drawer);
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

        // Close all GUIs viewing this drawer before removing it
        DrawerGUI.closeAllViewers(drawer);

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
                DrawerGUI.updateBarrelInventory(drawer);
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

                // The event.getItem() is the item the hopper is trying to pull from the barrel
                // We intercept this and remove from our drawer data instead
                Material itemType = drawer.getItemType();

                // Try to add the drawer's item to destination
                ItemStack extracted = new ItemStack(itemType, 1);
                var leftover = destination.addItem(extracted);

                if (leftover.isEmpty()) {
                    // Successfully added to destination, remove from drawer
                    drawer.removeItems(1);
                    DrawerManager.getInstance().markDirty();
                    DrawerGUI.refreshAllViewers(drawer);

                    // Ensure the barrel inventory is updated with a representative item
                    DrawerGUI.updateBarrelInventory(drawer);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        // Handles hoppers picking up dropped items - not needed for drawer interaction
    }

    /**
     * Cleans up GUI references when a player disconnects.
     * Prevents memory leaks from lingering GUI references.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        DrawerGUI.cleanupPlayer(event.getPlayer());
    }

    // ==================== PISTON HANDLING ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        List<Block> movedBlocks = event.getBlocks();

        if (movedBlocks.isEmpty()) {
            return;
        }

        // Process in reverse order to avoid overwriting
        List<Drawer> drawersToMove = new ArrayList<>();
        List<Location> newLocations = new ArrayList<>();

        for (Block block : movedBlocks) {
            Drawer drawer = DrawerManager.getInstance().getDrawer(block.getLocation());
            if (drawer != null) {
                drawersToMove.add(drawer);
                newLocations.add(block.getLocation().add(
                        direction.getModX(),
                        direction.getModY(),
                        direction.getModZ()));
            }
        }

        if (drawersToMove.isEmpty()) {
            return;
        }

        // Move drawers to new positions
        for (int i = 0; i < drawersToMove.size(); i++) {
            Drawer drawer = drawersToMove.get(i);
            Location oldLocation = drawer.getLocation();
            Location newLocation = newLocations.get(i);

            if (oldLocation == null || newLocation == null) {
                continue;
            }

            // Close viewers before moving
            DrawerGUI.closeAllViewers(drawer);

            // Remove from old location and create at new location
            DrawerManager.getInstance().removeDrawer(oldLocation);
            Drawer newDrawer = DrawerManager.getInstance().createDrawer(newLocation, drawer.getTier());
            if (newDrawer != null && drawer.getItemType() != null) {
                newDrawer.addItems(drawer.getItemType(), drawer.getItemCount());
            }
            
            // Mark dirty immediately after each move to prevent data loss on crash
            DrawerManager.getInstance().markDirty();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!event.isSticky())
            return;

        BlockFace direction = event.getDirection();
        List<Block> movedBlocks = event.getBlocks();

        if (movedBlocks.isEmpty()) {
            return;
        }

        List<Drawer> drawersToMove = new ArrayList<>();
        List<Location> newLocations = new ArrayList<>();

        for (Block block : movedBlocks) {
            Drawer drawer = DrawerManager.getInstance().getDrawer(block.getLocation());
            if (drawer != null) {
                drawersToMove.add(drawer);
                newLocations.add(block.getLocation().add(
                        direction.getModX(),
                        direction.getModY(),
                        direction.getModZ()));
            }
        }

        if (drawersToMove.isEmpty()) {
            return;
        }

        // Move drawers to new positions
        for (int i = 0; i < drawersToMove.size(); i++) {
            Drawer drawer = drawersToMove.get(i);
            Location oldLocation = drawer.getLocation();
            Location newLocation = newLocations.get(i);

            if (oldLocation == null || newLocation == null) {
                continue;
            }

            // Close viewers before moving
            DrawerGUI.closeAllViewers(drawer);

            // Remove from old location and create at new location
            DrawerManager.getInstance().removeDrawer(oldLocation);
            Drawer newDrawer = DrawerManager.getInstance().createDrawer(newLocation, drawer.getTier());
            if (newDrawer != null && drawer.getItemType() != null) {
                newDrawer.addItems(drawer.getItemType(), drawer.getItemCount());
            }
            
            // Mark dirty immediately after each move to prevent data loss on crash
            DrawerManager.getInstance().markDirty();
        }
    }

    // ==================== EXPLOSION HANDLING ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> affectedBlocks) {
        List<Block> drawersToDestroy = new ArrayList<>();

        for (Block block : affectedBlocks) {
            Drawer drawer = DrawerManager.getInstance().getDrawer(block.getLocation());
            if (drawer != null) {
                drawersToDestroy.add(block);

                // Close all GUIs viewing this drawer
                DrawerGUI.closeAllViewers(drawer);

                // Drop the drawer item with contents
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                ItemStack drawerItem;
                if (!drawer.isEmpty()) {
                    drawerItem = DrawerRecipes.createDrawerItemWithContents(
                            drawer.getTier(),
                            drawer.getItemType(),
                            drawer.getItemCount());
                } else {
                    drawerItem = DrawerRecipes.createDrawerItem(drawer.getTier());
                }
                block.getWorld().dropItemNaturally(dropLoc, drawerItem);

                // Remove drawer data
                DrawerManager.getInstance().removeDrawer(block.getLocation());
            }
        }

        // Remove drawers from explosion list and manually destroy them
        // This prevents vanilla barrel drop while still destroying the block
        affectedBlocks.removeAll(drawersToDestroy);
        for (Block block : drawersToDestroy) {
            block.setType(Material.AIR);
        }
    }
}
