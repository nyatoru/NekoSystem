package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GraveManager {
    private final NekoPlugin plugin;
    private final GraveRepository repository;
    private final GraveDisplayManager displayManager;
    private final GravePersistenceQueue persistence = new GravePersistenceQueue();
    private final GraveLocationReservations reservations = new GraveLocationReservations();
    private final Map<UUID, Grave> graves = new ConcurrentHashMap<>();
    private final Map<String, UUID> locations = new ConcurrentHashMap<>();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private long graveLifetimeMillis = GraveConfig.DEFAULT_GRAVE_LIFETIME_MS;
    private long checkIntervalTicks = GraveConfig.DEFAULT_GRAVE_CHECK_INTERVAL_TICKS;
    private int maxGravesPerPlayer = GraveConfig.DEFAULT_MAX_GRAVES_PER_PLAYER;
    private int safeLocationSearchRadius = GraveConfig.DEFAULT_MAX_SAFE_LOCATION_SEARCH_RADIUS;
    private boolean started;
    private BukkitTask paperTask;
    private ScheduledTask foliaTask;

    public GraveManager(NekoPlugin plugin) {
        this.plugin = plugin;
        this.repository = new GraveRepository(plugin);
        this.displayManager = new GraveDisplayManager(plugin, this);
    }

    public void setGraveLifetimeMillis(long value) { graveLifetimeMillis = value; }
    public void setCheckIntervalTicks(long value) {
        checkIntervalTicks = value;
        if (started) rescheduleExpiryTask();
    }
    public void setMaxGravesPerPlayer(int value) { maxGravesPerPlayer = value; }
    public void setSafeLocationSearchRadius(int value) { safeLocationSearchRadius = value; }
    public void setDisplayUpdateIntervalTicks(long value) { displayManager.setUpdateIntervalTicks(value); }

    public boolean start() {
        if (!repository.initialize()) return false;
        for (Grave grave : repository.loadAll()) {
            addToIndexes(grave);
            resume(grave);
        }
        displayManager.start();
        started = true;
        rescheduleExpiryTask();
        return true;
    }

    public void stop() {
        started = false;
        cancelExpiryTask();
        displayManager.stop();
        persistence.close();
        viewers.clear(); graves.clear(); locations.clear(); repository.close();
    }

    public Grave create(Player player, Location deathLocation, List<ItemStack> items, int experience) {
        Location markerLocation = reserveSafeLocation(deathLocation);
        if (markerLocation == null) return null;
        GravePosition markerPosition = GravePosition.from(markerLocation);
        Block block = markerLocation.getBlock();
        if (!Bukkit.isOwnedByCurrentRegion(block) || !block.getType().isAir()) {
            reservations.release(markerPosition); return null;
        }
        block.setType(Material.PLAYER_HEAD);
        if (!applyMarkerProfile(block, ResolvableProfile.resolvableProfile(player.getPlayerProfile()))) {
            reservations.release(markerPosition); return null;
        }
        Grave grave = Grave.create(player.getUniqueId(), player.getName(), GravePosition.from(deathLocation),
            markerPosition, items, experience, System.currentTimeMillis(), graveLifetimeMillis);
        addToIndexes(grave);
        displayManager.reconcile(grave, markerLocation);
        save(grave, success -> {
            if (!success) failInitialPersistence(grave); else enforceLimit(grave.getOwnerId());
        });
        return grave;
    }

    public Grave get(Location location) {
        UUID id = locations.get(GravePosition.from(location).key());
        return id == null ? null : graves.get(id);
    }

    Grave get(UUID id) { return graves.get(id); }

    void reconcileDisplays(Collection<org.bukkit.entity.Entity> entities) { displayManager.reconcileLoaded(entities); }
    void unloadDisplays(Collection<org.bukkit.entity.Entity> entities) { displayManager.unloaded(entities); }

    public boolean isGrave(Location location) { return get(location) != null; }

    public List<Grave> getForPlayer(UUID ownerId) {
        return graves.values().stream().filter(grave -> grave.getOwnerId().equals(ownerId) && grave.getState() == Grave.State.ACTIVE)
            .sorted(Comparator.comparingLong(Grave::getCreatedAt)).toList();
    }

    public Collection<Grave> getAll() { return List.copyOf(graves.values()); }

    public boolean canAccess(Player player, Grave grave) {
        return grave.getState() == Grave.State.ACTIVE && GraveAccessPolicy.canAccess(
            grave.getOwnerId(), player.getUniqueId(), player.hasPermission("nekoplugin.grave.use"),
            player.hasPermission("nekoplugin.grave.admin"));
    }

    public boolean acquireViewer(Grave grave) { return grave.getState() == Grave.State.ACTIVE && viewers.add(grave.getId()); }
    public void releaseViewer(Grave grave) { viewers.remove(grave.getId()); }

    public boolean claimItem(Grave grave, int index, Player player, Consumer<Boolean> completion) {
        Grave.ItemClaim claim = grave.claimItem(index);
        if (claim == null) return false;
        boolean finalClaim = grave.isEmpty();
        int experience = 0;
        if (finalClaim) {
            if (!grave.beginRemoval(Grave.Disposition.LOOTED)) {
                grave.rollbackClaim(claim);
                return false;
            }
            experience = grave.consumeExperience();
        }
        int claimedExperience = experience;
        save(grave, saved -> {
            if (!saved) {
                if (finalClaim) {
                    grave.restoreExperience(claimedExperience);
                    grave.cancelRemoval();
                }
                grave.rollbackClaim(claim);
                SchedulerUtils.runAtEntity(player, () -> completion.accept(false));
                return;
            }
            SchedulerUtils.runAtEntity(player, () -> deliverClaim(
                grave, claim, claimedExperience, finalClaim, player, completion));
        });
        return true;
    }

    private void deliverClaim(Grave grave, Grave.ItemClaim claim, int experience, boolean finalClaim,
                              Player player, Consumer<Boolean> completion) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(claim.item());
        grave.commitClaim(claim);
        if (!overflow.isEmpty()) {
            if (finalClaim) {
                grave.restoreExperience(experience);
                grave.cancelRemoval();
            }
            grave.restoreItem(claim.index(), overflow.values().iterator().next());
            save(grave, ignored -> SchedulerUtils.runAtEntity(player, () -> completion.accept(false)));
            return;
        }
        if (finalClaim) {
            player.giveExp(experience);
            performDisposition(grave, player, completion);
        } else {
            completion.accept(true);
        }
    }

    public boolean claimAll(Grave grave, Player player, Consumer<Boolean> completion) {
        List<ItemStack> items = grave.getItems();
        if (!canFit(player, items)) return false;
        Grave.AllClaim claim = grave.claimAll();
        if (claim == null) return false;
        save(grave, saved -> {
            if (!saved) {
                grave.rollbackAll(claim);
                SchedulerUtils.runAtEntity(player, () -> completion.accept(false));
                return;
            }
            SchedulerUtils.runAtEntity(player, () -> deliverAll(grave, claim, player, completion));
        });
        return true;
    }

    private void deliverAll(Grave grave, Grave.AllClaim claim, Player player, Consumer<Boolean> completion) {
        List<ItemStack> items = claim.items();
        if (!canFit(player, items)) {
            rollbackAll(grave, claim, player, completion);
            return;
        }
        ItemStack[] before = GraveInventoryCapacity.cloneContents(player.getInventory().getStorageContents());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        if (!overflow.isEmpty()) {
            player.getInventory().setStorageContents(before);
            rollbackAll(grave, claim, player, completion);
            return;
        }
        grave.commitAll(claim);
        player.giveExp(claim.experience());
        performDisposition(grave, player, completion);
    }

    private void rollbackAll(Grave grave, Grave.AllClaim claim, Player player, Consumer<Boolean> completion) {
        grave.rollbackAll(claim);
        save(grave, ignored -> SchedulerUtils.runAtEntity(player, () -> completion.accept(false)));
    }

    private static boolean canFit(Player player, List<ItemStack> items) {
        return GraveInventoryCapacity.canFit(player.getInventory().getStorageContents(),
            player.getInventory().getMaxStackSize(), items);
    }

    public void remove(Grave grave, boolean dropContents) {
        if (!grave.beginRemoval(dropContents ? Grave.Disposition.DROP : Grave.Disposition.LOOTED)) return;
        viewers.remove(grave.getId());
        save(grave, saved -> {
            if (!saved) { grave.cancelRemoval(); return; }
            performDisposition(grave, null, ignored -> {});
        });
    }

    private void performDisposition(Grave grave, Player player, Consumer<Boolean> completion) {
        Location location = grave.getGravePosition().resolve(plugin.getServer());
        if (location == null) return;
        SchedulerUtils.runAtLocation(location, () -> {
            if (grave.getDisposition() == Grave.Disposition.DROP) dropContentsAndExperience(grave, location);
            displayManager.remove(grave, location);
            removeMarker(location);
            grave.markDisposed();
            save(grave, disposed -> {
                if (!disposed) return;
                persistence.submit(() -> repository.delete(grave.getId())).thenAccept(deleted -> {
                    if (deleted) finalizeRemoval(grave);
                    if (player != null) SchedulerUtils.runAtEntity(player, () -> completion.accept(deleted));
                });
            });
        });
    }

    private void failInitialPersistence(Grave grave) {
        Location location = grave.getGravePosition().resolve(plugin.getServer());
        if (location == null) return;
        SchedulerUtils.runAtLocation(location, () -> {
            dropContentsAndExperience(grave, location);
            displayManager.remove(grave, location);
            removeMarker(location);
            finalizeRemoval(grave);
        });
    }

    private void resume(Grave grave) {
        if (grave.getState() == Grave.State.ACTIVE) { restoreMarker(grave); return; }
        if (grave.getState() == Grave.State.REMOVING) {
            performDisposition(grave, null, ignored -> {});
            return;
        }
        persistence.submit(() -> repository.delete(grave.getId())).thenAccept(deleted -> {
            if (deleted) finalizeRemoval(grave);
        });
    }

    private void save(Grave grave, Consumer<Boolean> completion) {
        GraveSnapshot snapshot = grave.snapshot();
        persistence.submit(() -> repository.save(snapshot)).thenAccept(completion);
    }

    private void finalizeRemoval(Grave grave) {
        graves.remove(grave.getId(), grave);
        locations.remove(grave.getGravePosition().key(), grave.getId());
        reservations.release(grave.getGravePosition()); viewers.remove(grave.getId());
    }

    private void addToIndexes(Grave grave) {
        graves.put(grave.getId(), grave); locations.put(grave.getGravePosition().key(), grave.getId());
        reservations.reserve(grave.getGravePosition());
    }

    private void restoreMarker(Grave grave) {
        Location location = grave.getGravePosition().resolve(plugin.getServer());
        if (location != null) SchedulerUtils.runAtLocation(location, () -> {
            if (grave.getState() != Grave.State.ACTIVE || graves.get(grave.getId()) != grave) return;
            Block block = location.getBlock();
            if (block.getType().isAir()) block.setType(Material.PLAYER_HEAD);
            if (block.getType() == Material.PLAYER_HEAD) {
                ResolvableProfile profile = ResolvableProfile.resolvableProfile()
                    .uuid(grave.getOwnerId())
                    .name(grave.getOwnerName())
                    .build();
                applyMarkerProfile(block, profile);
                displayManager.reconcile(grave, location);
            }
        });
    }

    static boolean applyMarkerProfile(Block block, ResolvableProfile profile) {
        if (!(block.getState() instanceof Skull skull)) return false;
        return applyMarkerProfile(skull, profile);
    }

    static boolean applyMarkerProfile(Skull skull, ResolvableProfile profile) {
        skull.setProfile(profile);
        return skull.update(true);
    }

    private Location reserveSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int baseX = origin.getBlockX();
        int baseY = Math.max(world.getMinHeight() + 1, Math.min(origin.getBlockY(), world.getMaxHeight() - 2));
        int baseZ = origin.getBlockZ();
        for (int radius = 0; radius <= safeLocationSearchRadius; radius++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) for (int xOffset = -radius; xOffset <= radius; xOffset++) for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                Location candidate = new Location(world, baseX + xOffset, baseY + yOffset, baseZ + zOffset);
                if (!Bukkit.isOwnedByCurrentRegion(candidate) || !isSafe(candidate)) continue;
                GravePosition position = GravePosition.from(candidate);
                if (reservations.reserve(position)) return candidate;
            }
        }
        return null;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld(); int y = location.getBlockY();
        if (world == null || y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return false;
        Block target = location.getBlock();
        return target.getType().isAir() && target.getRelative(0, 1, 0).getType().isAir()
            && target.getRelative(0, -1, 0).getType().isSolid();
    }

    private void enforceLimit(UUID ownerId) {
        List<Grave> ownerGraves = new ArrayList<>(getForPlayer(ownerId));
        while (ownerGraves.size() > maxGravesPerPlayer) remove(ownerGraves.removeFirst(), true);
    }

    private void rescheduleExpiryTask() {
        cancelExpiryTask();
        if (!started) return;
        if (!plugin.isEnabled()) return;
        try {
            if (SchedulerUtils.isFolia()) {
                foliaTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                    task -> checkExpired(), checkIntervalTicks, checkIntervalTicks);
            } else {
                paperTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    this::checkExpired, checkIntervalTicks, checkIntervalTicks);
            }
        } catch (Exception ignored) {}
    }

    private void cancelExpiryTask() {
        if (paperTask != null) { paperTask.cancel(); paperTask = null; }
        if (foliaTask != null) { foliaTask.cancel(); foliaTask = null; }
    }

    private void checkExpired() {
        long now = System.currentTimeMillis();
        graves.values().stream().filter(grave -> grave.getState() == Grave.State.ACTIVE && grave.isExpired(now))
            .toList().forEach(grave -> remove(grave, true));
    }

    private static void dropContentsAndExperience(Grave grave, Location location) {
        World world = location.getWorld();
        if (world == null) return;
        Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
        grave.getItems().forEach(item -> world.dropItemNaturally(dropLocation, item));
        int experience = grave.consumeExperience();
        if (experience > 0) world.spawn(dropLocation, ExperienceOrb.class, orb -> orb.setExperience(experience));
    }

    private static void removeMarker(Location location) {
        if (location.getBlock().getType() == Material.PLAYER_HEAD) location.getBlock().setType(Material.AIR);
    }
}
