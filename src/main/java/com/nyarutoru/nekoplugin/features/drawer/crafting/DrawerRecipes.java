package com.nyarutoru.nekoplugin.features.drawer.crafting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
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
import java.util.List;

/**
 * Handles creation and registration of drawer crafting recipes.
 */
public class DrawerRecipes {

    private final NekoPlugin plugin;

    public DrawerRecipes(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        registerBaseDrawerRecipe();

        for (DrawerTier tier : DrawerTier.values()) {
            if (tier == DrawerTier.TIER_1)
                continue;
            registerUpgradeRecipe(tier);
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

    private void registerBaseDrawerRecipe() {
        ItemStack result = createDrawerItem(DrawerTier.TIER_1);
        NamespacedKey key = new NamespacedKey(plugin, "drawer_tier_1");

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("PPP", "PBP", "PPP");
        recipe.setIngredient('P', Material.OAK_PLANKS);
        recipe.setIngredient('B', Material.BARREL);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerUpgradeRecipe(DrawerTier tier) {
        Material upgradeMaterial = tier.getUpgradeMaterial();
        if (upgradeMaterial == null)
            return;

        ItemStack result = createDrawerItem(tier);
        NamespacedKey key = new NamespacedKey(plugin, "drawer_tier_" + tier.getLevel());

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("MMM", "MDM", "MMM");
        recipe.setIngredient('M', upgradeMaterial);
        recipe.setIngredient('D', Material.BARREL);

        plugin.getServer().addRecipe(recipe);
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
