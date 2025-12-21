package com.nyarutoru.nekoplugin.features.drawer.crafting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles creation and registration of drawer crafting recipes.
 */
public class DrawerRecipes {

    private final NekoPlugin plugin;

    public DrawerRecipes(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        registerBaseCustomRecipe();

        for (DrawerTier tier : DrawerTier.values()) {
            if (tier == DrawerTier.TIER_1)
                continue;
            registerUpgradeCustomRecipe(tier);
        }

        plugin.getLogger().info("Registered drawer crafting recipes.");
    }

    public static ItemStack createDrawerItem(DrawerTier tier) {
        return createDrawerItemWithContents(tier, null, 0);
    }

    public static ItemStack createDrawerItemWithContents(DrawerTier tier, Material storedItem, int storedCount) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("Drawer ")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(tier.getDisplayName())
                            .decoration(TextDecoration.ITALIC, false)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            String capacityText = tier.getStackCapacity() < 0 ? "Unlimited"
                    : String.format("%,d items", tier.getMaxItems());
            lore.add(Component.text("Capacity: ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(capacityText)
                            .color(NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)));

            if (storedItem != null && storedCount > 0) {
                lore.add(Component.empty());
                lore.add(Component.text("Contains: ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%,d × %s", storedCount, formatMaterial(storedItem)))
                                .color(NamedTextColor.AQUA)
                                .decoration(TextDecoration.ITALIC, false)));
            } else {
                lore.add(Component.empty());
                lore.add(Component.text("Place and right-click to use")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            DrawerTier nextTier = tier.getNextTier();
            if (nextTier != null && storedCount == 0) {
                lore.add(Component.text("Upgrade with: ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(formatMaterial(nextTier.getUpgradeMaterial()))
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)));
            }

            meta.lore(lore);

            meta.getPersistentDataContainer().set(
                    getDrawerTierKey(), PersistentDataType.STRING, tier.name());

            if (storedItem != null && storedCount > 0) {
                meta.getPersistentDataContainer().set(
                        getStoredItemKey(), PersistentDataType.STRING, storedItem.name());
                meta.getPersistentDataContainer().set(
                        getStoredCountKey(), PersistentDataType.INTEGER, storedCount);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static DrawerTier getTierFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;

        String tierName = meta.getPersistentDataContainer().get(
                getDrawerTierKey(), PersistentDataType.STRING);
        if (tierName == null)
            return null;
        return DrawerTier.getByName(tierName);
    }

    public static Material getStoredItemType(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL)
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;

        String itemName = meta.getPersistentDataContainer().get(
                getStoredItemKey(), PersistentDataType.STRING);
        if (itemName == null)
            return null;
        return Material.getMaterial(itemName);
    }

    public static int getStoredItemCount(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL)
            return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return 0;

        Integer count = meta.getPersistentDataContainer().get(
                getStoredCountKey(), PersistentDataType.INTEGER);
        return count != null ? count : 0;
    }

    public static boolean isDrawerItem(ItemStack item) {
        return getTierFromItem(item) != null;
    }

    // ========== RecipeAPI Custom Recipes ==========

    private void registerBaseCustomRecipe() {
        ItemStack result = createDrawerItem(DrawerTier.TIER_1);

        CustomRecipe recipe = CustomRecipe.builder("drawer_tier_1")
                .category("drawer")
                .result(result)
                .shaped()
                .pattern("CCC", "CBC", "CCC",
                        Map.of(
                                'C', CustomRecipe.Ingredient.of(Material.CHEST),
                                'B', CustomRecipe.Ingredient.of(Material.BARREL)))
                .build();

        RecipeAPI.getInstance().registerRecipe(recipe);
    }

    private void registerUpgradeCustomRecipe(DrawerTier tier) {
        Material upgradeMaterial = tier.getUpgradeMaterial();
        if (upgradeMaterial == null)
            return;

        // Get previous tier name for matching
        DrawerTier previousTier = null;
        for (DrawerTier t : DrawerTier.values()) {
            if (t.getLevel() == tier.getLevel() - 1) {
                previousTier = t;
                break;
            }
        }
        if (previousTier == null)
            return;

        final DrawerTier targetTier = tier;
        ItemStack result = createDrawerItem(tier);

        // Create drawer display item for recipe preview
        ItemStack previousDrawerDisplayItem = createDrawerItem(previousTier);
        if (previousDrawerDisplayItem.getItemMeta() != null) {
            com.nyarutoru.nekoplugin.NekoPlugin.getPlugin(com.nyarutoru.nekoplugin.NekoPlugin.class)
                    .getLogger().info("DEBUG: created previousDrawerDisplayItem for " + previousTier.name()
                            + " name: " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                                    .plainText().serialize(previousDrawerDisplayItem.getItemMeta().displayName()));
        } else {
            com.nyarutoru.nekoplugin.NekoPlugin.getPlugin(com.nyarutoru.nekoplugin.NekoPlugin.class)
                    .getLogger().info("DEBUG: previousDrawerDisplayItem meta is null!");
        }

        // Create ingredient that matches previous tier drawer specifically
        CustomRecipe.Ingredient drawerIngredient = CustomRecipe.Ingredient.ofCustomItem(
                Material.BARREL,
                getDrawerTierKey(),
                previousTier.name(),
                previousDrawerDisplayItem);

        CustomRecipe recipe = CustomRecipe.builder("drawer_tier_" + tier.getLevel())
                .category("drawer")
                .result(result)
                .shaped()
                .pattern("MMM", "MDM", "MMM",
                        Map.of(
                                'M', CustomRecipe.Ingredient.of(upgradeMaterial),
                                'D', drawerIngredient))
                .transformer((craftResult, grid) -> {
                    // Center slot (index 4) contains the drawer being upgraded
                    ItemStack oldDrawer = grid[4];
                    if (oldDrawer == null)
                        return craftResult;

                    // Read stored item data from old drawer
                    Material storedItem = getStoredItemType(oldDrawer);
                    int storedCount = getStoredItemCount(oldDrawer);

                    // If old drawer had items, create result with those items preserved
                    if (storedItem != null && storedCount > 0) {
                        return createDrawerItemWithContents(targetTier, storedItem, storedCount);
                    }

                    return craftResult;
                })
                .build();

        RecipeAPI.getInstance().registerRecipe(recipe);
    }

    public static NamespacedKey getDrawerTierKey() {
        return new NamespacedKey("nekoplugin", "drawer_tier");
    }

    public static NamespacedKey getStoredItemKey() {
        return new NamespacedKey("nekoplugin", "drawer_stored_item");
    }

    public static NamespacedKey getStoredCountKey() {
        return new NamespacedKey("nekoplugin", "drawer_stored_count");
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
}
