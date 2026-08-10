package com.nyarutoru.nekoplugin.features.magnet;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MagnetFeature extends AbstractFeature {

    private MagnetListener listener;
    private final List<NamespacedKey> bukkitKeys = new ArrayList<>();
    private final List<String> customIds = new ArrayList<>();

    public MagnetFeature() {
        super("magnet", "Magnet");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        if (listener == null) listener = new MagnetListener();
        registerListener(listener, plugin);
        super.onEnable(plugin);
        registerRecipes(plugin);
        listener.start();
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        ActiveToolAPI.getInstance().registerSettings(registry, state, getId());
        if (listener == null) listener = new MagnetListener();
        listener.registerSettings(registry, state);
    }

    @Override
    protected void cleanup() {
        if (listener != null) listener.stop();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ActiveToolAPI.getInstance().cleanupTool(player, MagnetListener.TOOL_NAME);
        }
        NekoPlugin plugin = NekoPlugin.getInstance();
        if (plugin != null) {
            for (NamespacedKey key : bukkitKeys) {
                try {
                    plugin.getServer().removeRecipe(key);
                } catch (Throwable ignored) {}
            }
        }
        bukkitKeys.clear();
        for (String id : customIds) {
            RecipeAPI.getInstance().unregisterRecipe(id);
        }
        customIds.clear();
    }

    private void registerRecipes(NekoPlugin plugin) {
        for (NamespacedKey key : bukkitKeys) {
            try {
                plugin.getServer().removeRecipe(key);
            } catch (Throwable ignored) {}
        }
        bukkitKeys.clear();
        for (String id : customIds) {
            RecipeAPI.getInstance().unregisterRecipe(id);
        }
        customIds.clear();

        ItemStack result = MagnetListener.createMagnetItem();
        NamespacedKey key = new NamespacedKey(plugin, "magnet");
        plugin.getServer().removeRecipe(key);
        // Slots: 1=REDSTONE_BLOCK 2=air 3=LAPIS 4=IRON_BLOCK 5=air 6=IRON_BLOCK 7=IRON_BLOCK 8=air 9=IRON_BLOCK
        // Pattern: "R L" / "I I" / "I I"
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("R L", "I I", "I I");
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('L', Material.LAPIS_BLOCK);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        if (plugin.getServer().addRecipe(recipe)) {
            bukkitKeys.add(key);
        }

        CustomRecipe custom = CustomRecipe.builder("magnet")
                .category("magnet")
                .result(result)
                .shaped()
                .pattern("R L", "I I", "I I", Map.of(
                        'R', CustomRecipe.Ingredient.of(Material.REDSTONE_BLOCK),
                        'L', CustomRecipe.Ingredient.of(Material.LAPIS_BLOCK),
                        'I', CustomRecipe.Ingredient.of(Material.IRON_BLOCK)))
                .build();
        RecipeAPI.getInstance().registerRecipe(custom);
        customIds.add("magnet");
    }

    public MagnetListener getListener() {
        return listener;
    }
}
