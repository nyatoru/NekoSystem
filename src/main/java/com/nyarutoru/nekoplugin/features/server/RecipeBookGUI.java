package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Recipe Book GUI for browsing custom recipes.
 * Shows all RecipeAPI items and displays crafting recipes when clicked.
 */
public class RecipeBookGUI implements Listener {

    // GUI Layout constants
    private static final int ITEMS_PER_PAGE = 28; // 4 rows of 7 items
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final Component BOOK_TITLE = Component.text("✦ Recipe Book ✦")
            .color(NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.BOLD, true);
    private final NekoPlugin plugin;
    // Track which GUI players have open
    private final Map<UUID, GUIState> playerStates = new java.util.concurrent.ConcurrentHashMap<>();
    private RecipePreviewGUI recipePreviewGUI;

    public RecipeBookGUI(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public RecipePreviewGUI getRecipePreviewGUI() {
        return recipePreviewGUI;
    }

    public void setRecipePreviewGUI(RecipePreviewGUI recipePreviewGUI) {
        this.recipePreviewGUI = recipePreviewGUI;
    }

    /**
     * Open the recipe book for a player.
     */
    public void openRecipeBook(Player player) {
        List<CustomRecipe> allRecipes = RecipeAPI.getInstance().getAllRecipes();

        // Group recipes by result display name + material to avoid true duplicates
        // but still show different tiers (like Drawers with same material but different
        // names)
        Map<String, CustomRecipe> uniqueRecipes = new LinkedHashMap<>();
        for (CustomRecipe recipe : allRecipes) {
            ItemStack result = recipe.getResult();
            String displayName = "";
            if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(result.getItemMeta().displayName());
            }
            // Use combination of display name and material for unique key
            String key = displayName + "_" + result.getType().name();
            if (!uniqueRecipes.containsKey(key)) {
                uniqueRecipes.put(key, recipe);
            }
        }

        List<CustomRecipe> recipes = new ArrayList<>(uniqueRecipes.values());
        GUIState state = new GUIState(0, recipes);
        playerStates.put(player.getUniqueId(), state);

        openRecipeListPage(player, state);
    }

    private void openRecipeListPage(Player player, GUIState state) {
        Inventory gui = Bukkit.createInventory(null, 54, BOOK_TITLE);

        ItemStack blackGlass = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);

        // Fill borders
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, blackGlass);
            gui.setItem(45 + i, blackGlass);
        }
        gui.setItem(9, grayGlass);
        gui.setItem(17, grayGlass);
        gui.setItem(18, grayGlass);
        gui.setItem(26, grayGlass);
        gui.setItem(27, grayGlass);
        gui.setItem(35, grayGlass);
        gui.setItem(36, grayGlass);
        gui.setItem(44, grayGlass);

        // Populate items
        int startIndex = state.page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, state.recipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex < ITEM_SLOTS.length) {
                CustomRecipe recipe = state.recipes.get(i);
                ItemStack displayItem = createRecipeDisplayItem(recipe);
                gui.setItem(ITEM_SLOTS[slotIndex], displayItem);
            }
        }

        // Navigation buttons
        if (state.page > 0) {
            gui.setItem(48, createNavigationItem(Material.ARROW, "← Previous Page"));
        }

        int totalPages = (int) Math.ceil((double) state.recipes.size() / ITEMS_PER_PAGE);
        if (state.page < totalPages - 1) {
            gui.setItem(50, createNavigationItem(Material.ARROW, "Next Page →"));
        }

        // Close button
        gui.setItem(49, createCloseButton());

        // Page indicator
        gui.setItem(4, createPageIndicator(state.page + 1, totalPages));

        player.openInventory(gui);
    }

    /**
     * Open recipe preview for a specific recipe.
     */
    private void openRecipePreview(Player player, CustomRecipe recipe) {
        // Remove player from our tracking
        playerStates.remove(player.getUniqueId());
        // Delegate to RecipePreviewGUI
        if (recipePreviewGUI != null) {
            recipePreviewGUI.openPreview(player, recipe);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        GUIState state = playerStates.get(player.getUniqueId());
        if (state == null)
            return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54)
            return;

        handleRecipeListClick(player, state, slot);
    }

    private void handleRecipeListClick(Player player, GUIState state, int slot) {
        // Check if close button
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Check previous page
        if (slot == 48 && state.page > 0) {
            state.page--;
            openRecipeListPage(player, state);
            return;
        }

        // Check next page
        int totalPages = (int) Math.ceil((double) state.recipes.size() / ITEMS_PER_PAGE);
        if (slot == 50 && state.page < totalPages - 1) {
            state.page++;
            openRecipeListPage(player, state);
            return;
        }

        // Check if item slot
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                int recipeIndex = state.page * ITEMS_PER_PAGE + i;
                if (recipeIndex < state.recipes.size()) {
                    openRecipePreview(player, state.recipes.get(recipeIndex));
                }
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            playerStates.remove(player.getUniqueId());
        }
    }

    private ItemStack createRecipeDisplayItem(CustomRecipe recipe) {
        ItemStack item = recipe.getResult().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Click to view recipe")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Category: " + recipe.getCategory())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlassPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✖ Close")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPageIndicator(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Page " + currentPage + "/" + totalPages)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ========== State Classes ==========

    private static class GUIState {
        int page;
        List<CustomRecipe> recipes;

        GUIState(int page, List<CustomRecipe> recipes) {
            this.page = page;
            this.recipes = recipes;
        }
    }
}
