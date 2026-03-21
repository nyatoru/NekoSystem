package com.nyarutoru.nekoplugin.features.treefeller.tool;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import org.bukkit.inventory.ItemStack;

/**
 * Matches items against configured tool definitions.
 * <p>
 * The matcher checks if a player's held item matches any configured tool
 * based on material type and enchantment requirements.
 *
 * @author Redstone Agents
 * @since 2026-03-21
 */
public final class ToolMatcher {

    /**
     * Finds a matching tool configuration for the given item.
     * <p>
     * Iterates through all configured tools and returns the first match.
     *
     * @param item the item stack to match
     * @return the matching tool configuration, or null if no match found
     */
    public ToolConfig match(ItemStack item) {
        if (item == null) {
            return null;
        }

        for (ToolConfig tool : TreeFellerConfig.TOOLS) {
            if (tool.matches(item)) {
                return tool;
            }
        }

        return null;
    }

    /**
     * Checks if the given item can activate tree felling.
     * <p>
     * Convenience method that returns true if any tool matches.
     *
     * @param item the item stack to check
     * @return true if the item can activate tree felling, false otherwise
     */
    public boolean isValidTool(ItemStack item) {
        return match(item) != null;
    }
}
