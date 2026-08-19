package com.nyarutoru.nekoplugin.features.furnace;

import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles creation and registration of Upgrade Furnace crafting recipes.
 */
public class FurnaceRecipes {
    private static final NamespacedKey TIER_KEY = new NamespacedKey("nekoplugin", "furnace_tier");

    private final List<String> registeredRecipeIds = new ArrayList<>();

    public static ItemStack createFurnaceItem(FurnaceTier tier) {
        ItemStack item = new ItemStack(Material.FURNACE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setCustomModelData(3000 + tier.getLevel());
            meta.displayName(Component.text("Upgrade Furnace ")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(tier.getDisplayNameComponent()));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Speed: ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(tier.getSpeedMultiplier() + "x")
                            .color(NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)));
            if (tier.getNextTier() != null) {
                lore.add(Component.empty());
                lore.add(Component.text("Upgrade with: ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(formatMaterial(tier.getNextTier().getUpgradeMaterial()))
                                .color(NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)));
            } else {
                lore.add(Component.empty());
                lore.add(Component.text("Maximum tier")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Place and right-click to use")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(TIER_KEY, PersistentDataType.STRING, tier.name());
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }

        return item;
    }

    public static FurnaceTier getTierFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.FURNACE)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        String tierName = meta.getPersistentDataContainer().get(TIER_KEY, PersistentDataType.STRING);
        return tierName == null ? null : FurnaceTier.getByName(tierName);
    }

    public static boolean isFurnaceItem(ItemStack item) {
        return getTierFromItem(item) != null;
    }

    public static NamespacedKey getTierKey() {
        return TIER_KEY;
    }

    private static String formatMaterial(Material material) {
        if (material == null)
            return "None";
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!result.isEmpty())
                result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public void registerAll() {
        unregisterAll();
        registerBaseRecipe();

        for (FurnaceTier tier : FurnaceTier.values()) {
            if (tier == FurnaceTier.TIER_1)
                continue;
            registerUpgradeRecipe(tier);
        }
    }

    public void unregisterAll() {
        for (String recipeId : List.copyOf(registeredRecipeIds)) {
            RecipeAPI.getInstance().unregisterRecipe(recipeId);
        }
        registeredRecipeIds.clear();
    }

    private void register(CustomRecipe recipe) {
        RecipeAPI.getInstance().registerRecipe(recipe);
        registeredRecipeIds.add(recipe.getId());
    }

    private void registerBaseRecipe() {
        ItemStack result = createFurnaceItem(FurnaceTier.TIER_1);

        CustomRecipe recipe = CustomRecipe.builder("furnace_tier_1")
                .category("furnace")
                .result(result)
                .shaped()
                .pattern("CCC", "CFC", "CCC",
                        Map.of(
                                'C', CustomRecipe.Ingredient.of(Material.STONE),
                                'F', CustomRecipe.Ingredient.of(Material.FURNACE)))
                .build();

        register(recipe);
    }

    private void registerUpgradeRecipe(FurnaceTier tier) {
        FurnaceTier previousTier = tier.getPreviousTier();
        if (previousTier == null)
            return;

        ItemStack result = createFurnaceItem(tier);
        ItemStack previousDisplayItem = createFurnaceItem(previousTier);

        CustomRecipe.Ingredient furnaceIngredient = CustomRecipe.Ingredient.ofCustomItem(
                Material.FURNACE,
                TIER_KEY,
                previousTier.name(),
                previousDisplayItem);

        CustomRecipe recipe = CustomRecipe.builder("furnace_tier_" + tier.getLevel())
                .category("furnace")
                .result(result)
                .shaped()
                .pattern("MMM", "MFM", "MMM",
                        Map.of(
                                'M', CustomRecipe.Ingredient.of(tier.getUpgradeMaterial()),
                                'F', furnaceIngredient))
                .build();

        register(recipe);
    }
}
