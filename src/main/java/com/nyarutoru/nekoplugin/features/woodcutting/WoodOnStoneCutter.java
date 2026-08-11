package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        Map<String, List<Material>> inputsByWoodType = groupInputsByWoodType(Tag.LOGS.getValues());

        for (Map.Entry<String, List<Material>> entry : inputsByWoodType.entrySet()) {
            String woodType = entry.getKey();
            RecipeChoice.MaterialChoice input = new RecipeChoice.MaterialChoice(entry.getValue());

            for (String itemType : WOOD_ITEMS) {
                int outputAmount = OUTPUT_AMOUNTS.getOrDefault(itemType, 1);
                Material outputMaterial = getMaterial(woodType, itemType);

                if (outputMaterial != null && outputMaterial.isItem()) {
                    NamespacedKey key = new NamespacedKey(plugin, recipeKeyPath(woodType, itemType));
                    ItemStack result = new ItemStack(outputMaterial, outputAmount);
                    StonecuttingRecipe recipe = new StonecuttingRecipe(key, result, input);

                    // Remove a stale recipe left by an earlier feature instance.
                    plugin.getServer().removeRecipe(key);
                    recipeKeys.add(key);
                    if (plugin.getServer().addRecipe(recipe)) {
                        recipeCount++;
                    } else {
                        skippedCount++;
                        plugin.getLogger().warning("Failed to register woodcutting recipe: " + key);
                    }
                }
            }
        }

        plugin.getLogger().info("Registered " + recipeCount + " wood stonecutter recipes" +
            (skippedCount > 0 ? " (skipped " + skippedCount + " invalid)" : ""));
    }

    private String recipeKeyPath(String woodType, String itemType) {
        return "woodcutting/" + woodType.toLowerCase() + "/" + itemType.toLowerCase();
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

    static Map<String, List<Material>> groupInputsByWoodType(Iterable<Material> logs) {
        Map<String, List<Material>> inputsByWoodType = new TreeMap<>();
        for (Material log : logs) {
            if (log.name().startsWith("STRIPPED_")) {
                continue;
            }

            String woodType = getWoodType(log);
            if (woodType != null) {
                inputsByWoodType.computeIfAbsent(woodType, ignored -> new ArrayList<>()).add(log);
            }
        }

        inputsByWoodType.values().forEach(inputs -> inputs.sort(Comparator.comparing(Material::name)));
        return inputsByWoodType;
    }

    /**
     * Extract wood type from log material name.
     * Handles: LOG, WOOD, STEM, HYPHAE, and STRIPPED_* variants.
     */
    static String getWoodType(Material log) {
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
