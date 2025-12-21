package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.gui.PreviewGUI;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Recipe Preview GUI for displaying a single recipe's crafting pattern.
 * Uses PreviewGUI from the GuiAPI for reliable non-interactive display.
 */
public class RecipePreviewGUI {

    private final NekoPlugin plugin;
    private final RecipeBookGUI recipeBookGUI;

    // Preview GUI layout
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
        PreviewGUI gui = new PreviewGUI(54, PREVIEW_TITLE);

        // Fill with black glass
        gui.fillWithBlackGlass();

        // Show crafting grid
        CustomRecipe.Ingredient[] ingredients = recipe.getIngredients();
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack ingredient = ingredientToItemStack(ingredients[i]);
            gui.setDisplayItem(CRAFTING_SLOTS[i], ingredient);
        }

        // Arrow
        gui.setDisplayItem(ARROW_SLOT, createArrowItem());

        // Result
        gui.setDisplayItem(RESULT_SLOT, recipe.getResult().clone());

        // Recipe info
        gui.setDisplayItem(INFO_SLOT, createRecipeInfoItem(recipe));

        // Back button - uses scheduler to avoid inventory conflicts
        gui.setBackButton(BACK_BUTTON_SLOT, event -> {
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> recipeBookGUI.openRecipeBook(p));
        });

        gui.open(player);
    }

    // ========== Helper Methods ==========

    private ItemStack ingredientToItemStack(CustomRecipe.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }

        // Use the display item if available (shows custom name, lore, etc.)
        ItemStack displayItem = ingredient.getDisplayItem();
        if (displayItem != null) {
            return displayItem;
        }

        return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
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
