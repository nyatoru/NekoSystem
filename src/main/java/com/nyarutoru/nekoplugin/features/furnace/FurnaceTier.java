package com.nyarutoru.nekoplugin.features.furnace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

/**
 * Defines the 10 upgrade tiers for the Upgrade Furnace.
 * Tier 0 (plain vanilla furnace) smelts at 1x; tier N smelts at speedMultiplier x,
 * with a steeper buff from tier 6 onward.
 */
public enum FurnaceTier {
    TIER_1(1, 2, Material.STONE, "Tier 1", NamedTextColor.GRAY),
    TIER_2(2, 3, Material.SMOOTH_STONE, "Tier 2", NamedTextColor.WHITE),
    TIER_3(3, 5, Material.IRON_INGOT, "Tier 3", NamedTextColor.GOLD),
    TIER_4(4, 8, Material.IRON_BLOCK, "Tier 4", NamedTextColor.AQUA),
    TIER_5(5, 12, Material.GOLD_BLOCK, "Tier 5", NamedTextColor.GREEN),
    TIER_6(6, 18, Material.DIAMOND_BLOCK, "Tier 6", NamedTextColor.DARK_PURPLE),
    TIER_7(7, 25, Material.TURTLE_HELMET, "Tier 7", NamedTextColor.DARK_RED),
    TIER_8(8, 40, Material.NETHERITE_INGOT, "Tier 8", NamedTextColor.RED),
    TIER_9(9, 60, Material.NETHERITE_BLOCK, "Tier 9", NamedTextColor.LIGHT_PURPLE),
    TIER_10(10, 100, Material.NETHER_STAR, "Tier 10", NamedTextColor.YELLOW);

    private final int level;
    private final int speedMultiplier;
    private final Material upgradeMaterial;
    private final String displayName;
    private final NamedTextColor color;

    FurnaceTier(int level, int speedMultiplier, Material upgradeMaterial, String displayName, NamedTextColor color) {
        this.level = level;
        this.speedMultiplier = speedMultiplier;
        this.upgradeMaterial = upgradeMaterial;
        this.displayName = displayName;
        this.color = color;
    }

    public static FurnaceTier getByLevel(int level) {
        for (FurnaceTier tier : values()) {
            if (tier.level == level)
                return tier;
        }
        return TIER_1;
    }

    public static FurnaceTier getByName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TIER_1;
        }
    }

    public int getLevel() {
        return level;
    }

    /** Smelting speed multiplier relative to a vanilla furnace (tier 0 = 1x). */
    public int getSpeedMultiplier() {
        return speedMultiplier;
    }

    public Material getUpgradeMaterial() {
        return upgradeMaterial;
    }

    public Component getDisplayNameComponent() {
        return Component.text(displayName)
                .color(color)
                .decoration(TextDecoration.ITALIC, false);
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public FurnaceTier getNextTier() {
        if (this.ordinal() < values().length - 1) {
            return values()[this.ordinal() + 1];
        }
        return null;
    }

    public FurnaceTier getPreviousTier() {
        if (this.ordinal() > 0) {
            return values()[this.ordinal() - 1];
        }
        return null;
    }
}
