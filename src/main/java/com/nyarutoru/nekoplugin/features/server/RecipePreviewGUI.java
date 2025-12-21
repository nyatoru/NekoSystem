package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
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
 * Recipe Preview GUI for displaying a single recipe's crafting pattern.
 * Separate from RecipeBookGUI for cleaner code organization.
 */
public class RecipePreviewGUI implements Listener {

    private final NekoPlugin plugin;
    private final RecipeBookGUI recipeBookGUI;

    // Track players viewing preview - store the inventory for reliable comparison
    private final Map<UUID, Inventory> playerInventories = new HashMap<>();

    // Preview GUI layout (same as crafting table)
    private static final int[] CRAFTING_SLOTS = { 10, 11, 12, 19, 20, 21, 28, 29, 30 };
    private static final int RESULT_SLOT = 24;
    private static final int ARROW_SLOT = 23;
    private static final int INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 49;

    private static final Component PREVIEW_TITLE = Component.text("✦ Recipe Preview ✦")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true);

    public RecipePreviewGUI(NekoPlugin plugin, RecipeBookGUI recipeBookGUI) {
        this.plugin = plugin;
        this.recipeBookGUI = recipeBookGUI;
    }

    /**
     * Open recipe preview for a specific recipe.
     */
    public void openPreview(Player player, CustomRecipe recipe) {
        Inventory gui = Bukkit.createInventory(null, 54, PREVIEW_TITLE);

        ItemStack blackGlass = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);

        // Fill all with black glass first
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, blackGlass);
        }

        // Show crafting grid
        CustomRecipe.Ingredient[] ingredients = recipe.getIngredients();
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack ingredient = ingredientToItemStack(ingredients[i]);
            gui.setItem(CRAFTING_SLOTS[i], ingredient);
        }

        // Arrow
        gui.setItem(ARROW_SLOT, createArrowItem());

        // Result
        gui.setItem(RESULT_SLOT, recipe.getResult().clone());

        // Back button
        gui.setItem(BACK_BUTTON_SLOT, createBackButton());

        // Recipe info
        gui.setItem(INFO_SLOT, createRecipeInfoItem(recipe));

        // Store the inventory BEFORE opening so listener can detect it
        playerInventories.put(player.getUniqueId(), gui);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Inventory trackedInventory = playerInventories.get(player.getUniqueId());
        if (trackedInventory == null)
            return;

        // Check if this is our inventory by reference
        if (event.getInventory() != trackedInventory)
            return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54)
            return;

        // Back button - return to recipe book
        if (slot == BACK_BUTTON_SLOT) {
            playerInventories.remove(player.getUniqueId());
            recipeBookGUI.openRecipeBook(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            playerInventories.remove(player.getUniqueId());
        }
    }

    // ========== Helper Methods ==========

    private ItemStack ingredientToItemStack(CustomRecipe.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }
        Material material = ingredient.getMaterial();
        if (material == null) {
            return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }
        return new ItemStack(material);
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

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("← Back to Recipes")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createArrowItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("→ Crafts →")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRecipeInfoItem(CustomRecipe recipe) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Recipe: " + recipe.getId())
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = List.of(
                    Component.empty(),
                    Component.text("Category: " + recipe.getCategory())
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Type: " + recipe.getShape().name())
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
