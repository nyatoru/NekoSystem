package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Unified Player Feature Listener.
 * Handles: Pet Carrying, AFK System, Auto Item Replenishment
 */
public class PlayerFeatureListener implements Listener {

    private static final long AFK_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final String AFK_METADATA_KEY = "neko_afk";

    // ========== Auto Replenish ==========
    private static final Set<Material> FOODS = Set.of(
            Material.APPLE, Material.BAKED_POTATO, Material.BEEF, Material.BEETROOT,
            Material.BREAD, Material.CARROT, Material.CHICKEN, Material.COD,
            Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_COD,
            Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_RABBIT,
            Material.COOKED_SALMON, Material.COOKIE, Material.DRIED_KELP,
            Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE, Material.GOLDEN_CARROT,
            Material.MELON_SLICE, Material.MUTTON, Material.PORKCHOP, Material.POTATO,
            Material.PUMPKIN_PIE, Material.RABBIT, Material.SALMON, Material.SWEET_BERRIES,
            Material.GLOW_BERRIES);

    private final NekoPlugin plugin;

    // ========== AFK System ==========
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Boolean> afkStatus = new HashMap<>();
    // Store original display names and player list names to restore when player
    // returns from AFK
    private final Map<UUID, Component> originalDisplayNames = new HashMap<>();
    private final Map<UUID, Component> originalPlayerListNames = new HashMap<>();
    // Store last known location for head rotation tracking
    private final Map<UUID, Location> lastKnownLocation = new HashMap<>();
    private NamespacedKey afkKey;

    public PlayerFeatureListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.afkKey = new NamespacedKey(plugin, AFK_METADATA_KEY);
    }

    public void start() {
        // Start AFK check with optimized interval
        SchedulerUtils.runGlobalTimer(this::checkAfkPlayers, 20 * 30, 20 * 30);

        // Initialize online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateActivity(player);
            lastKnownLocation.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    public void stop() {
        // Restore all AFK players' display names
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (afkStatus.getOrDefault(player.getUniqueId(), false)) {
                restoreDisplayName(player);
            }
        }
    }

    // ==================== AFK SYSTEM ====================

    private void checkAfkPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long last = lastActivity.get(uuid);
            if (last == null) {
                updateActivity(player);
                continue;
            }

            boolean wasAfk = afkStatus.getOrDefault(uuid, false);
            if ((now - last) >= AFK_TIMEOUT_MS && !wasAfk) {
                setAfk(player, true);
            }
        }
    }

    public void updateActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        lastKnownLocation.put(player.getUniqueId(), player.getLocation().clone());
        if (afkStatus.getOrDefault(player.getUniqueId(), false)) {
            setAfk(player, false);
        }
    }

    private void setAfk(Player player, boolean afk) {
        afkStatus.put(player.getUniqueId(), afk);

        if (afk) {
            // Store original display name and player list name
            originalDisplayNames.put(player.getUniqueId(), player.displayName());
            originalPlayerListNames.put(player.getUniqueId(), player.playerListName());

            // Set AFK prefix for both display name and tablist
            Component afkName = Component.text("[AFK] ").color(NamedTextColor.GRAY)
                    .append(player.displayName().color(NamedTextColor.GRAY));

            // Update display name
            player.displayName(afkName);

            // Update player list name (tablist) - FOLIA FIX
            SchedulerUtils.runAtEntity(player, () -> {
                player.playerListName(afkName);
            });

            // Set metadata for AI pathfinding optimization
            player.getPersistentDataContainer().set(afkKey, PersistentDataType.BYTE, (byte) 1);

            Bukkit.broadcast(Component.text(player.getName() + " is now AFK").color(NamedTextColor.GRAY));
        } else {
            restoreDisplayName(player);

            // Remove metadata
            player.getPersistentDataContainer().remove(afkKey);

            Bukkit.broadcast(Component.text(player.getName() + " is no longer AFK").color(NamedTextColor.GREEN));
        }
    }

    private void restoreDisplayName(Player player) {
        Component originalDisplay = originalDisplayNames.remove(player.getUniqueId());
        Component originalListName = originalPlayerListNames.remove(player.getUniqueId());

        if (originalDisplay != null) {
            player.displayName(originalDisplay);
        }

        if (originalListName != null) {
            // Restore player list name (tablist) - FOLIA FIX
            SchedulerUtils.runAtEntity(player, () -> {
                player.playerListName(originalListName);
            });
        }
    }

    public boolean isAfk(Player player) {
        return afkStatus.getOrDefault(player.getUniqueId(), false);
    }

    // ==================== AFK ACTIVITY DETECTION ====================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check for block position change
        boolean blockChanged = from.getBlockX() != to.getBlockX() ||
                from.getBlockY() != to.getBlockY() ||
                from.getBlockZ() != to.getBlockZ();

        // Check for head rotation (yaw/pitch) change
        boolean rotationChanged = Math.abs(from.getYaw() - to.getYaw()) > 0.1 ||
                Math.abs(from.getPitch() - to.getPitch()) > 0.1;

        if (blockChanged || rotationChanged) {
            updateActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastActivity.remove(uuid);
        afkStatus.remove(uuid);
        originalDisplayNames.remove(uuid);
        originalPlayerListNames.remove(uuid);
        lastKnownLocation.remove(uuid);
    }

    // ==================== AFK MONSTER PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isAfk(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== AUTO REPLENISH ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack placed = event.getItemInHand();
        updateActivity(player);

        if (placed.getAmount() <= 1) {
            replenishItem(player, placed.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Material bucket = event.getBucket();
        updateActivity(player);

        SchedulerUtils.runAtEntityLater(player, () -> replenishItem(player, bucket), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumed = event.getItem();
        updateActivity(player);

        if (consumed.getAmount() <= 1) {
            Material type = consumed.getType();
            SchedulerUtils.runAtEntityLater(player, () -> {
                if (!replenishItem(player, type) && FOODS.contains(type)) {
                    replenishAnyFood(player);
                }
            }, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player))
            return;
        updateActivity(player);

        Material type = switch (event.getEntity().getType()) {
            case SNOWBALL -> Material.SNOWBALL;
            case EGG -> Material.EGG;
            case ENDER_PEARL -> Material.ENDER_PEARL;
            default -> null;
        };

        if (type != null) {
            Material finalType = type;
            SchedulerUtils.runAtEntityLater(player, () -> {
                if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                    replenishItem(player, finalType);
                }
            }, 1);
        }
    }

    private boolean replenishItem(Player player, Material type) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == type) {
                inv.setItem(inv.getHeldItemSlot(), item.clone());
                inv.setItem(i, null);
                return true;
            }
        }
        return false;
    }

    private void replenishAnyFood(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && FOODS.contains(item.getType())) {
                inv.setItem(inv.getHeldItemSlot(), item.clone());
                inv.setItem(i, null);
                return;
            }
        }
    }
}
