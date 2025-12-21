package com.nyarutoru.nekoplugin.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Preview GUI - a non-interactive GUI for displaying information.
 * All slots are blocked by default. Use addClickableSlot() to enable specific
 * slots.
 */
public class PreviewGUI extends BaseGUI {

    private Consumer<InventoryClickEvent> backButtonHandler;
    private int backButtonSlot = -1;

    /**
     * Creates a new Preview GUI with the specified size and title.
     *
     * @param size  The inventory size (must be multiple of 9, max 54)
     * @param title The inventory title
     */
    public PreviewGUI(int size, Component title) {
        super(size, title);
    }

    /**
     * Creates a new Preview GUI with a string title.
     */
    public PreviewGUI(int size, String title) {
        super(size, title);
    }

    /**
     * Sets a back button at the specified slot.
     *
     * @param slot    The slot to place the back button
     * @param handler The handler to execute when clicked
     */
    public void setBackButton(int slot, Consumer<InventoryClickEvent> handler) {
        this.backButtonSlot = slot;
        this.backButtonHandler = handler;

        ItemStack item = new ItemStack(Material.ARROW);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("← Back")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to go back")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
        }
        setItem(slot, item);
    }

    /**
     * Sets a close button at the specified slot.
     *
     * @param slot The slot to place the close button
     */
    public void setCloseButton(int slot) {
        setItem(slot, createCloseButton(), event -> {
            event.getWhoClicked().closeInventory();
        });
    }

    /**
     * Adds a clickable slot that can be interacted with.
     *
     * @param slot    The slot to make clickable
     * @param item    The item to display
     * @param handler The handler to execute when clicked
     */
    public void addClickableSlot(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        setItem(slot, item, handler);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // Always cancel the event first (preview mode - no interaction)
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Check for back button
        if (slot == backButtonSlot && backButtonHandler != null) {
            backButtonHandler.accept(event);
            return;
        }

        // Check for other registered handlers
        if (slot >= 0 && slot < size && clickHandlers.containsKey(slot)) {
            clickHandlers.get(slot).accept(event);
        }
    }

    @Override
    public void refresh() {
        // Override in subclass if needed
    }

    /**
     * Fills the entire GUI with black glass panes.
     */
    public void fillWithBlackGlass() {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    /**
     * Fills the entire GUI with the specified glass pane color.
     */
    public void fillWithGlass(Material glassMaterial) {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createItem(glassMaterial, " "));
            }
        }
    }

    /**
     * Sets a display item (non-interactive) at the specified slot.
     */
    public void setDisplayItem(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        // No click handler - display only
    }
}
