package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates stonecutter recipes for wood processing.
 * Allows converting logs to all wood-related items (planks, stairs, slabs, etc.)
 * 
 * Prevents duplicate recipes by tracking processed wood types instead of materials.
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
    private static final Map<String, Integer> OUTPUT_AMOUNTS = Map.ofEntries(
            Map.entry("PLANKS", 4),
            Map.entry("STAIRS", 4),
            Map.entry("SLAB", 2),
            Map.entry("FENCE", 4),
            Map.entry("FENCE_GATE", 1),
            Map.entry("DOOR", 1),
            Map.entry("TRAPDOOR", 2),
            Map.entry("PRESSURE_PLATE", 2),
            Map.entry("BUTTON", 4),
            Map.entry("SIGN", 2),
            Map.entry("HANGING_SIGN", 2)
    );

    public WoodOnStoneCutter(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all wood stonecutter recipes.
     * Prevents duplicates by tracking processed wood types.
     */
    public void registerRecipes() {
        // Clear any existing recipes first
        removeRecipes();
        
        int recipeCount = 0;
        int skippedCount = 0;
        int duplicateCount = 0;

        // Track processed wood types to prevent duplicates
        // Tag.LOGS contains both OAK_LOG and OAK_WOOD, which would create duplicate recipes
        Set<String> processedWoodTypes = new HashSet<>();

        for (Material log : Tag.LOGS.getValues()) {
            // Skip stripped logs as input
            if (log.name().contains("STRIPPED")) {
                skippedCount++;
                continue;
            }

            // Get wood type prefix (e.g., "OAK" from "OAK_LOG" or "OAK_WOOD")
            String woodType = getWoodType(log);
            if (woodType == null) {
                plugin.getLogger().warning("Failed to detect wood type for: " + log.name());
                skippedCount++;
                continue;
            }

            // Skip if we already processed this wood type (prevents duplicate recipes)
            if (processedWoodTypes.contains(woodType)) {
                plugin.getLogger().fine("Skipping duplicate wood type: " + woodType + " (from " + log.name() + ")");
                duplicateCount++;
                continue;
            }
            processedWoodTypes.add(woodType);

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
            (duplicateCount > 0 ? " (skipped " + duplicateCount + " duplicates)" : "") +
            (skippedCount > 0 ? " (skipped " + skippedCount + " invalid)" : ""));
    }

    /**
     * Remove all registered recipes.
     */
    public void removeRecipes() {
        int removedCount = 0;
        for (NamespacedKey key : recipeKeys) {
            if (plugin.getServer().removeRecipe(key)) {
                removedCount++;
            }
        }
        recipeKeys.clear();
        
        if (removedCount > 0) {
            plugin.getLogger().fine("Removed " + removedCount + " wood recipes");
        }
    }

    /**
     * Extract wood type from log material name.
     * Handles: LOG, WOOD, STEM, HYPHAE, and STRIPPED_* variants.
     */
    private String getWoodType(Material log) {
        String name = log.name();

        // Handle special nether wood cases
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

        // Remove suffix to get wood type
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
     */
    private Material getMaterial(String woodType, String itemType) {
        if (woodType == null || itemType == null) {
            return null;
        }
        
        try {
            String materialName = woodType + "_" + itemType;
            Material material = Material.getMaterial(materialName);
            
            if (material != null && !material.isItem()) {
                return null;
            }
            
            return material;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
