package com.nyarutoru.nekoplugin.api.gui;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Manages all open plugin GUIs and their events. */
public class GUIManager implements Listener {
    private static volatile GUIManager instance;
    private final Map<Player, BaseGUI> openGUIs = new ConcurrentHashMap<>();
    private final Map<Player, AnvilRegistration> openAnvils = new ConcurrentHashMap<>();

    private GUIManager() { }

    public static GUIManager getInstance() {
        if (instance == null) {
            synchronized (GUIManager.class) {
                if (instance == null) instance = new GUIManager();
            }
        }
        return instance;
    }

    public void initialize(NekoPlugin plugin) { plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    public void registerGUI(Player player, BaseGUI gui) { openAnvils.remove(player); openGUIs.put(player, gui); }
    public BaseGUI getGUI(Player player) { return openGUIs.get(player); }
    public void closeGUI(Player player) { openGUIs.remove(player); openAnvils.remove(player); }

    public void registerAnvil(Player player, AnvilTextInputGUI gui, AnvilView view) {
        openGUIs.remove(player);
        openAnvils.put(player, new AnvilRegistration(gui, view));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        AnvilRegistration registration = openAnvils.get(player);
        if (registration != null) {
            if (event.getView() == registration.view()
                    && event.getInventory() == registration.view().getTopInventory()) {
                event.setCancelled(true);
                registration.gui().click(player, registration.view(), event.getRawSlot());
            }
            return;
        }
        BaseGUI gui = event.getInventory().getHolder() instanceof BaseGUI holder ? holder : openGUIs.get(player);
        if (gui == null) return;
        if (gui instanceof PreviewGUI) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < gui.getInventory().getSize()) gui.onClick(event);
            return;
        }
        if (event.getClickedInventory() == gui.getInventory()) {
            event.setCancelled(true);
            gui.onClick(event);
        } else if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) gui.onClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        AnvilRegistration registration = openAnvils.get(player);
        if (registration != null) {
            if (event.getView() == registration.view()
                    && event.getInventory() == registration.view().getTopInventory()) event.setCancelled(true);
            return;
        }
        BaseGUI gui = event.getInventory().getHolder() instanceof BaseGUI holder ? holder : openGUIs.get(player);
        if (gui == null) return;
        if (gui instanceof PreviewGUI) { event.setCancelled(true); return; }
        for (int slot : event.getRawSlots()) if (slot < gui.getInventory().getSize()) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        AnvilRegistration registration = openAnvils.get(player);
        if (registration != null) {
            if (event.getView() == registration.view()
                    && openAnvils.remove(player, registration)) {
                registration.gui().closed(player, registration.view());
            }
            return;
        }
        BaseGUI gui = openGUIs.remove(player);
        if (gui != null) gui.onClose(player);
    }

    private record AnvilRegistration(AnvilTextInputGUI gui, AnvilView view) { }
}
