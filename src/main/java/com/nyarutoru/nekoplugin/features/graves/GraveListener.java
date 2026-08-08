package com.nyarutoru.nekoplugin.features.graves;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
        Grave grave = manager.get(event.getBlock().getLocation());
        if (grave == null) return;
        event.setCancelled(true);
        if (!manager.canAccess(event.getPlayer(), grave)) {
            event.getPlayer().sendMessage(Component.text("You cannot break this grave.", NamedTextColor.RED));
            return;
        }
        event.getPlayer().sendMessage(Component.text("Open the grave to retrieve its contents.", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.isGrave(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.isGrave(block.getLocation()));
    }
}
