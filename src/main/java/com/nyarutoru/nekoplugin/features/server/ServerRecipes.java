package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

/**
 * Server-side custom recipes (furnace, smelting, etc.)
 */
public class ServerRecipes {

    private static final String RECIPE_KEY = "rotten_flesh_to_leather";
    private final NekoPlugin plugin;
    private NamespacedKey recipeKey;

    public ServerRecipes(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all server recipes.
     */
    public void registerAll() {
        unregisterAll();
        registerRottenFleshToLeather();
    }

    /**
     * Register rotten flesh to leather furnace recipe.
     * Allows players to obtain leather by smelting rotten flesh.
     */
    private void registerRottenFleshToLeather() {
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        recipeKey = key;

        ItemStack result = new ItemStack(Material.LEATHER);
        RecipeChoice.MaterialChoice input = new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH);

        // Experience: 0.35 (similar to cooking meat)
        // Cooking time: 200 ticks (10 seconds, standard furnace time)
        FurnaceRecipe recipe = new FurnaceRecipe(key, result, input, 0.35f, 200);

        plugin.getServer().addRecipe(recipe);
    }

    public void unregisterAll() {
        if (recipeKey != null) {
            plugin.getServer().removeRecipe(recipeKey);
            recipeKey = null;
        }
    }
}
