package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Player Feature Listener.
 * Handles: AFK System, Auto Item Replenishment, Crop Harvest
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

    // ========== Crop Harvest ==========
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART);

    private static final Set<Material> HOES = Set.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE);

    private final NekoPlugin plugin;

    // ========== AFK System ==========
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afkStatus = new ConcurrentHashMap<>();
    // Store original display names and player list names to restore when player
    // returns from AFK
    private final Map<UUID, Component> originalDisplayNames = new ConcurrentHashMap<>();
    private final Map<UUID, Component> originalPlayerListNames = new ConcurrentHashMap<>();
    // Store last known location for head rotation tracking
    private final Map<UUID, Location> lastKnownLocation = new ConcurrentHashMap<>();
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
        // Restore all AFK players' display names and clean up all maps
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (afkStatus.getOrDefault(uuid, false)) {
                restoreDisplayName(player);
            }
        }
        
        // Clear all maps to prevent memory leaks
        originalDisplayNames.clear();
        originalPlayerListNames.clear();
        lastKnownLocation.clear();
        lastActivity.clear();
        afkStatus.clear();
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
        if (player == null) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        afkStatus.put(uuid, afk);

        if (afk) {
            // Store original display name and player list name with null safety
            Component currentDisplayName = player.displayName();
            Component currentPlayerListName = player.playerListName();
            
            if (currentDisplayName != null) {
                originalDisplayNames.put(uuid, currentDisplayName);
            }
            if (currentPlayerListName != null) {
                originalPlayerListNames.put(uuid, currentPlayerListName);
            }

            // Set AFK prefix for both display name and tablist
            Component baseName = currentDisplayName != null ? currentDisplayName : Component.text(player.getName());
            Component afkName = Component.text("[AFK] ").color(NamedTextColor.GRAY)
                    .append(baseName.color(NamedTextColor.GRAY));

            // Update display name
            SchedulerUtils.runAtEntity(player, () -> {
                player.displayName(afkName);
            });

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
        if (player == null) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        Component originalDisplay = originalDisplayNames.remove(uuid);
        Component originalListName = originalPlayerListNames.remove(uuid);

        if (originalDisplay != null) {
            SchedulerUtils.runAtEntity(player, () -> {
                player.displayName(originalDisplay);
            });
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

        // Right-click crop harvest
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            handleCropHarvest(event);
        }
    }

    // ==================== CROP HARVEST ====================

    private void handleCropHarvest(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Material cropType = block.getType();
        if (!CROPS.contains(cropType))
            return;

        // Check if using hoe or empty hand
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material handMaterial = handItem.getType();

        if (handMaterial != Material.AIR && !HOES.contains(handMaterial)) {
            return;
        }

        // Check if crop is fully grown
        if (!(block.getBlockData() instanceof Ageable ageable))
            return;

        if (ageable.getAge() < ageable.getMaximumAge()) {
            return; // Not fully grown
        }

        // Get seed material for replanting
        Material seedMaterial = getSeedMaterial(cropType);
        if (seedMaterial == null)
            return;

        // Harvest: drop items at block location
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : block.getDrops(handItem, player)) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }

        // Replant: reset crop age to 0
        ageable.setAge(0);
        block.setBlockData(ageable);

        // Play harvest sound
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);

        // Cancel event to prevent other interactions
        event.setCancelled(true);
    }

    private Material getSeedMaterial(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
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
            Material type = placed.getType();
            // Determine which hand was used for placing
            boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

            SchedulerUtils.runAtEntityLater(player, () -> {
                // Only replenish if the hand is now empty
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();

                if (handItem.getType() == Material.AIR) {
                    replenishItem(player, type, isOffhand);
                }
            }, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Material bucket = event.getBucket();
        updateActivity(player);

        // Determine which hand held the bucket
        boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

        SchedulerUtils.runAtEntityLater(player, () -> {
            // Only replenish if the hand now has an empty bucket
            ItemStack handItem = isOffhand
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();

            // After emptying bucket, the hand will have BUCKET (empty bucket)
            if (handItem.getType() == Material.BUCKET) {
                replenishItem(player, bucket, isOffhand);
            }
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumed = event.getItem();
        updateActivity(player);

        if (consumed.getAmount() <= 1) {
            Material type = consumed.getType();
            // Determine which hand the item was consumed from
            boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

            SchedulerUtils.runAtEntityLater(player, () -> {
                // Only replenish if the hand is now empty
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();

                if (handItem.getType() == Material.AIR) {
                    if (!replenishItem(player, type, isOffhand) && FOODS.contains(type)) {
                        replenishAnyFood(player, isOffhand);
                    }
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

            // Determine which hand held the projectile by checking which one had it
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean isOffhand = offHand.getType() == type && mainHand.getType() != type;

            SchedulerUtils.runAtEntityLater(player, () -> {
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();

                if (handItem.getType() == Material.AIR) {
                    replenishItem(player, finalType, isOffhand);
                }
            }, 1);
        }
    }

    private boolean replenishItem(Player player, Material type, boolean toOffhand) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == type) {
                if (toOffhand) {
                    inv.setItemInOffHand(item.clone());
                } else {
                    inv.setItem(inv.getHeldItemSlot(), item.clone());
                }
                inv.setItem(i, null);
                return true;
            }
        }
        return false;
    }

    private void replenishAnyFood(Player player, boolean toOffhand) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && FOODS.contains(item.getType())) {
                if (toOffhand) {
                    inv.setItemInOffHand(item.clone());
                } else {
                    inv.setItem(inv.getHeldItemSlot(), item.clone());
                }
                inv.setItem(i, null);
                return;
            }
        }
    }
}
