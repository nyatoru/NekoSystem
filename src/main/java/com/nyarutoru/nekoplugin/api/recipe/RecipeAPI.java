package com.nyarutoru.nekoplugin.api.recipe;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry and matcher for custom crafting recipes. */
public class RecipeAPI {
    private static volatile RecipeAPI instance;
    private final List<CustomRecipe> recipes = new CopyOnWriteArrayList<>();

    private RecipeAPI() {
    }

    public static RecipeAPI getInstance() {
        if (instance == null) {
            synchronized (RecipeAPI.class) {
                if (instance == null) instance = new RecipeAPI();
            }
        }
        return instance;
    }

    /**
     * Registers a recipe once by ID. Re-registering the same ID replaces it in-place,
     * preserving deterministic recipe priority across feature reloads.
     */
    public synchronized void registerRecipe(CustomRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        String id = Objects.requireNonNull(recipe.getId(), "recipe id");
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().equals(id)) {
                recipes.set(i, recipe);
                return;
            }
        }
        recipes.add(recipe);
    }

    public synchronized boolean unregisterRecipe(String id) {
        Objects.requireNonNull(id, "id");
        return recipes.removeIf(recipe -> recipe.getId().equals(id));
    }

    public synchronized boolean unregisterRecipe(CustomRecipe recipe) {
        return recipe != null && unregisterRecipe(recipe.getId());
    }

    public void clear() {
        recipes.clear();
    }

    public ItemStack findMatchingRecipe(ItemStack[] grid) {
        for (CustomRecipe recipe : recipes) {
            if (recipe.matches(grid)) return recipe.getResult(grid);
        }
        return null;
    }

    /** Returns an immutable snapshot. */
    public List<CustomRecipe> getAllRecipes() {
        return List.copyOf(recipes);
    }
}
