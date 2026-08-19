package com.nyarutoru.nekoplugin.features.shutup;

import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
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
 * Creates the three "Shut Up" furnace-based items and their crafting recipes.
 */
public final class ShutUpItems {

    public static final NamespacedKey TYPE_KEY = new NamespacedKey("nekoplugin", "shutup_type");

    private final List<String> registeredRecipeIds = new ArrayList<>();

    public static ItemStack createItem(ShutUpType type) {
        ItemStack item = new ItemStack(Material.FURNACE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        ItemUtils.setCustomModelData(meta, type.customModelData());
        meta.displayName(Component.text(type.displayName())
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Silences " + type.targetName() + " sounds")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("in a 33x33 area when placed")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Place to activate")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public static ShutUpType getTypeFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.FURNACE) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String name = meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
        return name == null ? null : ShutUpType.getByName(name);
    }

    public void registerAll() {
        unregisterAll();
        for (ShutUpType type : ShutUpType.values()) {
            register(type);
        }
    }

    public void unregisterAll() {
        for (String id : List.copyOf(registeredRecipeIds)) {
            RecipeAPI.getInstance().unregisterRecipe(id);
        }
        registeredRecipeIds.clear();
    }

    private void register(ShutUpType type) {
        ItemStack result = createItem(type);
        Material ring = switch (type) {
            case ENDERMAN -> Material.PURPUR_BLOCK;
            case GUARDIAN -> Material.PRISMARINE;
            case IRON_GOLEM -> Material.IRON_BLOCK;
        };
        Material center = switch (type) {
            case ENDERMAN -> Material.ENDER_PEARL;
            case GUARDIAN -> Material.PRISMARINE_CRYSTALS;
            case IRON_GOLEM -> Material.CARVED_PUMPKIN;
        };
        CustomRecipe recipe = CustomRecipe.builder("shutup_" + type.name().toLowerCase())
                .category("shutup")
                .result(result)
                .shaped()
                .pattern("XXX", "XCX", "XXX",
                        Map.of(
                                'X', CustomRecipe.Ingredient.of(ring),
                                'C', CustomRecipe.Ingredient.of(center)))
                .build();
        RecipeAPI.getInstance().registerRecipe(recipe);
        registeredRecipeIds.add(recipe.getId());
    }
}
