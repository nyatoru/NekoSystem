package com.nyarutoru.nekoplugin.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String processedMessage = "<green><bold>+</bold> <gray>" + event.getPlayer().getName() + " joined the server.";
        Component message = miniMessage.deserialize(processedMessage);
        event.joinMessage(message);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String processedMessage = "<red><bold>-</bold> <gray>" + event.getPlayer().getName() + " left the server.";
        Component message = miniMessage.deserialize(processedMessage);
        event.quitMessage(message);
    }
}