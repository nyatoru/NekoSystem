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
        ActiveToolAPI.getInstance().onItemSwitch(event.getPlayer());
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
