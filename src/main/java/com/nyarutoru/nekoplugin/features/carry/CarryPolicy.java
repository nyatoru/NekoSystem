package com.nyarutoru.nekoplugin.features.carry;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.type.BrewingStand;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CarryPolicy {
    private CarryPolicy() {
    }

    static boolean hasEmptyMainHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item.getType().isAir();
    }

    static boolean isCarryableMob(Entity entity) {
        return isCarryableMob(entity, true, true);
    }

    static boolean isCarryableMob(Entity entity, boolean animals, boolean villagers) {
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) return false;
        if (!entity.isValid() || entity.isDead() || entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) return false;
        if (living.isLeashed()) return false;
        return (animals && entity instanceof Animals) || (villagers && entity instanceof AbstractVillager);
    }

    static boolean isCarryableBlock(BlockState state) {
        return isCarryableBlock(state, true, true, true);
    }

    static boolean isCarryableBlock(BlockState state, boolean containers, boolean lecterns, boolean workstations) {
        Material material = state.getType();
        if (!material.isBlock() || material.isAir()) return false;
        return isCarryableBlockCategory(
            containers && (state instanceof Container || state instanceof ChiseledBookshelf),
            lecterns && state instanceof Lectern,
            workstations && (material == Material.CRAFTING_TABLE
                || state.getBlockData() instanceof Furnace
                || state.getBlockData() instanceof BrewingStand
                || state.getBlockData() instanceof Crafter)
        );
    }

    static boolean isCarryableBlockCategory(boolean container, boolean lectern, boolean workstation) {
        return container || lectern || workstation;
    }
}
