package com.nyarutoru.nekoplugin.features.carry;

import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.utils.ComponentUtils;
import io.papermc.paper.block.TileStateInventoryHolder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class CarryManager {
    private final Map<UUID, CarriedObject> carriedByPlayer = new HashMap<>();
    private boolean requireSneaking = true;
    private boolean animalsEnabled = true;
    private boolean villagersEnabled = true;
    private boolean containersEnabled = true;
    private boolean lecternsEnabled = true;
    private boolean workstationsEnabled = true;

    void configure(boolean requireSneaking, boolean animalsEnabled, boolean villagersEnabled,
                   boolean containersEnabled, boolean lecternsEnabled, boolean workstationsEnabled) {
        this.requireSneaking = requireSneaking;
        this.animalsEnabled = animalsEnabled;
        this.villagersEnabled = villagersEnabled;
        this.containersEnabled = containersEnabled;
        this.lecternsEnabled = lecternsEnabled;
        this.workstationsEnabled = workstationsEnabled;
    }

    boolean requireSneaking() {
        return requireSneaking;
    }

    boolean isCarrying(Player player) {
        return carriedByPlayer.containsKey(player.getUniqueId());
    }

    boolean pickupMob(Player player, Entity entity) {
        if (isCarrying(player) || !CarryPolicy.hasEmptyMainHand(player)
                || !CarryPolicy.isCarryableMob(entity, animalsEnabled, villagersEnabled)) return false;
        if (!player.addPassenger(entity)) return false;
        carriedByPlayer.put(player.getUniqueId(), new CarriedObject.Mob(entity));
        player.sendActionBar(ComponentUtils.success("Picked up " + entity.getType().key().value().replace('_', ' ')));
        return true;
    }

    boolean pickupBlock(Player player, Block block) {
        if (isCarrying(player) || !CarryPolicy.hasEmptyMainHand(player)) return false;
        if (DrawerManager.getInstance().isDrawer(block.getLocation())) return false;
        BlockState state = block.getState();
        if (!CarryPolicy.isCarryableBlock(state, containersEnabled, lecternsEnabled, workstationsEnabled)) return false;
        if (state instanceof org.bukkit.block.Chest chest
                && chest.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest) {
            player.sendActionBar(ComponentUtils.error("Double chests cannot be carried"));
            return true;
        }

        BlockDisplay display = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), BlockDisplay.class);
        display.setBlock(state.getBlockData());
        if (!player.addPassenger(display)) {
            display.remove();
            return false;
        }
        clearLiveInventory(state);
        block.setType(Material.AIR, false);
        carriedByPlayer.put(player.getUniqueId(), new CarriedObject.Block(state, display));
        player.sendActionBar(ComponentUtils.success("Picked up " + state.getType().key().value().replace('_', ' ')));
        return true;
    }

    boolean place(Player player, Block clicked, BlockFace face) {
        CarriedObject carried = carriedByPlayer.get(player.getUniqueId());
        if (carried == null) return false;
        Location location = clicked.getRelative(face).getLocation();
        Block destination = location.getBlock();
        if (carried instanceof CarriedObject.Block && !destination.isReplaceable()) {
            player.sendActionBar(ComponentUtils.error("There is no room to put that down"));
            return true;
        }

        if (carried instanceof CarriedObject.Block carriedBlock) {
            BlockState replaced = destination.getState();
            BlockPlaceEvent event = new BlockPlaceEvent(destination, replaced, clicked,
                new ItemStack(carriedBlock.state().getType()), player, true, EquipmentSlot.HAND);
            if (!event.callEvent() || !event.canBuild()) return true;
            BlockState placed = carriedBlock.state().copy(location);
            if (!placed.update(true, false)) return true;
            // ponytail: remove mapping before dismount so EntityDismount/EntityRemove handlers don't duplicate the block (bedrock damage / void case)
            carriedByPlayer.remove(player.getUniqueId());
            removePassenger(player, carriedBlock.passenger());
        } else {
            Entity entity = carried.passenger();
            if (entity.collidesAt(location.clone().add(0.5, 0.0, 0.5))) {
                player.sendActionBar(ComponentUtils.error("There is no room to put that down"));
                return true;
            }
            carriedByPlayer.remove(player.getUniqueId());
            removePassenger(player, entity);
            entity.teleport(location.clone().add(0.5, 0.0, 0.5));
        }

        player.sendActionBar(ComponentUtils.success("Put down carried object"));
        return true;
    }

    void release(Player player) {
        CarriedObject carried = carriedByPlayer.remove(player.getUniqueId());
        if (carried == null) return;
        releaseAt(player, carried, player.getLocation());
    }

    void releasePassenger(Entity passenger, Location location) {
        Map.Entry<UUID, CarriedObject> entry = carriedByPlayer.entrySet().stream()
            .filter(candidate -> candidate.getValue().passenger().getUniqueId().equals(passenger.getUniqueId()))
            .findFirst()
            .orElse(null);
        if (entry == null) return;
        carriedByPlayer.remove(entry.getKey());
        Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
        releaseAt(player, entry.getValue(), location);
    }

    void forget(Entity entity) {
        carriedByPlayer.entrySet().removeIf(entry -> entry.getValue().passenger().getUniqueId().equals(entity.getUniqueId()));
    }

    void shutdown() {
        for (UUID playerId : java.util.List.copyOf(carriedByPlayer.keySet())) {
            CarriedObject carried = carriedByPlayer.remove(playerId);
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            Location location = player != null ? player.getLocation() : carried.passenger().getLocation();
            releaseAt(player, carried, location);
        }
    }

    static void clearLiveInventory(BlockState state) {
        if (state instanceof TileStateInventoryHolder inventoryHolder) {
            inventoryHolder.getInventory().clear();
        }
    }

    private static void releaseAt(Player player, CarriedObject carried, Location location) {
        if (player != null) player.removePassenger(carried.passenger());
        else carried.passenger().leaveVehicle();
        if (carried instanceof CarriedObject.Block carriedBlock) restoreBlock(location, carriedBlock);
    }

    private static void removePassenger(Player player, Entity passenger) {
        player.removePassenger(passenger);
        if (passenger instanceof BlockDisplay) passenger.remove();
    }

    private static void restoreBlock(Location playerLocation, CarriedObject.Block carried) {
        Block destination = playerLocation.getBlock();
        if (!destination.isReplaceable()) destination = destination.getRelative(BlockFace.UP);
        carried.state().copy(destination.getLocation()).update(true, false);
        carried.passenger().remove();
    }
}
