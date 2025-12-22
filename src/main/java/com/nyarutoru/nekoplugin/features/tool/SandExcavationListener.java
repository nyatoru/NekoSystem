package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles Sand Excavation events using ActiveToolAPI.
 * Allows mass mining of sand and gravel with shovels.
 */
public class SandExcavationListener implements Listener {

    public static final String TOOL_NAME = "Sand Excavation";
    private static final int MAX_BLOCKS = 250;

    // Excavatable materials
    private static final Set<Material> EXCAVATABLE = Set.of(
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL);

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;

        Player player = event.getPlayer();

        ActiveToolAPI.getInstance().onShift(
                player,
                TOOL_NAME,
                this::isHoldingShovel,
                null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME))
            return;

        if (!isHoldingShovel(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "no shovel");
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!isExcavatable(blockType))
            return;

        // Excavate connected blocks
        excavate(player, block.getLocation(), blockType);
    }

    private boolean isHoldingShovel(Player player) {
        return ItemUtils.isShovel(player.getInventory().getItemInMainHand());
    }

    private boolean isExcavatable(Material material) {
        return EXCAVATABLE.contains(material);
    }

    private void excavate(Player player, Location origin, Material blockType) {
        ItemStack shovel = player.getInventory().getItemInMainHand();

        Set<Location> visited = new HashSet<>();
        Deque<Location> toCheck = new ArrayDeque<>();
        List<Block> blocksToBreak = new ArrayList<>();

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find all connected blocks of the same type
        while (!toCheck.isEmpty() && blocksToBreak.size() < MAX_BLOCKS) {
            Location current = toCheck.poll();
            Block block = current.getBlock();

            // Only include blocks of the same type as the original
            if (block.getType() != blockType)
                continue;

            blocksToBreak.add(block);

            // Check 6 adjacent directions (up, down, north, south, east, west)
            int[][] offsets = {
                    { 0, 1, 0 }, { 0, -1, 0 }, // up, down
                    { 1, 0, 0 }, { -1, 0, 0 }, // east, west
                    { 0, 0, 1 }, { 0, 0, -1 } // south, north
            };

            for (int[] offset : offsets) {
                Location adjacent = current.clone().add(offset[0], offset[1], offset[2]);
                if (!visited.contains(adjacent)) {
                    visited.add(adjacent);
                    Block adjBlock = adjacent.getBlock();
                    if (adjBlock.getType() == blockType) {
                        toCheck.add(adjacent);
                    }
                }
            }
        }

        // Break blocks (skip origin as it's broken by the event)
        for (Block block : blocksToBreak) {
            if (block.getLocation().equals(origin))
                continue;

            ItemStack currentShovel = player.getInventory().getItemInMainHand();
            if (currentShovel.getType() != shovel.getType())
                break;

            // Check and consume durability
            if (!ItemUtils.consumeDurabilityOrDeactivate(player, currentShovel, 1, TOOL_NAME)) {
                break;
            }

            // Drop items at origin for easy collection
            for (ItemStack drop : block.getDrops(currentShovel)) {
                origin.getWorld().dropItemNaturally(origin, drop);
            }

            block.setType(Material.AIR);
        }
    }
}
