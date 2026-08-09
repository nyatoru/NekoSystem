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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Unified Player Feature Listener.
 * Handles: AFK System, Auto Item Replenishment, Crop Harvest
 */
public class PlayerFeatureListener implements Listener {

    private static final int AFK_CHECK_INTERVAL_TICKS = 20 * 30;
    private static final int DEFAULT_AFK_TIMEOUT_SECONDS = 5 * 60;
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
    static final List<Material> DEFAULT_ALLOWED_CROPS = List.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART);
    private static final Set<Material> DEFAULT_CROP_SET = Set.copyOf(DEFAULT_ALLOWED_CROPS);

    private static final Set<Material> HOES = Set.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE);

    private final NekoPlugin plugin;
    private final Consumer<SchedulerUtils.TaskHandle> taskOwner;
    private final AtomicLong generation = new AtomicLong();
    private final Set<SchedulerUtils.TaskHandle> ownedTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<SchedulerUtils.TaskHandle>> delayedTasks = new ConcurrentHashMap<>();
    private final Set<SchedulerUtils.TaskHandle> cleanupTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private volatile SchedulerUtils.TaskHandle afkTimer;

    private volatile boolean afkEnabled = true;
    private volatile int afkTimeoutSeconds = DEFAULT_AFK_TIMEOUT_SECONDS;
    private volatile boolean activityDetection = true;
    private volatile boolean afkDisplay = true;
    private volatile String afkPrefix = "[AFK] ";
    private volatile boolean afkBroadcasts = true;
    private volatile boolean monsterProtection = true;
    private volatile boolean autoReplenish = true;
    private volatile boolean foodFallback = true;
    private volatile boolean cropHarvest = true;
    private volatile Set<Material> allowedCrops = DEFAULT_CROP_SET;
    private volatile boolean hoeRequired = true;
    private volatile boolean replantCrops = true;

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

    public PlayerFeatureListener(NekoPlugin plugin, Consumer<SchedulerUtils.TaskHandle> taskOwner) {
        this.plugin = plugin;
        this.taskOwner = taskOwner;
        this.afkKey = new NamespacedKey(plugin, AFK_METADATA_KEY);
    }

    public void configure(boolean afkEnabled, int afkTimeoutSeconds, boolean activityDetection,
                           boolean afkDisplay, String afkPrefix, boolean afkBroadcasts,
                           boolean monsterProtection, boolean autoReplenish, boolean foodFallback,
                           boolean cropHarvest, List<Material> allowedCrops, boolean hoeRequired,
                           boolean replantCrops) {
        this.afkEnabled = afkEnabled;
        this.afkTimeoutSeconds = afkTimeoutSeconds;
        this.activityDetection = activityDetection;
        this.afkDisplay = afkDisplay;
        this.afkPrefix = afkPrefix;
        this.afkBroadcasts = afkBroadcasts;
        this.monsterProtection = monsterProtection;
        this.autoReplenish = autoReplenish;
        this.foodFallback = foodFallback;
        this.cropHarvest = cropHarvest;
        this.allowedCrops = Set.copyOf(allowedCrops);
        this.hoeRequired = hoeRequired;
        this.replantCrops = replantCrops;
    }

    public void start() {
        if (running) return;
        for (SchedulerUtils.TaskHandle task : ownedTasks) cancelTask(task);
        ownedTasks.clear();
        for (SchedulerUtils.TaskHandle task : cleanupTasks) cancelTask(task);
        cleanupTasks.clear();
        running = true;
        long currentGeneration = generation.incrementAndGet();
        scheduleAfkTimer(currentGeneration);
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayer(player, currentGeneration, () -> updateActivity(player));
        }
    }

    public void stop() {
        running = false;
        generation.incrementAndGet();
        cancelTask(afkTimer);
        afkTimer = null;
        for (Set<SchedulerUtils.TaskHandle> tasks : delayedTasks.values()) {
            for (SchedulerUtils.TaskHandle task : tasks) cancelTask(task);
        }
        delayedTasks.clear();
        for (SchedulerUtils.TaskHandle task : ownedTasks) cancelTask(task);
        ownedTasks.clear();

        for (SchedulerUtils.TaskHandle task : cleanupTasks) cancelTask(task);
        cleanupTasks.clear();
        long cleanupGeneration = generation.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Component originalDisplay = originalDisplayNames.remove(uuid);
            Component originalListName = originalPlayerListNames.remove(uuid);
            SchedulerUtils.TaskHandle[] holder = new SchedulerUtils.TaskHandle[1];
            holder[0] = SchedulerUtils.runAtEntityTask(player, () -> {
                cleanupTasks.remove(holder[0]);
                if (!running && generation.get() == cleanupGeneration) {
                    if (originalDisplay != null) player.displayName(originalDisplay);
                    if (originalListName != null) player.playerListName(originalListName);
                    player.getPersistentDataContainer().remove(afkKey);
                }
            });
            cleanupTasks.add(holder[0]);
        }
        originalDisplayNames.clear();
        originalPlayerListNames.clear();
        lastKnownLocation.clear();
        lastActivity.clear();
        afkStatus.clear();
    }

    private void scheduleAfkTimer(long expectedGeneration) {
        cancelTask(afkTimer);
        afkTimer = own(SchedulerUtils.runGlobalTimerTask(
                () -> {
                    if (isCurrent(expectedGeneration)) checkAfkPlayers(expectedGeneration);
                }, AFK_CHECK_INTERVAL_TICKS, AFK_CHECK_INTERVAL_TICKS));
    }

    private void restoreAfkState(Player player) {
        UUID uuid = player.getUniqueId();
        Component originalDisplay = originalDisplayNames.remove(uuid);
        Component originalListName = originalPlayerListNames.remove(uuid);
        if (originalDisplay != null) player.displayName(originalDisplay);
        if (originalListName != null) player.playerListName(originalListName);
        player.getPersistentDataContainer().remove(afkKey);
    }

    private static void cancelTask(SchedulerUtils.TaskHandle task) {
        SchedulerUtils.cancelTask(task);
    }
    // ==================== AFK SYSTEM ====================

    private void checkAfkPlayers(long currentGeneration) {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayer(player, currentGeneration, () -> {
                UUID uuid = player.getUniqueId();
                Long last = lastActivity.get(uuid);
                if (last == null) {
                    updateActivity(player);
                    return;
                }

                boolean wasAfk = afkStatus.getOrDefault(uuid, false);
                if (afkEnabled && activityDetection && (now - last) >= afkTimeoutSeconds * 1000L && !wasAfk) {
                    setAfk(player, true);
                }
            });
        }
    }

    public void updateActivity(Player player) {
        if (!running) return;
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
            if (!afkEnabled) {
                afkStatus.remove(uuid);
                return;
            }
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
            Component afkName = Component.text(afkPrefix).color(NamedTextColor.GRAY)
                    .append(baseName.color(NamedTextColor.GRAY));

            if (afkDisplay) {
                player.displayName(afkName);
                player.playerListName(afkName);
                player.getPersistentDataContainer().set(afkKey, PersistentDataType.BYTE, (byte) 1);
            }

            if (afkBroadcasts) Bukkit.broadcast(Component.text(player.getName() + " is now AFK").color(NamedTextColor.GRAY));
        } else {
            restoreDisplayName(player);

            // Remove metadata
            player.getPersistentDataContainer().remove(afkKey);

            if (afkBroadcasts) Bukkit.broadcast(Component.text(player.getName() + " is no longer AFK").color(NamedTextColor.GREEN));
        }
    }

    private void restoreDisplayName(Player player) {
        if (player == null) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        Component originalDisplay = originalDisplayNames.remove(uuid);
        Component originalListName = originalPlayerListNames.remove(uuid);

        if (originalDisplay != null) player.displayName(originalDisplay);
        if (originalListName != null) player.playerListName(originalListName);
    }

    private SchedulerUtils.TaskHandle own(SchedulerUtils.TaskHandle task) {
        if (task != null) {
            ownedTasks.add(task);
            taskOwner.accept(task);
        }
        return task;
    }

    public void setAfkEnabled(boolean value) {
        afkEnabled = value;
        if (!value) {
            long expectedGeneration = generation.get();
            for (Player player : Bukkit.getOnlinePlayers()) {
                schedulePlayer(player, expectedGeneration, () -> {
                    restoreAfkState(player);
                    player.getPersistentDataContainer().remove(afkKey);
                    afkStatus.remove(player.getUniqueId());
                });
            }
        }
    }

    public void setAfkTimeoutSeconds(int value) {
        if (value < 1 || value > 86400) throw new IllegalArgumentException("Timeout must be between 1 and 86400 seconds");
        afkTimeoutSeconds = value;
        rescheduleAfkTimer();
    }

    public void setActivityDetection(boolean value) {
        activityDetection = value;
        rescheduleAfkTimer();
    }

    public void setAfkDisplay(boolean value) {
        afkDisplay = value;
        long expectedGeneration = generation.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayer(player, expectedGeneration, () -> {
                if (!value) restoreAfkState(player);
                else if (afkStatus.getOrDefault(player.getUniqueId(), false)) refreshAfkDisplay(player);
                if (!value) player.getPersistentDataContainer().remove(afkKey);
            });
        }
    }

    public void setAfkPrefix(String value) {
        if (value.length() > 64) throw new IllegalArgumentException("AFK prefix is too long");
        afkPrefix = value;
        if (afkDisplay) {
            long expectedGeneration = generation.get();
            for (Player player : Bukkit.getOnlinePlayers()) {
                schedulePlayer(player, expectedGeneration, () -> {
                    if (afkStatus.getOrDefault(player.getUniqueId(), false)) refreshAfkDisplay(player);
                });
            }
        }
    }

    private void refreshAfkDisplay(Player player) {
        restoreDisplayName(player);
        Component baseName = player.displayName() != null ? player.displayName() : Component.text(player.getName());
        Component afkName = Component.text(afkPrefix).color(NamedTextColor.GRAY)
                .append(baseName.color(NamedTextColor.GRAY));
        originalDisplayNames.put(player.getUniqueId(), baseName);
        originalPlayerListNames.put(player.getUniqueId(), player.playerListName());
        player.displayName(afkName);
        player.playerListName(afkName);
        player.getPersistentDataContainer().set(afkKey, PersistentDataType.BYTE, (byte) 1);
    }

    public void setAfkBroadcasts(boolean value) { afkBroadcasts = value; }

    public void setMonsterProtection(boolean value) { monsterProtection = value; }

    public void setAutoReplenish(boolean value) {
        autoReplenish = value;
        if (!value) cancelDelayedTasks();
    }

    public void setFoodFallback(boolean value) { foodFallback = value; }

    public void setCropHarvest(boolean value) { cropHarvest = value; }

    public void setAllowedCrops(List<Material> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("At least one crop is required");
        allowedCrops = Set.copyOf(values);
    }

    public void setHoeRequired(boolean value) { hoeRequired = value; }

    public void setReplantCrops(boolean value) { replantCrops = value; }

    private void rescheduleAfkTimer() {
        if (!running) return;
        long currentGeneration = generation.incrementAndGet();
        scheduleAfkTimer(currentGeneration);
    }

    private void cancelDelayedTasks() {
        for (Set<SchedulerUtils.TaskHandle> tasks : delayedTasks.values()) {
            for (SchedulerUtils.TaskHandle task : tasks) cancelTask(task);
        }
        delayedTasks.clear();
    }

    private boolean isCurrent(long expectedGeneration) {
        return running && generation.get() == expectedGeneration;
    }

    private void schedulePlayer(Player player, long expectedGeneration, Runnable action) {
        own(SchedulerUtils.runAtEntityTask(player, () -> {
            if (isCurrent(expectedGeneration)) action.run();
        }));
    }

    private void schedulePlayerLater(Player player, Runnable action) {
        if (!autoReplenish) return;
        UUID uuid = player.getUniqueId();
        long expectedGeneration = generation.get();
        Set<SchedulerUtils.TaskHandle> tasks = delayedTasks.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        SchedulerUtils.TaskHandle[] holder = new SchedulerUtils.TaskHandle[1];
        holder[0] = own(SchedulerUtils.runAtEntityLaterTask(player, () -> {
            tasks.remove(holder[0]);
            delayedTasks.computeIfPresent(uuid, (ignored, remaining) -> remaining.isEmpty() ? null : remaining);
            if (isCurrent(expectedGeneration) && autoReplenish && player.isOnline()) action.run();
        }, 1));
        tasks.add(holder[0]);
    }

    public boolean isAfk(Player player) {
        return afkStatus.getOrDefault(player.getUniqueId(), false);
    }

    // ==================== AFK ACTIVITY DETECTION ====================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

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
        if (!cropHarvest) return;
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Material cropType = block.getType();
        if (!allowedCrops.contains(cropType))
            return;

        // Check if using hoe or empty hand
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material handMaterial = handItem.getType();

        if (hoeRequired && handMaterial != Material.AIR && !HOES.contains(handMaterial)) {
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
        if (replantCrops) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        } else {
            block.setType(Material.AIR);
        }

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
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Set<SchedulerUtils.TaskHandle> tasks = delayedTasks.remove(uuid);
        if (tasks != null) for (SchedulerUtils.TaskHandle task : tasks) cancelTask(task);
        restoreAfkState(player);
        lastActivity.remove(uuid);
        afkStatus.remove(uuid);
        lastKnownLocation.remove(uuid);
    }

    // ==================== AFK MONSTER PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (monsterProtection && event.getTarget() instanceof Player player && isAfk(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== AUTO REPLENISH ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!autoReplenish) return;
        Player player = event.getPlayer();
        ItemStack placed = event.getItemInHand();
        updateActivity(player);

        if (placed.getAmount() <= 1) {
            Material type = placed.getType();
            // Determine which hand was used for placing
            boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

            schedulePlayerLater(player, () -> {
                // Only replenish if the hand is now empty
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();

                if (handItem.getType() == Material.AIR) {
                    replenishItem(player, type, isOffhand);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!autoReplenish) return;
        Player player = event.getPlayer();
        Material bucket = event.getBucket();
        updateActivity(player);

        // Determine which hand held the bucket
        boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

        schedulePlayerLater(player, () -> {
            // Only replenish if the hand now has an empty bucket (covers water/lava/powder snow/entity buckets)
            ItemStack handItem = isOffhand
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();

            if (handItem.getType() == Material.BUCKET) {
                replenishItem(player, bucket, isOffhand);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!autoReplenish) return;
        Player player = event.getPlayer();
        ItemStack consumed = event.getItem();
        updateActivity(player);

        if (consumed.getAmount() <= 1) {
            Material type = consumed.getType();
            boolean isOffhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;

            schedulePlayerLater(player, () -> {
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();
                Material handType = handItem.getType();
                // AIR = fully consumed; BOWL/GLASS_BOTTLE/BUCKET = remainder left (stew/potion/honey/milk)
                boolean isEmpty = handType == Material.AIR;
                boolean isRemainder = handType == Material.BOWL || handType == Material.GLASS_BOTTLE || handType == Material.BUCKET;
                if (isEmpty || isRemainder) {
                    boolean replenished = replenishItem(player, type, isOffhand);
                    if (!replenished && foodFallback && FOODS.contains(type)) {
                        replenishAnyFood(player, isOffhand);
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!autoReplenish) return;
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

            schedulePlayerLater(player, () -> {
                ItemStack handItem = isOffhand
                        ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();

                if (handItem.getType() == Material.AIR) {
                    replenishItem(player, finalType, isOffhand);
                }
            });
        }
    }

    private boolean replenishItem(Player player, Material type, boolean toOffhand) {
        PlayerInventory inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == type) {
                ItemStack handItem = toOffhand ? inv.getItemInOffHand() : inv.getItemInMainHand();
                ItemStack replenishment = item.clone();
                if (toOffhand) {
                    inv.setItemInOffHand(replenishment);
                } else {
                    inv.setItem(inv.getHeldItemSlot(), replenishment);
                }
                // Preserve remainder (BUCKET/BOWL/GLASS_BOTTLE) instead of deleting it
                if (handItem.getType() == Material.AIR) {
                    inv.setItem(i, null);
                } else {
                    inv.setItem(i, handItem.clone());
                }
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
                ItemStack handItem = toOffhand ? inv.getItemInOffHand() : inv.getItemInMainHand();
                ItemStack replenishment = item.clone();
                if (toOffhand) {
                    inv.setItemInOffHand(replenishment);
                } else {
                    inv.setItem(inv.getHeldItemSlot(), replenishment);
                }
                if (handItem.getType() == Material.AIR) {
                    inv.setItem(i, null);
                } else {
                    inv.setItem(i, handItem.clone());
                }
                return;
            }
        }
    }
}
