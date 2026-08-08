package com.nyarutoru.nekoplugin.features.graves;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class GraveListener implements Listener {
    private final GraveManager manager;

    public GraveListener(GraveManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;
        Player player = event.getEntity();
        List<ItemStack> drops = event.getDrops().stream().filter(item -> item != null && !item.isEmpty()).map(ItemStack::clone).toList();
        int experience = Math.max(0, event.getDroppedExp());
        Grave grave = manager.create(player, player.getLocation(), drops, experience);
        if (grave == null) {
            player.sendMessage(Component.text("A grave could not be created; your items will drop normally.", NamedTextColor.RED));
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        GravePosition position = grave.getGravePosition();
        player.sendMessage(Component.text("Your grave is at " + position.worldName() + " (" + position.x() + ", " + position.y() + ", " + position.z() + ").", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD) return;
        Grave grave = manager.get(block.getLocation());
        if (grave == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!manager.canAccess(player, grave)) {
            player.sendMessage(Component.text("You cannot access this grave.", NamedTextColor.RED));
            return;
        }
        if (!manager.acquireViewer(grave)) {
            player.sendMessage(Component.text("This grave is already open.", NamedTextColor.RED));
            return;
        }
        new GraveGUI(manager, grave, player).open(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isProtected(block)) return;
        event.setCancelled(true);
        Grave grave = manager.get(block.getLocation());
        if (grave == null) {
            event.getPlayer().sendMessage(Component.text("The block supporting this grave is protected.", NamedTextColor.RED));
            return;
        }
        if (!manager.canAccess(event.getPlayer(), grave)) {
            event.getPlayer().sendMessage(Component.text("You cannot break this grave.", NamedTextColor.RED));
            return;
        }
        event.getPlayer().sendMessage(Component.text("Open the grave to retrieve its contents.", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isProtected(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesProtectedBlock(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesProtectedBlock(event.getBlocks(), event.getDirection().getOppositeFace())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDestroy(BlockDestroyEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (isGraveMarker(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.reconcileDisplays(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        manager.unloadDisplays(event.getEntities());
    }

    private boolean movesProtectedBlock(List<Block> blocks, BlockFace movement) {
        return blocks.stream().anyMatch(block -> isProtected(block) || isProtected(block.getRelative(movement)));
    }

    private boolean isProtected(Block block) {
        return isGraveMarker(block) || manager.isGrave(block.getRelative(BlockFace.UP).getLocation());
    }

    private boolean isGraveMarker(Block block) {
        return manager.isGrave(block.getLocation());
    }
}
