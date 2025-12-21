package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * Handles Hammer crafting recipes for all tiers.
 */
public class HammerRecipes {

    private final NekoPlugin plugin;

    public static final NamespacedKey HAMMER_KEY = new NamespacedKey("nekoplugin", "hammer");
    public static final NamespacedKey HAMMER_TIER_KEY = new NamespacedKey("nekoplugin", "hammer_tier");

    // All plank types for wooden hammer
    private static final List<Material> ALL_PLANKS = List.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS,
            Material.CRIMSON_PLANKS, Material.WARPED_PLANKS);

    // Map of tier name to base pickaxe material
    public static final Map<String, HammerTier> TIERS = Map.of(
            "wooden", new HammerTier("Wooden", Material.WOODEN_PICKAXE, Material.OAK_PLANKS, NamedTextColor.GOLD),
            "stone", new HammerTier("Stone", Material.STONE_PICKAXE, Material.COBBLESTONE, NamedTextColor.GRAY),
            "iron", new HammerTier("Iron", Material.IRON_PICKAXE, Material.IRON_INGOT, NamedTextColor.WHITE),
            "golden", new HammerTier("Golden", Material.GOLDEN_PICKAXE, Material.GOLD_INGOT, NamedTextColor.YELLOW),
            "diamond", new HammerTier("Diamond", Material.DIAMOND_PICKAXE, Material.DIAMOND, NamedTextColor.AQUA),
            "netherite",
            new HammerTier("Netherite", Material.NETHERITE_PICKAXE, Material.NETHERITE_INGOT, NamedTextColor.DARK_RED));

    public HammerRecipes(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        for (Map.Entry<String, HammerTier> entry : TIERS.entrySet()) {
            registerHammerRecipe(entry.getKey(), entry.getValue());
            registerCustomRecipe(entry.getKey(), entry.getValue());
        }
        plugin.getLogger().info("Registered hammer crafting recipes (Bukkit + RecipeAPI).");
    }

    private void registerHammerRecipe(String tierName, HammerTier tier) {
        ItemStack hammer = createHammer(tierName, tier);
        NamespacedKey key = new NamespacedKey(plugin, "hammer_" + tierName);

        ShapedRecipe recipe = new ShapedRecipe(key, hammer);
        recipe.shape("MMM", "MSM", " S ");
        recipe.setIngredient('S', Material.STICK);

        // Use RecipeChoice for wooden hammer to accept all plank types
        if (tierName.equals("wooden")) {
            recipe.setIngredient('M', new RecipeChoice.MaterialChoice(ALL_PLANKS));
        } else {
            recipe.setIngredient('M', tier.material());
        }

        plugin.getServer().addRecipe(recipe);
    }

    private void registerCustomRecipe(String tierName, HammerTier tier) {
        ItemStack hammer = createHammer(tierName, tier);

        // For wooden hammer, register recipe for each plank type
        if (tierName.equals("wooden")) {
            for (Material plank : ALL_PLANKS) {
                CustomRecipe recipe = CustomRecipe.builder("hammer_wooden_" + plank.name().toLowerCase())
                        .category("hammer")
                        .result(hammer)
                        .shaped()
                        .pattern("MMM", "MSM", " S ",
                                Map.of(
                                        'M', CustomRecipe.Ingredient.of(plank),
                                        'S', CustomRecipe.Ingredient.of(Material.STICK)))
                        .build();
                RecipeAPI.getInstance().registerRecipe(recipe);
            }
        } else {
            CustomRecipe recipe = CustomRecipe.builder("hammer_" + tierName)
                    .category("hammer")
                    .result(hammer)
                    .shaped()
                    .pattern("MMM", "MSM", " S ",
                            Map.of(
                                    'M', CustomRecipe.Ingredient.of(tier.material()),
                                    'S', CustomRecipe.Ingredient.of(Material.STICK)))
                    .build();
            RecipeAPI.getInstance().registerRecipe(recipe);
        }
    }

    public static ItemStack createHammer(String tierName, HammerTier tier) {
        ItemStack hammer = new ItemStack(tier.baseTool());
        ItemMeta meta = hammer.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(tier.displayName() + " Hammer")
                    .color(tier.color())
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.empty(),
                    Component.text("3×3 Mining Area")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Cannot use Ore Excavation")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)));

            // Mark as hammer
            meta.getPersistentDataContainer().set(HAMMER_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(HAMMER_TIER_KEY, PersistentDataType.STRING, tierName);

            hammer.setItemMeta(meta);
        }

        return hammer;
    }

    public static boolean isHammer(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        Byte value = meta.getPersistentDataContainer().get(HAMMER_KEY, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public static String getHammerTier(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(HAMMER_TIER_KEY, PersistentDataType.STRING);
    }

    public record HammerTier(String displayName, Material baseTool, Material material, NamedTextColor color) {
    }
}
