package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Consolidated Server Listener for all server-side block interactions.
 * - Deepslate instant break with Netherite Pickaxe + Efficiency 5 + Haste
 * - Glass instant break with Netherite Pickaxe
 * - Ladder auto-placement up/down
 * - Anvil repair with Iron Block
 */
public class ServerListener implements Listener {

    private final NekoPlugin plugin;

    // Deepslate blocks that can be instant-mined
    private static final Set<Material> DEEPSLATE_BLOCKS = Set.of(
            Material.DEEPSLATE,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            Material.COBBLED_DEEPSLATE,
            Material.POLISHED_DEEPSLATE,
            Material.CHISELED_DEEPSLATE,
            Material.CRACKED_DEEPSLATE_BRICKS,
            Material.CRACKED_DEEPSLATE_TILES,
            Material.DEEPSLATE_BRICK_SLAB,
            Material.DEEPSLATE_TILE_SLAB,
            Material.COBBLED_DEEPSLATE_SLAB,
            Material.POLISHED_DEEPSLATE_SLAB,
            Material.DEEPSLATE_BRICK_STAIRS,
            Material.DEEPSLATE_TILE_STAIRS,
            Material.COBBLED_DEEPSLATE_STAIRS,
            Material.POLISHED_DEEPSLATE_STAIRS,
            Material.DEEPSLATE_BRICK_WALL,
            Material.DEEPSLATE_TILE_WALL,
            Material.COBBLED_DEEPSLATE_WALL,
            Material.POLISHED_DEEPSLATE_WALL);

    // Glass blocks that can be instant-mined
    private static final Set<Material> GLASS_BLOCKS = Set.of(
            Material.GLASS,
            Material.GLASS_PANE,
            Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS, Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS, Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS, Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS, Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS, Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS, Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS, Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS, Material.BLACK_STAINED_GLASS_PANE,
            Material.TINTED_GLASS);

    public ServerListener(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    // ========== Instant Break ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Glass: just needs Netherite Pickaxe - drop the glass
        if (GLASS_BLOCKS.contains(blockType)) {
            if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                event.setDropItems(false);
                event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(blockType));
            }
            return;
        }

        // Deepslate: needs Efficiency 5 + Haste 2
        // This event fires after the block is broken, so no action needed here
        // The instant break effect is handled in BlockDamageEvent
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Only deepslate blocks
        if (!DEEPSLATE_BLOCKS.contains(blockType))
            return;

        // Check Efficiency 5
        int effLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (effLevel < 5)
            return;

        // Check Haste 2
        var hasteEffect = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteEffect == null || hasteEffect.getAmplifier() < 1)
            return; // Amplifier 1 = Haste 2

        // Instant break - set to insta-break mode
        event.setInstaBreak(true);
    }

    // ========== Ladder Auto-Placement ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onLadderInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;

        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.LADDER)
            return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Must be holding ladders
        if (held.getType() != Material.LADDER)
            return;

        // Determine direction based on player pitch
        BlockFace direction = player.getLocation().getPitch() > 0 ? BlockFace.DOWN : BlockFace.UP;
        Block target = clicked.getRelative(direction);

        // Find the end of the ladder chain
        while (target.getType() == Material.LADDER) {
            target = target.getRelative(direction);
        }

        // Check if we can place a ladder
        if (target.getType() != Material.AIR)
            return;

        // Get ladder facing from the original clicked ladder
        if (!(clicked.getBlockData() instanceof Directional ladderData))
            return;
        BlockFace facing = ladderData.getFacing();

        // Check if there's a solid block behind where we want to place
        Block behind = target.getRelative(facing.getOppositeFace());
        if (!behind.getType().isSolid())
            return;

        // Place the ladder
        event.setCancelled(true);
        target.setType(Material.LADDER);
        Directional newLadderData = (Directional) target.getBlockData();
        newLadderData.setFacing(facing);
        target.setBlockData(newLadderData);

        // Consume item
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    // ========== Anvil Repair ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilRepair(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;

        Block clicked = event.getClickedBlock();
        Material anvilType = clicked.getType();

        // Check if it's a damaged anvil
        if (anvilType != Material.CHIPPED_ANVIL && anvilType != Material.DAMAGED_ANVIL)
            return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Must be holding Iron Block
        if (held.getType() != Material.IRON_BLOCK)
            return;

        // Repair the anvil
        event.setCancelled(true);

        Material repairedType = (anvilType == Material.DAMAGED_ANVIL)
                ? Material.CHIPPED_ANVIL
                : Material.ANVIL;

        clicked.setType(repairedType);

        // Consume iron block
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }

        player.sendMessage(Component.text("✓ Anvil repaired!")
                .color(NamedTextColor.GREEN));
    }
}
