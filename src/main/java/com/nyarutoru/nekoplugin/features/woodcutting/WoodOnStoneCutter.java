package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates stonecutter recipes for wood processing.
 * Allows converting logs to all wood-related items (planks, stairs, slabs, etc.)
 */
public class WoodOnStoneCutter {

    private final NekoPlugin plugin;
    private final FileConfiguration config;
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

    public WoodOnStoneCutter(NekoPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * Register all wood stonecutter recipes.
     */
    public void registerRecipes() {
        int recipeCount = 0;

        // Get output config section
        ConfigurationSection outputSection = config.getConfigurationSection("woodcutting.output");

        for (Material log : Tag.LOGS.getValues()) {
            // Skip stripped logs as input
            if (log.name().contains("STRIPPED")) {
                continue;
            }

            // Get wood type prefix (e.g., "OAK" from "OAK_LOG")
            String woodType = getWoodType(log);
            if (woodType == null) {
                continue;
            }

            // Create recipes for each wood item type
            for (String itemType : WOOD_ITEMS) {
                // Get output amount from config
                int outputAmount = 1;
                if (outputSection != null) {
                    outputAmount = outputSection.getInt(itemType.toLowerCase(), 1);
                }

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

        plugin.getLogger().info("Registered " + recipeCount + " wood stonecutter recipes");
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
        try {
            String materialName = woodType + "_" + itemType;
            return Material.getMaterial(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
