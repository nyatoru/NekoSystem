package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Custom Crafting Table with Double Chest GUI.
 * Replaces vanilla crafting table GUI to support custom recipes.
 */
public class CustomCraftingListener implements Listener {

    private final NekoPlugin plugin;
    private final Set<UUID> openCraftingGUIs = new HashSet<>();
    private RecipeBookGUI recipeBookGUI;

    // GUI Layout (54 slots - double chest)
    // Slots 0-8: Top decoration row
    // Slots 9-17: Empty row
    // Slots 18-20: 3x3 crafting grid (row 1)
    // Slots 27-29: 3x3 crafting grid (row 2)
    // Slots 36-38: 3x3 crafting grid (row 3)
    // Slot 24: Result slot
    // Slots 45-53: Bottom decoration row

    private static final int[] CRAFTING_SLOTS = { 10, 11, 12, 19, 20, 21, 28, 29, 30 };
    private static final int RESULT_SLOT = 24;
    private static final int CRAFT_BUTTON_SLOT = 23;
    private static final int CLOSE_SLOT = 49;
    private static final int RECIPE_BOOK_SLOT = 18;

    private static final Component TITLE = Component.text("✦ Crafting Table ✦")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true);

    public CustomCraftingListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.recipeBookGUI = new RecipeBookGUI(plugin);
        // Create and wire up RecipePreviewGUI
        RecipePreviewGUI recipePreviewGUI = new RecipePreviewGUI(plugin, recipeBookGUI);
        this.recipeBookGUI.setRecipePreviewGUI(recipePreviewGUI);
    }

    public RecipeBookGUI getRecipeBookGUI() {
        return recipeBookGUI;
    }

    public RecipePreviewGUI getRecipePreviewGUI() {
        return recipeBookGUI.getRecipePreviewGUI();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;
        if (event.getClickedBlock().getType() != Material.CRAFTING_TABLE)
            return;

        // Cancel vanilla crafting table opening
        event.setCancelled(true);

        // Open custom GUI
        openCraftingGUI(event.getPlayer());
    }

    private void openCraftingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        // Fill decoration - all black glass
        ItemStack blackGlass = createBlackGlassPane();

        // Top row - black glass
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, blackGlass);
        }

        // Bottom row - dynamic slots (45-48, 50-53) start red, close button at 49
        ItemStack redGlass = createRedGlassPane();
        for (int i = 45; i <= 48; i++) {
            gui.setItem(i, redGlass);
        }
        for (int i = 50; i <= 53; i++) {
            gui.setItem(i, redGlass);
        }

        // Side decorations - black glass
        gui.setItem(9, blackGlass);
        gui.setItem(17, blackGlass);
        gui.setItem(26, blackGlass);
        gui.setItem(27, blackGlass);
        gui.setItem(35, blackGlass);

        // Recipe Book button at slot 18
        gui.setItem(RECIPE_BOOK_SLOT, createRecipeBookButton());

        // Row 36-44: black glass pane (unused row)
        for (int i = 36; i <= 44; i++) {
            gui.setItem(i, blackGlass);
        }

        // Additional unused slots - black glass pane
        int[] unusedSlots = { 13, 14, 15, 16, 22, 25, 31, 32, 33, 34 };
        for (int slot : unusedSlots) {
            gui.setItem(slot, blackGlass);
        }

        // Arrow indicator
        gui.setItem(CRAFT_BUTTON_SLOT, createArrowItem());

        // Close button
        gui.setItem(CLOSE_SLOT, createCloseButton());

        // Result placeholder (barrier when empty)
        gui.setItem(RESULT_SLOT, createResultPlaceholder());

        player.openInventory(gui);
        openCraftingGUIs.add(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!openCraftingGUIs.contains(player.getUniqueId()))
            return;

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();

        // Allow clicks in player inventory, but update result after
        if (slot >= 54) {
            // If shift-clicking from player inventory, update result after
            if (event.isShiftClick()) {
                Bukkit.getScheduler().runTask(plugin, () -> updateCraftingResult(inv));
            }
            return;
        }

        // Check if it's a crafting slot
        boolean isCraftingSlot = false;
        for (int craftSlot : CRAFTING_SLOTS) {
            if (slot == craftSlot) {
                isCraftingSlot = true;
                break;
            }
        }

        // Allow crafting slot interaction
        if (isCraftingSlot) {
            // Update result after a tick (handles all click types)
            Bukkit.getScheduler().runTask(plugin, () -> updateCraftingResult(inv));
            return;
        }

        // Result slot - take crafted item
        if (slot == RESULT_SLOT) {
            ItemStack result = inv.getItem(RESULT_SLOT);

            // Check if result is valid (not null, not air, not the placeholder barrier)
            if (result != null && result.getType() != Material.AIR && result.getType() != Material.BARRIER) {
                if (event.isShiftClick()) {
                    // Shift-click: craft as many as possible
                    craftAll(player, inv, result);
                } else {
                    // Normal click: craft one
                    ItemStack cursor = player.getItemOnCursor();
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        // Empty cursor - place result on cursor
                        player.setItemOnCursor(result.clone());
                        inv.setItem(RESULT_SLOT, createResultPlaceholder());
                        consumeCraftingMaterials(inv);
                        Bukkit.getScheduler().runTask(plugin, () -> updateCraftingResult(inv));
                    } else if (cursor.isSimilar(result)) {
                        // Same item on cursor - stack if possible (vanilla behavior)
                        int newAmount = cursor.getAmount() + result.getAmount();
                        if (newAmount <= cursor.getMaxStackSize()) {
                            cursor.setAmount(newAmount);
                            player.setItemOnCursor(cursor);
                            inv.setItem(RESULT_SLOT, createResultPlaceholder());
                            consumeCraftingMaterials(inv);
                            Bukkit.getScheduler().runTask(plugin, () -> updateCraftingResult(inv));
                        }
                    }
                }
            }
            event.setCancelled(true);
            return;
        }

        // Close button
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            event.setCancelled(true);
            return;
        }

        // Recipe Book button
        if (slot == RECIPE_BOOK_SLOT) {
            player.closeInventory();
            recipeBookGUI.openRecipeBook(player);
            event.setCancelled(true);
            return;
        }

        // Cancel other slot interactions (decoration)
        event.setCancelled(true);
    }

    /**
     * Craft as many items as possible with shift-click.
     */
    private void craftAll(Player player, Inventory inv, ItemStack result) {
        int maxCrafts = getMaxCraftCount(inv);

        // Clear result slot first to prevent duplication
        inv.setItem(RESULT_SLOT, createResultPlaceholder());

        for (int i = 0; i < maxCrafts; i++) {
            // Check if player can hold more
            if (!canAddToInventory(player, result))
                break;

            player.getInventory().addItem(result.clone());
            consumeCraftingMaterials(inv);

            // Check if recipe still valid
            ItemStack newResult = findMatchingRecipe(inv);
            if (newResult == null || newResult.getType() == Material.AIR)
                break;
        }

        updateCraftingResult(inv);
    }

    private int getMaxCraftCount(Inventory inv) {
        int min = Integer.MAX_VALUE;
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                min = Math.min(min, item.getAmount());
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private boolean canAddToInventory(Player player, ItemStack item) {
        return player.getInventory().firstEmpty() != -1 ||
                player.getInventory().contains(item.getType());
    }

    private ItemStack findMatchingRecipe(Inventory inv) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
            matrix[i] = item != null ? item.clone() : null;
        }

        ItemStack result = com.nyarutoru.nekoplugin.api.recipe.RecipeAPI.getInstance().findMatchingRecipe(matrix);
        if (result == null) {
            result = Bukkit.craftItem(matrix, Bukkit.getWorlds().get(0));
        }
        return result;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;
        if (!openCraftingGUIs.remove(player.getUniqueId()))
            return;

        Inventory inv = event.getInventory();

        // Return crafting materials to player
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                // Drop items that don't fit
                for (ItemStack leftover : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    private void updateCraftingResult(Inventory inv) {
        // Get crafting grid items
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
            matrix[i] = item != null ? item.clone() : null;
        }

        // First, try custom RecipeAPI
        ItemStack result = com.nyarutoru.nekoplugin.api.recipe.RecipeAPI.getInstance().findMatchingRecipe(matrix);

        // Fall back to Bukkit's recipe system
        if (result == null) {
            result = Bukkit.craftItem(matrix, Bukkit.getWorlds().get(0));
        }

        boolean hasResult = result != null && result.getType() != Material.AIR;

        if (hasResult) {
            inv.setItem(RESULT_SLOT, result);
        } else {
            inv.setItem(RESULT_SLOT, createResultPlaceholder());
        }

        // Update dynamic indicator slots (45-48, 50-53)
        ItemStack indicator = hasResult ? createLimeGlassPane() : createRedGlassPane();
        for (int i = 45; i <= 48; i++) {
            inv.setItem(i, indicator);
        }
        for (int i = 50; i <= 53; i++) {
            inv.setItem(i, indicator);
        }
    }

    private ItemStack createResultPlaceholder() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("No Result")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            barrier.setItemMeta(meta);
        }
        return barrier;
    }

    private void consumeCraftingMaterials(Inventory inv) {
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    inv.setItem(slot, null);
                }
            }
        }
    }

    private ItemStack createGlassPane() {
        ItemStack pane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createDarkGlassPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createBlackGlassPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createRedGlassPane() {
        ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createLimeGlassPane() {
        ItemStack pane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createRecipeBookButton() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📖 Recipe Book")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Click to browse custom recipes")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            book.setItemMeta(meta);
        }
        return book;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!openCraftingGUIs.contains(player.getUniqueId()))
            return;

        boolean affectsCrafting = false;
        boolean affectsDecoration = false;

        for (int slot : event.getRawSlots()) {
            // Skip player inventory slots
            if (slot >= 54)
                continue;

            // Check if crafting slot
            boolean isCraftingSlot = false;
            for (int craftSlot : CRAFTING_SLOTS) {
                if (slot == craftSlot) {
                    isCraftingSlot = true;
                    affectsCrafting = true;
                    break;
                }
            }

            // If not crafting slot, it's a decoration slot
            if (!isCraftingSlot) {
                affectsDecoration = true;
            }
        }

        // Cancel if dragging to decoration slots
        if (affectsDecoration) {
            event.setCancelled(true);
            return;
        }

        // Update result if dragging to crafting slots
        if (affectsCrafting) {
            Bukkit.getScheduler().runTask(plugin, () -> updateCraftingResult(event.getInventory()));
        }
    }

    private ItemStack createArrowItem() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("→ Craft →")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private ItemStack createCloseButton() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✖ Close")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            barrier.setItemMeta(meta);
        }
        return barrier;
    }
}
