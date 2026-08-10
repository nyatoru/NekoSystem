package com.nyarutoru.nekoplugin.features.sulfurcube;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SulfurCubeContent;
import io.papermc.paper.event.entity.SulfurCubeSwallowItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    public SulfurCubeFeature() {
        super("sulfurcube", "Sulfur Cube Sponge");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
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

    // immediate drain when swallowing sponge
    @EventHandler(ignoreCancelled = true)
    public void onSwallow(SulfurCubeSwallowItemEvent event) {
        ItemStack newItem = event.getNewItem();
        if (newItem != null && newItem.getType() == Material.SPONGE) {
            SulfurCube cube = event.getEntity();
            tracked.add(cube);
            SchedulerUtils.runAtEntityLater(cube, () -> {
                try {
                    if (cube.isValid() && isSpongeCube(cube)) {
                        SchedulerUtils.runAtLocation(cube.getLocation(), () -> drainAround(cube));
                    }
                } catch (Throwable t) {
                    NekoPlugin.getInstance().getLogger().log(Level.WARNING, "SulfurCube swallow drain failed", t);
                }
            }, 1L);
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
            SchedulerUtils.runAtLocation(cube.getLocation(), () -> {
                try {
                    if (!cube.isValid()) {
                        tracked.remove(cube);
                        return;
                    }
                    if (!isSpongeCube(cube)) return;
                    if (!hasNearbyWater(cube)) return;
                    drainAround(cube);
                } catch (Throwable t) {
                    NekoPlugin.getInstance().getLogger().log(Level.WARNING, "SulfurCube drain failed", t);
                }
            });
        }
    }

    private static boolean hasNearbyWater(SulfurCube cube) {
        try {
            World world = cube.getWorld();
            int bx = cube.getLocation().getBlockX();
            int by = cube.getLocation().getBlockY();
            int bz = cube.getLocation().getBlockZ();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                        Material type = b.getType();
                        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) return true;
                        BlockData data = b.getBlockData();
                        if (data instanceof Waterlogged wl && wl.isWaterlogged()) return true;
                        if (type == Material.KELP || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private static boolean isSpongeCube(SulfurCube cube) {
        try {
            SulfurCubeContent content = cube.getData(DataComponentTypes.SULFUR_CUBE_CONTENT);
            if (content == null) return false;
            ItemStack item = content.absorbedItem();
            if (item == null) return false;
            return item.getType() == Material.SPONGE;
        } catch (Throwable t) {
            return false;
        }
    }

    // must be called on region thread for the cube's location
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
                    if (type == Material.WATER) {
                        block.setType(Material.AIR, false);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    if (type == Material.BUBBLE_COLUMN) {
                        block.setType(Material.AIR, false);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    if (type == Material.KELP || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
                        block.setType(Material.AIR, false);
                        count++;
                        queue.add(nb);
                        if (count >= MAX_BLOCKS) break;
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data instanceof Waterlogged wl && wl.isWaterlogged()) {
                        wl.setWaterlogged(false);
                        block.setBlockData(wl, false);
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
