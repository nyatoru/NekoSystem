package com.nyarutoru.nekoplugin.features.drawer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.LocationUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all drawer instances in the world.
 * Uses JSON for storage with RAM caching and auto-save every 5 minutes.
 */
public class DrawerManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long AUTO_SAVE_TICKS = 20L * 60 * 5; // 5 minutes
    private static final int MAX_SAVE_RETRIES = 3;
    private static volatile DrawerManager instance;
    private final Map<String, Drawer> drawers = new ConcurrentHashMap<>();
    private File dataFile;
    private File backupFile;
    private NekoPlugin plugin;
    private BukkitTask autoSaveTask;
    private boolean dirty = false;

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
        this.backupFile = new File(plugin.getDataFolder(), "drawers.json.backup");
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
        return LocationUtils.getLocationKey(location);
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

        for (int attempt = 1; attempt <= MAX_SAVE_RETRIES; attempt++) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }

                // Create backup of existing file before saving
                if (dataFile.exists() && dataFile.length() > 0) {
                    copyFile(dataFile, backupFile);
                }

                Map<String, DrawerData> dataMap = new HashMap<>();
                for (Map.Entry<String, Drawer> entry : drawers.entrySet()) {
                    dataMap.put(entry.getKey(), DrawerData.fromDrawer(entry.getValue()));
                }

                // Write to temporary file first
                File tempFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                    GSON.toJson(dataMap, writer);
                }

                // Atomic rename to actual file
                if (dataFile.exists()) {
                    dataFile.delete();
                }
                tempFile.renameTo(dataFile);

                dirty = false;
                return; // Success
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save drawers (attempt " + attempt + "/" + MAX_SAVE_RETRIES + ")", e);
                if (attempt == MAX_SAVE_RETRIES) {
                    plugin.getLogger().log(Level.SEVERE, "All save attempts failed! Data may be lost.", e);
                } else {
                    try {
                        Thread.sleep(100 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    public void loadAll() {
        if (plugin == null)
            return;

        drawers.clear();

        // Try to load from main file first
        if (dataFile.exists()) {
            if (loadFromFile(dataFile)) {
                plugin.getLogger().info("Loaded " + drawers.size() + " drawers.");
                return;
            }
        }

        // If main file failed, try backup
        if (backupFile.exists()) {
            plugin.getLogger().warning("Main drawer file corrupted or missing, attempting to load from backup...");
            if (loadFromFile(backupFile)) {
                plugin.getLogger().info("Successfully loaded " + drawers.size() + " drawers from backup.");
                // Save to main file to restore it
                saveAll();
                return;
            }
        }

        plugin.getLogger().info("No drawer data found or all files corrupted. Starting fresh.");
    }

    private boolean loadFromFile(File file) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
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
                return true;
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load from " + file.getName(), e);
        }
        return false;
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
