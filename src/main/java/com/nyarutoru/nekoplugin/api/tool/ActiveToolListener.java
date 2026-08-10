package com.nyarutoru.nekoplugin.api.tool;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Global listener for ActiveToolAPI cancellation events.
 * Handles: item switch, death, teleport, quit.
 */
public class ActiveToolListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        String active = ActiveToolAPI.getInstance().getActiveToolName(player);
        // ponytail: Magnet supports offhand — keep active if magnet still held in either hand after switch.
        // Future API: ActiveToolAPI should store per-tool predicate and check it here generically.
        if ("Magnet".equals(active)) {
            // event.getNewSlot() is the slot that will be held; inventory may not have updated yet on some versions,
            // so check both the future slot and current hands.
            org.bukkit.inventory.ItemStack newMain = null;
            try {
                newMain = player.getInventory().getItem(event.getNewSlot());
            } catch (Throwable ignored) {}
            org.bukkit.inventory.ItemStack off = null;
            try {
                off = player.getInventory().getItemInOffHand();
            } catch (Throwable ignored) {}
            org.bukkit.inventory.ItemStack curMain = null;
            try {
                curMain = player.getInventory().getItemInMainHand();
            } catch (Throwable ignored) {}
            boolean stillHolding = false;
            try {
                Class<?> magnetCls = Class.forName("com.nyarutoru.nekoplugin.features.magnet.MagnetListener");
                java.lang.reflect.Method isMagnet = magnetCls.getMethod("isMagnetItem", org.bukkit.inventory.ItemStack.class);
                stillHolding = Boolean.TRUE.equals(isMagnet.invoke(null, newMain))
                        || Boolean.TRUE.equals(isMagnet.invoke(null, off))
                        || Boolean.TRUE.equals(isMagnet.invoke(null, curMain));
            } catch (Throwable ignored) {
                // fallback: check compass types directly if MagnetListener not present
                stillHolding = isCompassFallback(newMain) || isCompassFallback(off) || isCompassFallback(curMain);
            }
            if (stillHolding) return;
        }
        ActiveToolAPI.getInstance().onItemSwitch(player);
    }

    private static boolean isCompassFallback(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        org.bukkit.Material t = item.getType();
        return t == org.bukkit.Material.COMPASS || t == org.bukkit.Material.RECOVERY_COMPASS;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        ActiveToolAPI.getInstance().onDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getWorld() != event.getTo().getWorld() ||
                event.getFrom().distance(event.getTo()) > 10) {
            ActiveToolAPI.getInstance().onTeleport(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ActiveToolAPI.getInstance().onQuit(event.getPlayer());
    }
}
