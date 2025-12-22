package com.nyarutoru.nekoplugin.features.player;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

/**
 * Unified Player Feature Listener.
 * Handles: Pet Carrying, AFK System, Auto Item Replenishment
 */
public class PlayerFeatureListener implements Listener {

    private static final Set<EntityType> CARRIABLE_PETS = Set.of(
            EntityType.CAT, EntityType.WOLF, EntityType.PARROT, EntityType.FOX,
            EntityType.RABBIT, EntityType.CHICKEN, EntityType.OCELOT,
            EntityType.AXOLOTL, EntityType.FROG, EntityType.ALLAY, EntityType.BEE);
    private static final long AFK_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
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
    // ========== Pet Carrying ==========
    private final Map<UUID, Entity> carriedPets = new HashMap<>();
    // ========== AFK System ==========
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Boolean> afkStatus = new HashMap<>();
    // Store original display names to restore when player returns from AFK
    private final Map<UUID, Component> originalDisplayNames = new HashMap<>();

    public PlayerFeatureListener(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Start AFK check
        SchedulerUtils.runGlobalTimer(this::checkAfkPlayers, 20 * 30, 20 * 30);

        // Initialize online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateActivity(player);
        }
    }

    public void stop() {
        // Restore all AFK players' display names
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (afkStatus.getOrDefault(player.getUniqueId(), false)) {
                restoreDisplayName(player);
            }
        }
        // Drop all carried pets
        for (UUID uuid : new HashSet<>(carriedPets.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                dropPet(player);
        }
    }

    // ==================== PET CARRYING ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!player.isSneaking())
            return;
        if (carriedPets.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Already carrying a pet!").color(NamedTextColor.RED));
            return;
        }
        if (!CARRIABLE_PETS.contains(entity.getType()))
            return;

        if (entity instanceof Tameable tameable) {
            if (!tameable.isTamed() ||
                    (tameable.getOwner() != null && !tameable.getOwner().getUniqueId().equals(player.getUniqueId()))) {
                player.sendMessage(Component.text("Not your pet!").color(NamedTextColor.RED));
                return;
            }
        }

        event.setCancelled(true);
        entity.setInvulnerable(true);
        player.addPassenger(entity);
        carriedPets.put(player.getUniqueId(), entity);
        player.sendMessage(Component.text("✓ Picked up pet").color(NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        if (player.isSneaking())
            return;
        if (!carriedPets.containsKey(player.getUniqueId()))
            return;

        event.setCancelled(true);
        Entity pet = carriedPets.remove(player.getUniqueId());
        if (pet != null) {
            player.removePassenger(pet);
            pet.teleport(event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5));
            pet.setInvulnerable(false);
            player.sendMessage(Component.text("✓ Placed pet").color(NamedTextColor.GREEN));
        }
        // Update activity when interacting
        updateActivity(player);
    }

    public void dropPet(Player player) {
        Entity pet = carriedPets.remove(player.getUniqueId());
        if (pet != null) {
            player.removePassenger(pet);
            pet.teleport(player.getLocation());
            pet.setInvulnerable(false);
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
        if (afkStatus.getOrDefault(player.getUniqueId(), false)) {
            setAfk(player, false);
        }
    }

    private void setAfk(Player player, boolean afk) {
        afkStatus.put(player.getUniqueId(), afk);
        if (afk) {
            // Store original display name
            originalDisplayNames.put(player.getUniqueId(), player.displayName());
            // Set AFK prefix
            Component afkName = Component.text("[AFK] ").color(NamedTextColor.GRAY)
                    .append(player.displayName().color(NamedTextColor.GRAY));
            player.displayName(afkName);
            Bukkit.broadcast(Component.text(player.getName() + " is now AFK").color(NamedTextColor.GRAY));
        } else {
            restoreDisplayName(player);
            Bukkit.broadcast(Component.text(player.getName() + " is no longer AFK").color(NamedTextColor.GREEN));
        }
    }

    private void restoreDisplayName(Player player) {
        Component original = originalDisplayNames.remove(player.getUniqueId());
        if (original != null) {
            player.displayName(original);
        }
    }

    public boolean isAfk(Player player) {
        return afkStatus.getOrDefault(player.getUniqueId(), false);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            updateActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
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
        dropPet(event.getPlayer());
    }

    @EventHandler
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
