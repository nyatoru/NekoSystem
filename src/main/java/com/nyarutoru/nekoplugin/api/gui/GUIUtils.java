package com.nyarutoru.nekoplugin.api.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for common GUI operations and item caching.
 */
public final class GUIUtils {

    private static final Map<Material, ItemStack> glassCache = new HashMap<>();

    private GUIUtils() {
    }

    /**
     * Create or get a cached glass pane with no name.
     */
    public static ItemStack createGlassPane(Material material) {
        return glassCache.computeIfAbsent(material, mat -> {
            ItemStack pane = new ItemStack(mat);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(" "));
                pane.setItemMeta(meta);
            }
            return pane;
        });
    }

    public static ItemStack createBlackGlass() {
        return createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
    }

    public static ItemStack createGrayGlass() {
        return createGlassPane(Material.GRAY_STAINED_GLASS_PANE);
    }

    public static ItemStack createRedGlass() {
        return createGlassPane(Material.RED_STAINED_GLASS_PANE);
    }

    public static ItemStack createLimeGlass() {
        return createGlassPane(Material.LIME_STAINED_GLASS_PANE);
    }

    /**
     * Create a simple button item with name and optional lore.
     */
    public static ItemStack createButton(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(color)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }
}
