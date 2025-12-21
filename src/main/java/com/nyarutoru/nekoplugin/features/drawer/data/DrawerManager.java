package com.nyarutoru.nekoplugin.features.drawer.data;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all drawer instances in the world.
 * Uses JSON for storage with RAM caching and auto-save every 5 minutes.
 */
public class DrawerManager {

    private static volatile DrawerManager instance;
    private final Map<String, Drawer> drawers = new ConcurrentHashMap<>();
    private File dataFile;
    private NekoPlugin plugin;
    private BukkitTask autoSaveTask;
    private boolean dirty = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long AUTO_SAVE_TICKS = 20L * 60 * 5;

    private DrawerManager() {
    }

    public static DrawerManager getInstance() {
        if (instance == null) {
            synchronized (DrawerManager.class) {
                if (instance == null) {
                    instance = new DrawerManager();
                }
            }
        }
        return instance;
    }

    public void initialize(NekoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "drawers.json");
        loadAll();
        startAutoSave();
    }

    private void startAutoSave() {
        autoSaveTask = SchedulerUtils.runAsyncTimer(() -> {
            if (dirty) {
                saveAll();
                plugin.getLogger().info("Auto-saved " + drawers.size() + " drawers.");
            }
        }, AUTO_SAVE_TICKS, AUTO_SAVE_TICKS);
    }

    private String locationKey(Location location) {
        // Create a deterministic UUID based on world and coordinates
        String rawKey = location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
        return UUID.nameUUIDFromBytes(rawKey.getBytes()).toString();
    }

    public Drawer createDrawer(Location location, DrawerTier tier) {
        String key = locationKey(location);
        if (drawers.containsKey(key))
            return null;

        Drawer drawer = new Drawer(location);
        drawer.setTier(tier);
        drawers.put(key, drawer);
        markDirty();
        return drawer;
    }

    public Drawer createDrawer(Location location) {
        return createDrawer(location, DrawerTier.TIER_1);
    }

    public Drawer getDrawer(Location location) {
        return drawers.get(locationKey(location));
    }

    public boolean isDrawer(Location location) {
        return drawers.containsKey(locationKey(location));
    }

    public Drawer removeDrawer(Location location) {
        Drawer removed = drawers.remove(locationKey(location));
        if (removed != null)
            markDirty();
        return removed;
    }

    public int getDrawerCount() {
        return drawers.size();
    }

    public Map<String, Drawer> getAllDrawers() {
        return new HashMap<>(drawers);
    }

    public void markDirty() {
        this.dirty = true;
    }

    public synchronized void saveAll() {
        if (plugin == null)
            return;

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            Map<String, DrawerData> dataMap = new HashMap<>();
            for (Map.Entry<String, Drawer> entry : drawers.entrySet()) {
                dataMap.put(entry.getKey(), DrawerData.fromDrawer(entry.getValue()));
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                GSON.toJson(dataMap, writer);
            }

            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save drawers!", e);
        }
    }

    public void loadAll() {
        if (plugin == null || !dataFile.exists())
            return;

        drawers.clear();

        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, DrawerData>>() {
            }.getType();
            Map<String, DrawerData> dataMap = GSON.fromJson(reader, type);

            if (dataMap != null) {
                for (Map.Entry<String, DrawerData> entry : dataMap.entrySet()) {
                    Drawer drawer = entry.getValue().toDrawer(plugin.getServer());
                    if (drawer != null) {
                        drawers.put(entry.getKey(), drawer);
                    }
                }
            }

            plugin.getLogger().info("Loaded " + drawers.size() + " drawers.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load drawers!", e);
        }
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        saveAll();
        drawers.clear();
    }

    private static class DrawerData {
        String world;
        int x, y, z;
        String itemType;
        int itemCount;
        String tier;

        static DrawerData fromDrawer(Drawer drawer) {
            DrawerData data = new DrawerData();
            data.world = drawer.getLocation().getWorld().getName();
            data.x = drawer.getLocation().getBlockX();
            data.y = drawer.getLocation().getBlockY();
            data.z = drawer.getLocation().getBlockZ();
            data.itemType = drawer.getItemType() != null ? drawer.getItemType().name() : null;
            data.itemCount = drawer.getItemCount();
            data.tier = drawer.getTier().name();
            return data;
        }

        Drawer toDrawer(org.bukkit.Server server) {
            org.bukkit.World world = server.getWorld(this.world);
            if (world == null)
                return null;

            Location location = new Location(world, x, y, z);
            Material itemType = this.itemType != null ? Material.getMaterial(this.itemType) : null;
            DrawerTier tier = DrawerTier.getByName(this.tier);

            return new Drawer(location, itemType, itemCount, tier);
        }
    }
}
