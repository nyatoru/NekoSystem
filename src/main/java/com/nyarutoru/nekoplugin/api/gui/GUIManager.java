package com.nyarutoru.nekoplugin.api.gui;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all open GUIs and handles their events.
 */
public class GUIManager implements Listener {

    private static GUIManager instance;
    private final Map<Player, BaseGUI> openGUIs = new HashMap<>();

    private GUIManager() {
    }

    public static GUIManager getInstance() {
        if (instance == null) {
            instance = new GUIManager();
        }
        return instance;
    }

    /**
     * Initialize the GUI manager and register events.
     */
    public void initialize(NekoPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Registers an open GUI for a player.
     */
    public void registerGUI(Player player, BaseGUI gui) {
        openGUIs.put(player, gui);
    }

    /**
     * Gets the GUI open for a player.
     */
    public BaseGUI getGUI(Player player) {
        return openGUIs.get(player);
    }

    /**
     * Closes and unregisters a player's GUI.
     */
    public void closeGUI(Player player) {
        openGUIs.remove(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        BaseGUI gui = openGUIs.get(player);
        if (gui == null)
            return;

        if (event.getClickedInventory() == gui.getInventory()) {
            event.setCancelled(true);
            gui.onClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        BaseGUI gui = openGUIs.remove(player);
        if (gui != null) {
            gui.onClose(player);
        }
    }
}
