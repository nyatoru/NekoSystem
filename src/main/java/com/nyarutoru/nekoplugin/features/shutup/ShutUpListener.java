package com.nyarutoru.nekoplugin.features.shutup;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles "Shut Up" furnace placement, breaking, and immediate silencing of newly spawned mobs.
 */
public class ShutUpListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) return;
        ItemStack item = event.getItemInHand();
        ShutUpType type = ShutUpItems.getTypeFromItem(item);
        if (type == null) return;

        Block block = event.getBlock();
        if (block.getType() != Material.FURNACE) return;
        if (!(block.getState() instanceof Furnace furnace)) return;

        furnace.getPersistentDataContainer().set(ShutUpItems.TYPE_KEY, PersistentDataType.STRING, type.name());
        furnace.customName(net.kyori.adventure.text.Component.text(type.displayName())
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        furnace.update();

        ShutUpManager.getInstance().track(furnace.getLocation(), type);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Furnace furnace
                && furnace.getPersistentDataContainer().get(ShutUpItems.TYPE_KEY, PersistentDataType.STRING) != null) {
            ShutUpManager.getInstance().untrack(furnace.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (ShutUpType.matchesAny(event.getEntity())) {
            ShutUpManager.getInstance().silenceIfInZone(event.getEntity());
        }
    }
}
