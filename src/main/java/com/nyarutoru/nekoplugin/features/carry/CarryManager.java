package com.nyarutoru.nekoplugin.features.carry;

import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.utils.ComponentUtils;
import io.papermc.paper.block.TileStateInventoryHolder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
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
            applyPlayerFacing(placed, player);
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
            Location dest = location.clone().add(0.5, 0.0, 0.5);
            dest.setYaw(player.getLocation().getYaw());
            dest.setPitch(0f);
            entity.teleport(dest);
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

    private static void applyPlayerFacing(BlockState state, Player player) {
        BlockData data = state.getBlockData();
        if (data instanceof Directional directional) {
            // ponytail: preserve old block state but rotate directional face to follow player (mimic vanilla placement)
            BlockFace playerFacing = yawToHorizontal(player.getLocation().getYaw());
            BlockFace target = playerFacing.getOppositeFace();
            if (directional.getFaces().contains(target)) {
                directional.setFacing(target);
            } else if (directional.getFaces().contains(playerFacing)) {
                directional.setFacing(playerFacing);
            } else return;
            state.setBlockData(directional);
        } else if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(yawToRotation(player.getLocation().getYaw()));
            state.setBlockData(rotatable);
        }
    }

    private static BlockFace yawToHorizontal(float yaw) {
        yaw %= 360f;
        if (yaw < 0) yaw += 360f;
        if (yaw < 45f || yaw >= 315f) return BlockFace.SOUTH;
        if (yaw < 135f) return BlockFace.WEST;
        if (yaw < 225f) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private static BlockFace yawToRotation(float yaw) {
        yaw %= 360f;
        if (yaw < 0) yaw += 360f;
        if (yaw < 22.5f || yaw >= 337.5f) return BlockFace.SOUTH;
        if (yaw < 67.5f) return BlockFace.SOUTH_WEST;
        if (yaw < 112.5f) return BlockFace.WEST;
        if (yaw < 157.5f) return BlockFace.NORTH_WEST;
        if (yaw < 202.5f) return BlockFace.NORTH;
        if (yaw < 247.5f) return BlockFace.NORTH_EAST;
        if (yaw < 292.5f) return BlockFace.EAST;
        return BlockFace.SOUTH_EAST;
    }
}
