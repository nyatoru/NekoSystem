package com.nyarutoru.nekoplugin.features.woodcutting;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Listener for wood placement on stonecutter.
 * When a log is placed on a stonecutter, it converts to planks.
 */
public class WoodPlacingListener implements Listener {

    private final Random random = new Random();

    // Hardcoded values
    private static final int MIN_DROP = 4;
    private static final int MAX_DROP = 8;

    public WoodPlacingListener(NekoPlugin plugin) {
        // No config needed - values are hardcoded
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        // Check if block below is stonecutter
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() != Material.STONECUTTER) {
            return;
        }

        // Check if placed block is a log
        if (!isLog(block.getType())) {
            return;
        }

        // Convert log to planks
        event.setCancelled(true);

        // Remove the log from player's hand
        ItemStack itemInHand = event.getItemInHand();
        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                event.getPlayer().getInventory().setItemInMainHand(null);
            }
        }

        // Drop planks
        ItemStack planks = getPlanksForLog(block.getType());
        if (planks != null) {
            int amount = getRandomDropAmount();
            planks.setAmount(amount);
            world.dropItemNaturally(block.getLocation(), planks);

            // Play sound
            block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
        }
    }

    /**
     * Check if material is a log.
     */
    private boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material) ||
                material.name().contains("_STEM") ||
                material.name().contains("_HYPHAE");
    }

    /**
     * Get the corresponding planks for a log type.
     */
    private ItemStack getPlanksForLog(Material log) {
        Material plankType = switch (log) {
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD ->
                    Material.ACACIA_PLANKS;
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD ->
                    Material.BIRCH_PLANKS;
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD ->
                    Material.SPRUCE_PLANKS;
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD ->
                    Material.DARK_OAK_PLANKS;
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD ->
                    Material.JUNGLE_PLANKS;
            case OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD ->
                    Material.OAK_PLANKS;
            case CRIMSON_STEM, CRIMSON_HYPHAE, STRIPPED_CRIMSON_STEM, STRIPPED_CRIMSON_HYPHAE ->
                    Material.CRIMSON_PLANKS;
            case WARPED_STEM, WARPED_HYPHAE, STRIPPED_WARPED_STEM, STRIPPED_WARPED_HYPHAE ->
                    Material.WARPED_PLANKS;
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD ->
                    Material.MANGROVE_PLANKS;
            case CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD ->
                    Material.CHERRY_PLANKS;
            case BAMBOO_BLOCK, STRIPPED_BAMBOO_BLOCK ->
                    Material.BAMBOO_PLANKS;
            default -> null;
        };

        if (plankType != null) {
            return new ItemStack(plankType);
        }
        return null;
    }

    /**
     * Get random drop amount between min and max.
     */
    private int getRandomDropAmount() {
        return random.nextInt(MAX_DROP - MIN_DROP + 1) + MIN_DROP;
    }
}
