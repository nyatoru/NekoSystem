package com.nyarutoru.nekoplugin.features.petcarry;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

public class PetCarryListener implements Listener {

    /**
     * Handles picking up entities (Animals or Villagers) with Shift + Right Click.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return; // Only process main hand interactions
        Player player = event.getPlayer();

        // Must be sneaking to pick up
        if (!player.isSneaking())
            return;

        Entity clickedEntity = event.getRightClicked();

        // Allow carrying Animals only (villagers temporarily disabled)
        if (clickedEntity instanceof Animals) {
            // Check if player is already carrying something
            if (!player.getPassengers().isEmpty())
                return;

            // Pick up the entity
            event.setCancelled(true); // Prevent other interactions (like trading or breeding)
            player.addPassenger(clickedEntity);
        }
    }

    /**
     * Handles placing down carried entities with Right Click on a block.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        List<Entity> passengers = player.getPassengers();

        // If not carrying anything, do nothing
        if (passengers.isEmpty())
            return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null)
            return;

        // Place all passengers (usually just one)
        // We iterate and copy to avoid concurrent modification if removal happens
        // instantly,
        // though removePassenger is safe.
        // Important: Use a copy or iterator safely if we were doing complex logic,
        // but for a simple loop it is usually fine.
        // To be safe against list modification issues:
        for (Entity passenger : List.copyOf(passengers)) {
            player.removePassenger(passenger);

            // Teleport entity to the top of the clicked block
            Location loc = clickedBlock.getLocation().add(0.5, 1.0, 0.5);

            // Set rotation to match player's rotation (optional, but looks better)
            loc.setYaw(player.getLocation().getYaw());

            passenger.teleportAsync(loc);
        }

        // Prevent placing blocks or using items when placing an entity
        event.setCancelled(true);
    }

    /**
     * Ejects passengers before portal teleportation to prevent Folia async errors.
     * Portal teleportation tries to asynchronously create new entities for
     * passengers,
     * which fails for complex entities like villagers with brain logic.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalEntry(org.bukkit.event.player.PlayerPortalEvent event) {
        Player player = event.getPlayer();
        List<Entity> passengers = player.getPassengers();

        if (passengers.isEmpty())
            return;

        // Eject all passengers before portal teleportation
        for (Entity passenger : List.copyOf(passengers)) {
            player.removePassenger(passenger);

            // Place passenger at player's current location
            // Use async teleport to avoid threading issues
            Location loc = player.getLocation().clone();
            passenger.teleportAsync(loc);
        }
    }

    /**
     * Prevent passengers from entering portals on their own.
     * This prevents the async entity creation error.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        Entity entity = event.getEntity();

        // If entity is a passenger, prevent portal travel
        if (entity.isInsideVehicle()) {
            event.setCancelled(true);
        }
    }
}
