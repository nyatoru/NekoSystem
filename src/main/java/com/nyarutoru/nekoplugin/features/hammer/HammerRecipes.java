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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Handles Hammer crafting recipes for all tiers.
 */
public class HammerRecipes {

    public static final NamespacedKey HAMMER_KEY = new NamespacedKey("nekoplugin", "hammer");
    public static final NamespacedKey HAMMER_TIER_KEY = new NamespacedKey("nekoplugin", "hammer_tier");
    // Map of tier name to base pickaxe material
    public static final Map<String, HammerTier> TIERS = Map.of(
            "wooden", new HammerTier("Wooden", Material.WOODEN_PICKAXE, Material.OAK_PLANKS, NamedTextColor.GOLD),
            "stone", new HammerTier("Stone", Material.STONE_PICKAXE, Material.COBBLESTONE, NamedTextColor.GRAY),
            "iron", new HammerTier("Iron", Material.IRON_PICKAXE, Material.IRON_INGOT, NamedTextColor.WHITE),
            "golden", new HammerTier("Golden", Material.GOLDEN_PICKAXE, Material.GOLD_INGOT, NamedTextColor.YELLOW),
            "diamond", new HammerTier("Diamond", Material.DIAMOND_PICKAXE, Material.DIAMOND, NamedTextColor.AQUA),
            "netherite",
            new HammerTier("Netherite", Material.NETHERITE_PICKAXE, Material.NETHERITE_INGOT, NamedTextColor.DARK_RED));

    private final NekoPlugin plugin;
    private final List<NamespacedKey> bukkitRecipeKeys = new ArrayList<>();
    private final List<String> customRecipeIds = new ArrayList<>();

    public HammerRecipes(NekoPlugin plugin) {
        this.plugin = plugin;
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

    public void registerAll() {
        unregisterAll();
        TIERS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    registerHammerRecipe(entry.getKey(), entry.getValue());
                    registerCustomRecipe(entry.getKey(), entry.getValue());
                });
    }

    public void unregisterAll() {
        for (NamespacedKey key : bukkitRecipeKeys) {
            plugin.getServer().removeRecipe(key);
        }
        bukkitRecipeKeys.clear();

        for (String id : customRecipeIds) {
            RecipeAPI.getInstance().unregisterRecipe(id);
        }
        customRecipeIds.clear();
    }

    private void registerHammerRecipe(String tierName, HammerTier tier) {
        ItemStack hammer = createHammer(tierName, tier);
        NamespacedKey key = new NamespacedKey(plugin, "hammer_" + tierName);

        // Remove stale registrations from a previous feature instance before adding.
        plugin.getServer().removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, hammer);
        recipe.shape("PPP", " S ", " S ");
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('P', tier.baseTool());

        if (plugin.getServer().addRecipe(recipe)) {
            bukkitRecipeKeys.add(key);
        }
    }

    private void registerCustomRecipe(String tierName, HammerTier tier) {
        ItemStack hammer = createHammer(tierName, tier);
        String id = recipeId(tierName);

        CustomRecipe recipe = CustomRecipe.builder(id)
                .category("hammer")
                .result(hammer)
                .shaped()
                .pattern("PPP", " S ", " S ",
                        Map.of(
                                'P', CustomRecipe.Ingredient.of(tier.baseTool()),
                                'S', CustomRecipe.Ingredient.of(Material.STICK)))
                .build();
        RecipeAPI.getInstance().registerRecipe(recipe);
        customRecipeIds.add(id);
    }

    static List<String> orderedTierNames() {
        return TIERS.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    static String recipeId(String tierName) {
        return "hammer_" + tierName;
    }

    public record HammerTier(String displayName, Material baseTool, Material material, NamedTextColor color) {
    }
}
