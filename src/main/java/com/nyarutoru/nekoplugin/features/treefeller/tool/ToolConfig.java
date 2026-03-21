package com.nyarutoru.nekoplugin.features.treefeller.tool;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Represents a configured tool that can activate tree felling.
 * <p>
 * Tools are matched based on material type and optional enchantment requirements.
 * Each tool has a durability cost that is applied when felling a tree.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public class ToolConfig {

    /**
     * The name of this tool configuration (for identification and debugging).
     */
    private final String name;

    /**
     * The material type required for this tool.
     */
    private final Material material;

    /**
     * The durability cost applied when felling a tree with this tool.
     */
    private final int durabilityCost;

    /**
     * Map of required enchantments (enchantment -> minimum level).
     * If null, no specific enchantments are required.
     */
    private final Map<Enchantment, Integer> requiredEnchantments;

    /**
     * Map of forbidden enchantments (enchantment -> any level).
     * If null, no enchantments are forbidden.
     */
    private final Map<Enchantment, Integer> forbiddenEnchantments;

    /**
     * Creates a new tool configuration.
     *
     * @param name the name of this tool configuration
     * @param material the material type required
     * @param durabilityCost the durability cost when felling a tree
     * @param requiredEnchantments map of required enchantments (can be null)
     * @param forbiddenEnchantments map of forbidden enchantments (can be null)
     */
    public ToolConfig(String name, Material material, int durabilityCost,
                      Map<Enchantment, Integer> requiredEnchantments,
                      Map<Enchantment, Integer> forbiddenEnchantments) {
        this.name = name;
        this.material = material;
        this.durabilityCost = durabilityCost;
        this.requiredEnchantments = requiredEnchantments;
        this.forbiddenEnchantments = forbiddenEnchantments;
    }

    /**
     * Gets the name of this tool configuration.
     *
     * @return the tool name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the material type required for this tool.
     *
     * @return the required material
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Gets the durability cost applied when felling a tree.
     *
     * @return the durability cost
     */
    public int getDurabilityCost() {
        return durabilityCost;
    }

    /**
     * Gets the map of required enchantments.
     *
     * @return the required enchantments map, or null if none required
     */
    public Map<Enchantment, Integer> getRequiredEnchantments() {
        return requiredEnchantments;
    }

    /**
     * Gets the map of forbidden enchantments.
     *
     * @return the forbidden enchantments map, or null if none forbidden
     */
    public Map<Enchantment, Integer> getForbiddenEnchantments() {
        return forbiddenEnchantments;
    }

    /**
     * Checks if the given item stack matches this tool configuration.
     * <p>
     * Matching is based on material type and enchantment requirements.
     *
     * @param item the item stack to check
     * @return true if the item matches this tool configuration, false otherwise
     */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != material) {
            return false;
        }

        // Check required enchantments
        if (requiredEnchantments != null) {
            for (Map.Entry<Enchantment, Integer> entry : requiredEnchantments.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int requiredLevel = entry.getValue();
                int actualLevel = item.getEnchantmentLevel(enchantment);
                if (actualLevel < requiredLevel) {
                    return false;
                }
            }
        }

        // Check forbidden enchantments
        if (forbiddenEnchantments != null) {
            for (Enchantment enchantment : forbiddenEnchantments.keySet()) {
                if (item.containsEnchantment(enchantment)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "ToolConfig{" +
                "name='" + name + '\'' +
                ", material=" + material +
                ", durabilityCost=" + durabilityCost +
                '}';
    }
}
