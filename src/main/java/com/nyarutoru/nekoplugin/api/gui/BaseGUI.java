package com.nyarutoru.nekoplugin.api.gui;

import com.nyarutoru.nekoplugin.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base GUI class for creating custom inventory GUIs.
 * Extend this class to create feature-specific GUIs.
 */
public abstract class BaseGUI implements InventoryHolder {

    protected final int size;
    protected final Component title;
    protected final Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

    /**
     * Creates a new GUI with the specified size and title.
     *
     * @param size  The inventory size (must be multiple of 9, max 54)
     * @param title The inventory title
     */
    public BaseGUI(int size, Component title) {
        this.size = size;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /**
     * Creates a new GUI with a string title.
     */
    public BaseGUI(int size, String title) {
        this(size, Component.text(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the GUI for a player.
     */
    public void open(Player player) {
        GUIManager.getInstance().registerGUI(player, this);
        player.openInventory(inventory);
    }

    /**
     * Called when a slot is clicked. Override for custom behavior.
     */
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < size && clickHandlers.containsKey(slot)) {
            clickHandlers.get(slot).accept(event);
        }
    }

    /**
     * Called when the inventory is closed.
     */
    public void onClose(Player player) {
        // Override in subclass if needed
    }

    /**
     * Refreshes the GUI contents. Override in subclass.
     */
    public abstract void refresh();

    // ===== Helper Methods for Building GUI =====

    /**
     * Sets an item in a slot with a click handler.
     */
    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            clickHandlers.put(slot, handler);
        }
    }

    /**
     * Sets an item in a slot without a click handler.
     */
    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Fills the entire inventory with a filler item.
     */
    protected void fillBackground(Material material) {
        ItemStack filler = createItem(material, " ");
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Fills empty slots with a filler item.
     */
    protected void fillEmpty(Material material) {
        ItemStack filler = createItem(material, " ");
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Creates a simple item with a display name.
     */
    protected ItemStack createItem(Material material, String name) {
        return ItemUtils.createDisplayItem(material, name);
    }

    /**
     * Creates an item with a display name and lore.
     */
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        return ItemUtils.createDisplayItem(material, name, lore);
    }

    /**
     * Creates a close button.
     */
    protected ItemStack createCloseButton() {
        return ItemUtils.createCloseButton();
    }

    /**
     * Formats a material name for display (e.g., DIAMOND_SWORD -> Diamond Sword).
     */
    protected String formatMaterial(Material material) {
        return ItemUtils.formatMaterialName(material);
    }
}
