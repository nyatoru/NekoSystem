package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates stonecutter recipes for wood processing.
 * Allows converting logs to all wood-related items (planks, stairs, slabs, etc.)
 */
public class WoodOnStoneCutter {

    private final NekoPlugin plugin;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    // Wood items that can be crafted from logs
    private static final String[] WOOD_ITEMS = {
            "PLANKS",
            "STAIRS",
            "SLAB",
            "FENCE",
            "FENCE_GATE",
            "DOOR",
            "TRAPDOOR",
            "PRESSURE_PLATE",
            "BUTTON",
            "SIGN",
            "HANGING_SIGN"
    };

    // Hardcoded output amounts per item type
    private static final Map<String, Integer> OUTPUT_AMOUNTS;
    static {
        Map<String, Integer> amounts = new java.util.HashMap<>();
        amounts.put("PLANKS", 4);
        amounts.put("STAIRS", 4);
        amounts.put("SLAB", 2);
        amounts.put("FENCE", 4);
        amounts.put("FENCE_GATE", 1);
        amounts.put("DOOR", 1);
        amounts.put("TRAPDOOR", 2);
        amounts.put("PRESSURE_PLATE", 2);
        amounts.put("BUTTON", 4);
        amounts.put("SIGN", 2);
        amounts.put("HANGING_SIGN", 2);
        OUTPUT_AMOUNTS = Map.copyOf(amounts);
    }

    public WoodOnStoneCutter(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all wood stonecutter recipes.
     */
    public void registerRecipes() {
        int recipeCount = 0;
        int skippedCount = 0;

        for (Material log : Tag.LOGS.getValues()) {
            // Skip stripped logs as input
            if (log.name().contains("STRIPPED")) {
                continue;
            }

            // Get wood type prefix (e.g., "OAK" from "OAK_LOG")
            String woodType = getWoodType(log);
            if (woodType == null) {
                plugin.getLogger().warning("Failed to detect wood type for: " + log.name());
                skippedCount++;
                continue;
            }

            // Create recipes for each wood item type
            for (String itemType : WOOD_ITEMS) {
                int outputAmount = OUTPUT_AMOUNTS.getOrDefault(itemType, 1);
                Material outputMaterial = getMaterial(woodType, itemType);

                if (outputMaterial != null && outputMaterial.isItem()) {
                    NamespacedKey key = new NamespacedKey(plugin, "wood_" + woodType.toLowerCase() + "_" + itemType.toLowerCase());
                    ItemStack result = new ItemStack(outputMaterial, outputAmount);
                    StonecuttingRecipe recipe = new StonecuttingRecipe(key, result, log);
                    recipe.setGroup("woodcutting_" + woodType.toLowerCase());

                    plugin.getServer().addRecipe(recipe);
                    recipeKeys.add(key);
                    recipeCount++;
                }
            }
        }

        plugin.getLogger().info("Registered " + recipeCount + " wood stonecutter recipes" + 
            (skippedCount > 0 ? " (skipped " + skippedCount + " materials)" : ""));
    }

    /**
     * Remove all registered recipes.
     */
    public void removeRecipes() {
        for (NamespacedKey key : recipeKeys) {
            plugin.getServer().removeRecipe(key);
        }
        recipeKeys.clear();
    }

    /**
     * Extract wood type from log material name.
     * e.g., "OAK_LOG" -> "OAK", "DARK_OAK_LOG" -> "DARK_OAK"
     */
    private String getWoodType(Material log) {
        String name = log.name();

        // Handle special cases
        if (name.equals("CRIMSON_STEM") || name.equals("CRIMSON_HYPHAE")) {
            return "CRIMSON";
        }
        if (name.equals("WARPED_STEM") || name.equals("WARPED_HYPHAE")) {
            return "WARPED";
        }

        // Remove STRIPPED prefix if present
        if (name.startsWith("STRIPPED_")) {
            name = name.substring(9);
        }

        // Remove LOG or WOOD suffix
        if (name.endsWith("_LOG")) {
            return name.substring(0, name.length() - 4);
        }
        if (name.endsWith("_WOOD")) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith("_STEM")) {
            return name.substring(0, name.length() - 5);
        }
        if (name.endsWith("_HYPHAE")) {
            return name.substring(0, name.length() - 7);
        }

        return null;
    }

    /**
     * Get material from wood type and item type.
     * e.g., ("OAK", "PLANKS") -> OAK_PLANKS
     */
    private Material getMaterial(String woodType, String itemType) {
        if (woodType == null || itemType == null) {
            return null;
        }
        
        try {
            String materialName = woodType + "_" + itemType;
            Material material = Material.getMaterial(materialName);
            
            // Validate material exists and is an item
            if (material != null && !material.isItem()) {
                plugin.getLogger().fine("Material " + materialName + " is not craftable as item");
                return null;
            }
            
            return material;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().fine("Invalid material name: " + woodType + "_" + itemType);
            return null;
        }
    }
}
