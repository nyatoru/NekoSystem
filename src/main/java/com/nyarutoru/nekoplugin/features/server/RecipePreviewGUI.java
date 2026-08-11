package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.gui.PreviewGUI;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * Recipe Preview GUI for displaying a single recipe's crafting pattern.
 * Supports both custom (RecipeAPI) and vanilla recipes.
 */
public class RecipePreviewGUI {

    private static final int[] CRAFTING_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 24;
    private static final int ARROW_SLOT = 23;
    private static final int INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 49;
    private static final Component PREVIEW_TITLE = Component.text("✦ Recipe Preview ✦")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true);
    private final NekoPlugin plugin;
    private final RecipeBookGUI recipeBookGUI;

    public RecipePreviewGUI(NekoPlugin plugin, RecipeBookGUI recipeBookGUI) {
        this.plugin = plugin;
        this.recipeBookGUI = recipeBookGUI;
    }

    /** Backwards compat */
    public void openPreview(Player player, CustomRecipe recipe) {
        openPreview(player, recipe, null);
    }

    public void openPreview(Player player, CustomRecipe recipe, RecipeBookGUI.GUIState prevState) {
        PreviewGUI gui = new PreviewGUI(54, PREVIEW_TITLE);
        gui.fillWithBlackGlass();
        CustomRecipe.Ingredient[] ingredients = recipe.getIngredients();
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack ingredient = ingredientToItemStack(ingredients[i]);
            gui.setDisplayItem(CRAFTING_SLOTS[i], ingredient);
        }
        gui.setDisplayItem(ARROW_SLOT, createArrowItem());
        gui.setDisplayItem(RESULT_SLOT, recipe.getResult().clone());
        gui.setDisplayItem(INFO_SLOT, createRecipeInfoItem(recipe));
        gui.setBackButton(BACK_BUTTON_SLOT, event -> {
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(p, () -> {
                if (prevState != null) recipeBookGUI.openRecipeBook(p, prevState);
                else recipeBookGUI.openRecipeBook(p);
            });
        });
        gui.open(player);
    }

    public void openVanillaPreview(Player player, Recipe recipe, RecipeBookGUI.GUIState prevState) {
        PreviewGUI gui = new PreviewGUI(54, PREVIEW_TITLE);
        gui.fillWithBlackGlass();

        // Fill grid based on vanilla recipe type
        // First clear crafting slots to gray pane, then fill with ingredients
        ItemStack gray = createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        for (int slot : CRAFTING_SLOTS) gui.setDisplayItem(slot, gray);

        if (recipe instanceof ShapedRecipe sr) {
            String[] shape = sr.getShape();
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (int r = 0; r < shape.length && r < 3; r++) {
                String row = shape[r];
                for (int c = 0; c < row.length() && c < 3; c++) {
                    char ch = row.charAt(c);
                    if (ch == ' ') continue;
                    RecipeChoice choice = map.get(ch);
                    ItemStack display = choiceToDisplay(choice);
                    int idx = r * 3 + c;
                    gui.setDisplayItem(CRAFTING_SLOTS[idx], display);
                }
            }
        } else if (recipe instanceof ShapelessRecipe sr) {
            List<RecipeChoice> choices = sr.getChoiceList();
            for (int i = 0; i < choices.size() && i < 9; i++) {
                RecipeChoice choice = choices.get(i);
                ItemStack display = choiceToDisplay(choice);
                gui.setDisplayItem(CRAFTING_SLOTS[i], display);
            }
        } else {
            // fallback: try to show something
        }

        gui.setDisplayItem(ARROW_SLOT, createArrowItem());
        try {
            gui.setDisplayItem(RESULT_SLOT, recipe.getResult().clone());
        } catch (Exception ignored) {
            gui.setDisplayItem(RESULT_SLOT, new ItemStack(Material.BARRIER));
        }
        gui.setDisplayItem(INFO_SLOT, createVanillaInfoItem(recipe));
        gui.setBackButton(BACK_BUTTON_SLOT, event -> {
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(p, () -> {
                if (prevState != null) recipeBookGUI.openRecipeBook(p, prevState);
                else recipeBookGUI.openRecipeBook(p);
            });
        });
        gui.open(player);
    }

    private ItemStack choiceToDisplay(RecipeChoice choice) {
        if (choice == null) return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        try {
            if (choice instanceof RecipeChoice.ExactChoice ec) {
                var list = ec.getChoices();
                if (!list.isEmpty()) {
                    ItemStack rep = list.get(0);
                    if (rep != null && rep.getType() != Material.AIR) return rep.clone();
                }
            } else if (choice instanceof RecipeChoice.MaterialChoice mc) {
                var mats = mc.getChoices();
                if (!mats.isEmpty()) {
                    Material m = mats.get(0);
                    if (m != null && m != Material.AIR) return new ItemStack(m);
                }
            } else if (choice instanceof RecipeChoice.ItemTypeChoice itc) {
                var types = itc.itemTypes().resolve(org.bukkit.Registry.ITEM);
                if (!types.isEmpty()) {
                    var first = types.iterator().next();
                    if (first != null) {
                        ItemStack rep = first.createItemStack();
                        if (rep != null && rep.getType() != Material.AIR) return rep;
                    }
                }
            }
        } catch (Exception ignored) {}
        return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack ingredientToItemStack(CustomRecipe.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return createGlassPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }
        ItemStack displayItem = ingredient.getDisplayItem();
        if (displayItem != null) return displayItem;
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
            meta.displayName(Component.text("→ Crafts →").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRecipeInfoItem(CustomRecipe recipe) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Recipe: " + recipe.getId()).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = List.of(
                    Component.empty(),
                    Component.text("Category: " + recipe.getCategory()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Type: " + recipe.getShape().name()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createVanillaInfoItem(Recipe recipe) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String key = "vanilla";
            if (recipe instanceof ShapedRecipe sr) key = sr.getKey().toString();
            else if (recipe instanceof ShapelessRecipe sr) key = sr.getKey().toString();
            String type = recipe.getClass().getSimpleName();
            meta.displayName(Component.text("Recipe: " + key).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = List.of(
                    Component.empty(),
                    Component.text("Category: vanilla").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Type: " + type).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
