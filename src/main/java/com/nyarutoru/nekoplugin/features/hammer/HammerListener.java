package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles Hammer 3x3 mining and anvil restriction.
 */
public class HammerListener implements Listener {

    private final NekoPlugin plugin;

    // Track blocks being broken to prevent recursion
    private final Set<Location> breakingBlocks = new HashSet<>();

    // Blocks that can be mined with a pickaxe
    private static final Set<Material> MINEABLE = Set.of(
            // Stone types
            Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.POLISHED_GRANITE, Material.POLISHED_DIORITE, Material.POLISHED_ANDESITE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
            Material.CALCITE, Material.TUFF, Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE,
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
            Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES,
            Material.CHISELED_DEEPSLATE, Material.INFESTED_STONE,
            // Netherrack and basalt
            Material.NETHERRACK, Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
            Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.CHISELED_POLISHED_BLACKSTONE,
            Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Material.GILDED_BLACKSTONE,
            // End stone
            Material.END_STONE, Material.END_STONE_BRICKS,
            // Sandstone
            Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE,
            Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, Material.CUT_RED_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE,
            // Terracotta
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA,
            Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA,
            // Concrete
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
            Material.BLACK_CONCRETE,
            // Ores
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,
            // Raw ore blocks
            Material.RAW_IRON_BLOCK, Material.RAW_COPPER_BLOCK, Material.RAW_GOLD_BLOCK,
            // Ice
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            // Prismarine
            Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
            // Obsidian
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            // Bricks
            Material.BRICKS, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
            Material.CHISELED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS,
            // Quartz
            Material.QUARTZ_BLOCK, Material.CHISELED_QUARTZ_BLOCK, Material.QUARTZ_BRICKS,
            Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ,
            // Purpur
            Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
            // Amethyst
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            // Misc
            Material.GLOWSTONE, Material.MAGMA_BLOCK, Material.BONE_BLOCK,
            Material.LODESTONE, Material.RESPAWN_ANCHOR);

    public HammerListener(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!HammerRecipes.isHammer(tool))
            return;

        Block center = event.getBlock();

        // Prevent recursion
        if (breakingBlocks.contains(center.getLocation()))
            return;

        // Check if block is mineable
        if (!isMineable(center.getType()))
            return;

        // Get the face the player is looking at to determine 3x3 plane
        BlockFace face = getTargetBlockFace(player);

        breakingBlocks.add(center.getLocation());

        try {
            // Mine 3x3 area
            mine3x3(player, center, face, tool);
        } finally {
            breakingBlocks.remove(center.getLocation());
        }
    }

    private void mine3x3(Player player, Block center, BlockFace face, ItemStack hammer) {
        int[][] offsets = get3x3Offsets(face);
        Location centerLoc = center.getLocation();

        // Check durability once at the start - hammer uses 1 durability per 3x3 swing
        if (!ItemUtils.isUnbreakable(hammer) && ItemUtils.wouldBreakFromDamage(hammer, 1)) {
            return; // Don't break any extra blocks if hammer would break
        }

        boolean brokeAnyBlock = false;

        for (int[] offset : offsets) {
            // Skip center block (already being broken)
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0)
                continue;

            Block target = centerLoc.clone().add(offset[0], offset[1], offset[2]).getBlock();

            // Skip if not mineable or air
            if (target.getType() == Material.AIR || !isMineable(target.getType()))
                continue;

            // Skip if already being broken
            if (breakingBlocks.contains(target.getLocation()))
                continue;

            // Break block with drops
            breakingBlocks.add(target.getLocation());
            target.breakNaturally(hammer);
            breakingBlocks.remove(target.getLocation());
            brokeAnyBlock = true;
        }

        // Apply only 1 durability for the entire 3x3 operation (if any blocks were
        // broken)
        if (brokeAnyBlock) {
            ItemUtils.applyDurabilityDamage(hammer, 1);
        }
    }

    private int[][] get3x3Offsets(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new int[][] {
                    { -1, 0, -1 }, { 0, 0, -1 }, { 1, 0, -1 },
                    { -1, 0, 0 }, { 0, 0, 0 }, { 1, 0, 0 },
                    { -1, 0, 1 }, { 0, 0, 1 }, { 1, 0, 1 }
            };
            case NORTH, SOUTH -> new int[][] {
                    { -1, -1, 0 }, { 0, -1, 0 }, { 1, -1, 0 },
                    { -1, 0, 0 }, { 0, 0, 0 }, { 1, 0, 0 },
                    { -1, 1, 0 }, { 0, 1, 0 }, { 1, 1, 0 }
            };
            case EAST, WEST -> new int[][] {
                    { 0, -1, -1 }, { 0, -1, 0 }, { 0, -1, 1 },
                    { 0, 0, -1 }, { 0, 0, 0 }, { 0, 0, 1 },
                    { 0, 1, -1 }, { 0, 1, 0 }, { 0, 1, 1 }
            };
            default -> new int[][] { { 0, 0, 0 } };
        };
    }

    private BlockFace getTargetBlockFace(Player player) {
        // Get the direction the player is facing
        float pitch = player.getLocation().getPitch();
        float yaw = player.getLocation().getYaw();

        // Looking up/down (mining floor/ceiling)
        if (pitch < -45)
            return BlockFace.UP;
        if (pitch > 45)
            return BlockFace.DOWN;

        // Normalize yaw
        yaw = (yaw % 360 + 360) % 360;

        // Cardinal directions
        if (yaw >= 315 || yaw < 45)
            return BlockFace.SOUTH;
        if (yaw >= 45 && yaw < 135)
            return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225)
            return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private boolean isMineable(Material material) {
        return MINEABLE.contains(material);
    }

    /**
     * Prevent anvil upgrades for hammers.
     */
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);

        // If either item is a hammer, cancel the result
        if (HammerRecipes.isHammer(first) || HammerRecipes.isHammer(second)) {
            event.setResult(null);
        }
    }
}
