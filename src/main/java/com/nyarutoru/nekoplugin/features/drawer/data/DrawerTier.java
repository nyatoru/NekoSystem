package com.nyarutoru.nekoplugin.features.drawer.data;

import org.bukkit.Material;

/**
 * Defines the 10 upgrade tiers for drawers with increasing capacity.
 * Level 10 (Nether Star) = Unlimited storage
 */
public enum DrawerTier {
    // Tier 1: 8 Chests + Barrel = basic storage
    TIER_1(1, 128, Material.CHEST, "Tier 1", "§7"),              // 8,192 items
    
    // Tier 2: 8 Leather (cheap) = moderate increase
    TIER_2(2, 512, Material.LEATHER, "Tier 2", "§6"),             // 32,768 items
    
    // Tier 3: 8 Iron Ingot = decent resources
    TIER_3(3, 1024, Material.IRON_INGOT, "Tier 3", "§f"),         // 65,536 items
    
    // Tier 4: 8 Iron Block (72 ingots) = significant investment
    TIER_4(4, 2048, Material.IRON_BLOCK, "Tier 4", "§7"),         // 131,072 items
    
    // Tier 5: 8 Gold Block (72 ingots) = rare resources
    TIER_5(5, 4096, Material.GOLD_BLOCK, "Tier 5", "§e"),         // 262,144 items
    
    // Tier 6: 8 Diamond Block (72 diamonds) = very expensive
    TIER_6(6, 8192, Material.DIAMOND_BLOCK, "Tier 6", "§b"),      // 524,288 items
    
    // Tier 7: 8 Turtle Helmet = rare mob drop
    TIER_7(7, 16384, Material.TURTLE_HELMET, "Tier 7", "§a"),     // 1,048,576 items
    
    // Tier 8: 8 Netherite Ingot = endgame resources
    TIER_8(8, 32768, Material.NETHERITE_INGOT, "Tier 8", "§5"),   // 2,097,152 items
    
    // Tier 9: 8 Netherite Block (72 netherite ingots) = massive investment
    TIER_9(9, 65536, Material.NETHERITE_BLOCK, "Tier 9", "§4"),   // 4,194,304 items
    
    // Tier 10: 8 Nether Star = boss drops, unlimited
    TIER_10(10, -1, Material.NETHER_STAR, "Unlimited", "§c");     // Unlimited

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
