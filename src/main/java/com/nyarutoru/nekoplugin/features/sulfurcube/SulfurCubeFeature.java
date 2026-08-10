package com.nyarutoru.nekoplugin.features.sulfurcube;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SulfurCubeContent;
import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.SulfurCube;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Sponge Sulfur Cube: dry sponge cubes drain water like vanilla sponge.
 * Vanilla sponge drains 65 blocks within Manhattan distance 6.
 * Folia-safe: all block reads/writes happen on region thread via runAtLocation,
 * entity tracking via chunk load/unload + spawn events avoids global entity scans.
 */
public class SulfurCubeFeature extends AbstractFeature implements Listener {

    private static final int MAX_BLOCKS = 65;
    private static final int MAX_DISTANCE = 6;
    private static final long PERIOD_TICKS = 5L; // 0.25s feels instant
    private static final long REFRESH_PERIOD = 100L; // sync global list every 5s to catch misses

    private final Set<SulfurCube> tracked = ConcurrentHashMap.newKeySet();
    private long tickCount = 0;
    private NamespacedKey wetKey;
    private NamespacedKey spongeKey;

    public SulfurCubeFeature() {
        super("sulfurcube", "Sulfur Cube Sponge");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        wetKey = new NamespacedKey(plugin, "sulfurcube_sponge_wet");
        spongeKey = new NamespacedKey(plugin, "sulfurcube_has_sponge");
        registerListener(this, plugin);
        // initial population - safe during onEnable before regions tick
        try {
            for (World world : Bukkit.getWorlds()) {
                for (SulfurCube cube : world.getEntitiesByClass(SulfurCube.class)) {
                    tracked.add(cube);
                }
            }
        } catch (Throwable ignored) {}
        ownTask(SchedulerUtils.runGlobalTimerTask(this::tick, PERIOD_TICKS, PERIOD_TICKS));
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        tracked.clear();
    }

    // immediate drain when swallowing sponge - also clears wet flag like vanilla dry sponge
    @EventHandler(ignoreCancelled = true)
    public void onSwallow(SulfurCubeSwallowItemEvent event) {
        ItemStack newItem = event.getNewItem();
        ItemStack oldItem = event.getOldItem();
        SulfurCube cube = event.getEntity();
        // track any swallow
        tracked.add(cube);
        if (newItem != null && newItem.getType() == Material.SPONGE) {
            // dry sponge -> clear wet flag, mark has_sponge
            try {
                cube.getPersistentDataContainer().remove(wetKey);
                cube.getPersistentDataContainer().set(spongeKey, PersistentDataType.BYTE, (byte) 1);
            } catch (Throwable ignored) {}
            SchedulerUtils.runAtEntityLater(cube, () -> {
                try {
                    if (cube.isValid() && isSpongeCube(cube) && !isWet(cube)) {
                        SchedulerUtils.runAtLocation(cube.getLocation(), () -> drainAround(cube));
                    }
                } catch (Throwable t) {
                    NekoPlugin.getInstance().getLogger().log(Level.WARNING, "SulfurCube swallow drain failed", t);
                }
            }, 1L);
        } else if (newItem != null && newItem.getType() == Material.WET_SPONGE) {
            // wet sponge never drains - mark wet
            try {
                cube.getPersistentDataContainer().set(wetKey, PersistentDataType.BYTE, (byte) 1);
                cube.getPersistentDataContainer().set(spongeKey, PersistentDataType.BYTE, (byte) 1);
            } catch (Throwable ignored) {}
        } else {
            // any other item (including air) -> sponge removed
            try {
                cube.getPersistentDataContainer().remove(wetKey);
                cube.getPersistentDataContainer().remove(spongeKey);
            } catch (Throwable ignored) {}
        }
        // handle old sponge removal explicitly if needed
        if (oldItem != null && oldItem.getType() == Material.SPONGE && (newItem == null || newItem.getType() == Material.AIR)) {
            try {
                cube.getPersistentDataContainer().remove(wetKey);
                cube.getPersistentDataContainer().remove(spongeKey);
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof SulfurCube sc) tracked.add(sc);
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof SulfurCube sc) tracked.remove(sc);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof SulfurCube sc) tracked.add(sc);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof SulfurCube sc) tracked.remove(sc);
    }

    private void tick() {
        tickCount += PERIOD_TICKS;
        // periodic global refresh to catch any untracked cubes (e.g., after /reload or missed events)
        if (tickCount % REFRESH_PERIOD == 0) {
            try {
                for (World world : Bukkit.getWorlds()) {
                    for (SulfurCube cube : world.getEntitiesByClass(SulfurCube.class)) {
                        tracked.add(cube);
                    }
                }
            } catch (Throwable ignored) {
                // on Folia global read may occasionally fail - tracked set still covers chunk-based adds
            }
        }

        if (tracked.isEmpty()) return;
        // snapshot to avoid CME while iterating
        for (SulfurCube cube : Set.copyOf(tracked)) {
            if (!cube.isValid() || cube.isDead()) {
                tracked.remove(cube);
                continue;
            }
            // Folia-safe: all block/entity checks inside region task
            // ponytail: no hasNearbyWater early-out here - it skipped water at distance 3-6, breaking vanilla shape
            SchedulerUtils.runAtLocation(cube.getLocation(), () -> {
                try {
                    if (!cube.isValid()) {
                        tracked.remove(cube);
                        return;
                    }
                    if (!isSpongeCube(cube)) return;
                    // wet check via PDC - vanilla sponge becomes wet after draining
                    if (isWet(cube)) return;
                    drainAround(cube);
                } catch (Throwable t) {
                    NekoPlugin.getInstance().getLogger().log(Level.WARNING, "SulfurCube drain failed", t);
                }
            });
        }
    }

    private boolean isWet(SulfurCube cube) {
        try {
            if (wetKey == null) return false;
            Byte v = cube.getPersistentDataContainer().get(wetKey, PersistentDataType.BYTE);
            return v != null && v != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isSpongeCube(SulfurCube cube) {
        // primary: DataComponent (vanilla), fallback: PDC has_sponge flag (for cases where DataComponent read fails or timing)
        try {
            SulfurCubeContent content = cube.getData(DataComponentTypes.SULFUR_CUBE_CONTENT);
            if (content != null) {
                ItemStack item = content.absorbedItem();
                if (item != null) {
                    if (item.getType() == Material.SPONGE) return true;
                    if (item.getType() == Material.WET_SPONGE) return false;
                    // if content is other item, fall through to PDC check
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (spongeKey != null) {
                Byte v = cube.getPersistentDataContainer().get(spongeKey, PersistentDataType.BYTE);
                if (v != null && v != 0) {
                    // has_sponge but check not wet (wet handled outside, but double-check)
                    Byte wet = wetKey != null ? cube.getPersistentDataContainer().get(wetKey, PersistentDataType.BYTE) : null;
                    return wet == null || wet == 0;
                }
            }
        } catch (Throwable ignored) {}
        // final fallback: no sponge
        try {
            SulfurCubeContent c = cube.getData(DataComponentTypes.SULFUR_CUBE_CONTENT);
            return c != null && c.absorbedItem() != null && c.absorbedItem().getType() == Material.SPONGE;
        } catch (Throwable t) {
            return false;
        }
    }

    // must be called on region thread for the cube's location
    // replicates net.minecraft.world.level.block.SpongeBlock.removeWaterBreadthFirstSearch
    // MAX_DEPTH=6, MAX_COUNT=65, checks FluidTags.WATER via BucketPickup/Waterlogged/LiquidBlock/KELP
    private void drainAround(SulfurCube cube) {
        try {
            if (!cube.isValid()) return;
            World world = cube.getWorld();
            if (world == null) return;

            int cx = cube.getLocation().getBlockX();
            int cy = cube.getLocation().getBlockY();
            int cz = cube.getLocation().getBlockZ();

            BlockPos origin = new BlockPos(cx, cy, cz);
            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>(128);
            queue.add(origin);
            visited.add(origin);

            int count = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

            while (!queue.isEmpty() && count < MAX_BLOCKS) {
                BlockPos cur = queue.poll();
                for (int[] d : dirs) {
                    BlockPos nb = new BlockPos(cur.x + d[0], cur.y + d[1], cur.z + d[2]);
                    if (!visited.add(nb)) continue;
                    int dist = Math.abs(nb.x - origin.x) + Math.abs(nb.y - origin.y) + Math.abs(nb.z - origin.z);
                    if (dist > MAX_DISTANCE) continue;
                    if (nb.y < world.getMinHeight() || nb.y > world.getMaxHeight()) continue;

                    Block block;
                    try {
                        block = world.getBlockAt(nb.x, nb.y, nb.z);
                    } catch (Throwable e) {
                        continue;
                    }

                    Material type = block.getType();
                    // vanilla: checks FluidTags.WATER via BucketPickup then LiquidBlock then kelp/seagrass
                    // Bukkit mapping: WATER = LiquidBlock, waterlogged = BucketPickup, kelp = special
                    if (type == Material.WATER) {
                        // true = apply physics, like Level.setBlock UPDATE_ALL
                        block.setType(Material.AIR);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    if (type == Material.BUBBLE_COLUMN) {
                        block.setType(Material.AIR);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    if (type == Material.KELP || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
                        // vanilla drops resources for these
                        try { block.breakNaturally(true); } catch (Throwable ignored) { block.setType(Material.AIR); }
                        // ensure air if breakNaturally didn't
                        if (block.getType() != Material.AIR) block.setType(Material.AIR);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    // BucketPickup path for waterlogged blocks (fluid WATER)
                    BlockData data = block.getBlockData();
                    if (data instanceof Waterlogged wl && wl.isWaterlogged()) {
                        wl.setWaterlogged(false);
                        block.setBlockData(wl);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                    }
                }
            }

            if (count > 0) {
                try {
                    world.playSound(cube.getLocation(), Sound.BLOCK_SPONGE_ABSORB, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                // vanilla: sponge -> wet_sponge after absorbing. mimic via PDC so it drains once per sponge like vanilla
                try {
                    cube.getPersistentDataContainer().set(wetKey, PersistentDataType.BYTE, (byte) 1);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            NekoPlugin.getInstance().getLogger().log(Level.WARNING, "drainAround failed for " + cube.getUniqueId(), t);
        }
    }

    private static final class BlockPos {
        final int x, y, z;
        BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos p)) return false;
            return x == p.x && y == p.y && z == p.z;
        }
        @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
    }
}
