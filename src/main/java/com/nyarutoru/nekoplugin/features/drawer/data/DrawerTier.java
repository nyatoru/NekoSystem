package com.nyarutoru.nekoplugin.features.drawer.data;

import org.bukkit.Material;

/**
 * Defines the 9 upgrade tiers for drawers with exponentially increasing
 * capacity.
 */
public enum DrawerTier {
    TIER_1(1, 32, null, "Tier 1", "§7"),
    TIER_2(2, 64, Material.IRON_INGOT, "Tier 2", "§f"),
    TIER_3(3, 128, Material.GOLD_INGOT, "Tier 3", "§6"),
    TIER_4(4, 256, Material.DIAMOND, "Tier 4", "§b"),
    TIER_5(5, 512, Material.EMERALD, "Tier 5", "§a"),
    TIER_6(6, 1024, Material.NETHERITE_INGOT, "Tier 6", "§5"),
    TIER_7(7, 2048, Material.NETHER_STAR, "Tier 7", "§e"),
    TIER_8(8, 4096, Material.ECHO_SHARD, "Tier 8", "§8"),
    TIER_9(9, -1, Material.DRAGON_EGG, "Unlimited", "§c");

    private final int level;
    private final int stackCapacity;
    private final Material upgradeMaterial;
    private final String displayName;
    private final String colorCode;

    DrawerTier(int level, int stackCapacity, Material upgradeMaterial, String displayName, String colorCode) {
        this.level = level;
        this.stackCapacity = stackCapacity;
        this.upgradeMaterial = upgradeMaterial;
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public int getLevel() {
        return level;
    }

    public int getStackCapacity() {
        return stackCapacity;
    }

    public int getMaxItems() {
        return stackCapacity < 0 ? Integer.MAX_VALUE : stackCapacity * 64;
    }

    public Material getUpgradeMaterial() {
        return upgradeMaterial;
    }

    public String getDisplayName() {
        return colorCode + displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public DrawerTier getNextTier() {
        if (this.ordinal() < values().length - 1) {
            return values()[this.ordinal() + 1];
        }
        return null;
    }

    public static DrawerTier getByLevel(int level) {
        for (DrawerTier tier : values()) {
            if (tier.level == level)
                return tier;
        }
        return TIER_1;
    }

    public static DrawerTier getByName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TIER_1;
        }
    }
}
