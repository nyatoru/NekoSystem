package com.nyarutoru.nekoplugin.features.oreexcavation;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.features.hammer.HammerRecipes;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles ore excavation events using ActiveToolAPI.
 */
public class OreExcavationListener implements Listener {

    public static final String TOOL_NAME = "Ore Excavation";
    private static final int RADIUS = 8;

    // Ores that can be vein-mined
    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS);

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;

        Player player = event.getPlayer();

        // Use ActiveToolAPI for shift activation
        ActiveToolAPI.getInstance().onShift(
                player,
                TOOL_NAME,
                this::isHoldingValidPickaxe,
                null // No special activation callback needed
        );
    }

    /**
     * Checks if player is holding a valid pickaxe (not a hammer).
     */
    private boolean isHoldingValidPickaxe(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        // Must be a pickaxe but NOT a hammer
        return ItemUtils.isPickaxe(item) && !HammerRecipes.isHammer(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Check if Ore Excavation is active via ActiveToolAPI
        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME))
            return;

        if (!isHoldingValidPickaxe(player)) {
            ActiveToolAPI.getInstance().deactivate(player, "no pickaxe");
            return;
        }

        Block block = event.getBlock();
        Material oreType = block.getType();

        if (!isOre(oreType))
            return;

        // Perform vein mining (don't deactivate after mining)
        veinMine(player, block.getLocation(), oreType);
    }

    private boolean isOre(Material material) {
        return ORES.contains(material);
    }

    private void veinMine(Player player, Location origin, Material oreType) {
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        boolean hasSilkTouch = pickaxe.containsEnchantment(Enchantment.SILK_TOUCH);

        Set<Location> visited = new HashSet<>();
        Queue<Location> toCheck = new LinkedList<>();
        List<Block> toBreak = new ArrayList<>();

        toCheck.add(origin);
        visited.add(origin);

        // BFS to find connected ores within radius
        while (!toCheck.isEmpty()) {
            Location current = toCheck.poll();

            if (current.distance(origin) > RADIUS)
                continue;

            Block block = current.getBlock();
            if (block.getType() != oreType)
                continue;

            toBreak.add(block);

            // Check adjacent blocks (3D diagonal)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0)
                            continue;

                        Location adjacent = current.clone().add(dx, dy, dz);
                        if (!visited.contains(adjacent) && adjacent.distance(origin) <= RADIUS) {
                            visited.add(adjacent);
                            if (adjacent.getBlock().getType() == oreType) {
                                toCheck.add(adjacent);
                            }
                        }
                    }
                }
            }
        }

        // Break all found ores (skip origin since it's already broken by the event)
        int broken = 0;
        for (Block oreBlock : toBreak) {
            if (oreBlock.getLocation().equals(origin))
                continue;

            ItemStack currentPickaxe = player.getInventory().getItemInMainHand();
            if (currentPickaxe.getType() != pickaxe.getType()) {
                // Tool changed - ActiveToolAPI will handle deactivation
                break;
            }

            // Check if pickaxe would break
            if (!ItemUtils.isUnbreakable(currentPickaxe) &&
                    ItemUtils.wouldBreakFromDamage(currentPickaxe, 1)) {
                // Deactivate when tool breaks
                ActiveToolAPI.getInstance().deactivate(player, "tool broke");
                break;
            }

            // Apply durability damage with Unbreaking support
            ItemUtils.applyDurabilityDamage(currentPickaxe, 1);

            // Break block with proper drops at origin location
            if (hasSilkTouch) {
                origin.getWorld().dropItemNaturally(origin, new ItemStack(oreType));
                oreBlock.setType(Material.AIR);
            } else {
                // Get drops and spawn at origin
                for (ItemStack drop : oreBlock.getDrops(currentPickaxe)) {
                    origin.getWorld().dropItemNaturally(origin, drop);
                }
                oreBlock.setType(Material.AIR);
            }

            broken++;
        }
    }
}
