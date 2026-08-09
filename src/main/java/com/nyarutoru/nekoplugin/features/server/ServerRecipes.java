package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

/**
 * Server-side custom recipes (furnace, smelting, etc.)
 */
public class ServerRecipes {

    private static final String RECIPE_KEY = "rotten_flesh_to_leather";
    private static final String MOSS_RECIPE_ID = "dirt_leaves_to_moss";
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
        registerMossRecipe();
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

    private void registerMossRecipe() {
        // 8 dirt surrounding any leaves (center) -> 8 moss_block
        ItemStack result = new ItemStack(Material.MOSS_BLOCK, 8);
        CustomRecipe recipe = CustomRecipe.builder(MOSS_RECIPE_ID)
                .category("server")
                .result(result)
                .shaped()
                .pattern("DDD", "DLD", "DDD", java.util.Map.of(
                        'D', CustomRecipe.Ingredient.of(Material.DIRT),
                        'L', CustomRecipe.Ingredient.ofTag(Tag.LEAVES)))
                .build();
        RecipeAPI.getInstance().registerRecipe(recipe);
    }

    public void unregisterAll() {
        if (recipeKey != null) {
            plugin.getServer().removeRecipe(recipeKey);
            recipeKey = null;
        }
        RecipeAPI.getInstance().unregisterRecipe(MOSS_RECIPE_ID);
    }
}
