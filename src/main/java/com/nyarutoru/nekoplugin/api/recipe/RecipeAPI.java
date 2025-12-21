package com.nyarutoru.nekoplugin.api.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recipe API for custom crafting recipes.
 * Supports both shaped and shapeless recipes with custom ItemStack results.
 */
public class RecipeAPI {

    private static volatile RecipeAPI instance;
    // Thread-safe list for recipes
    private final List<CustomRecipe> recipes = new CopyOnWriteArrayList<>();

    private RecipeAPI() {
    }

    public static RecipeAPI getInstance() {
        if (instance == null) {
            synchronized (RecipeAPI.class) {
                if (instance == null) {
                    instance = new RecipeAPI();
                }
            }
        }
        return instance;
    }

    /**
     * Register a custom recipe.
     */
    public void registerRecipe(CustomRecipe recipe) {
        recipes.add(recipe);
    }

    /**
     * Unregister a recipe by ID.
     */
    public void unregisterRecipe(String recipeId) {
        recipes.removeIf(r -> r.getId().equals(recipeId));
    }

    /**
     * Find a matching recipe for the given crafting grid.
     * 
     * @param grid 9-element array representing 3x3 crafting grid
     * @return Matching recipe result, or null if no match
     */
    public ItemStack findMatchingRecipe(ItemStack[] grid) {
        for (CustomRecipe recipe : recipes) {
            if (recipe.matches(grid)) {
                // Use getResult(grid) to apply any result transformers
                return recipe.getResult(grid);
            }
        }
        return null;
    }

    /**
     * Get all registered recipes.
     */
    public List<CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    /**
     * Clear all recipes.
     */
    public void clearRecipes() {
        recipes.clear();
    }

    /**
     * Get recipes by category.
     */
    public List<CustomRecipe> getRecipesByCategory(String category) {
        return recipes.stream()
                .filter(r -> category.equals(r.getCategory()))
                .toList();
    }
}
