package com.nyarutoru.nekoplugin.features.drawer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.DatabaseManager;
import com.nyarutoru.nekoplugin.utils.LocationUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all drawer instances in the world.
 * Uses SQLite for storage with RAM caching and auto-save every 5 minutes.
 * Automatically migrates from legacy JSON format if found.
 */
public class DrawerManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long AUTO_SAVE_TICKS = 20L * 60 * 5; // 5 minutes
    private static volatile DrawerManager instance;

    private final Map<String, Drawer> drawers = new ConcurrentHashMap<>();
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

        // Initialize database table
        initializeDatabase();

        // Check for legacy JSON and migrate if needed
        migrateFromJson();

        // Load all drawers from database
        loadAll();

        // Start auto-save
        startAutoSave();
    }

    private void initializeDatabase() {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS drawers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    item_type TEXT,
                    item_count INTEGER DEFAULT 0,
                    tier TEXT NOT NULL DEFAULT 'TIER_1',
                    UNIQUE(world, x, y, z)
                )
                """;

        DatabaseManager.getInstance().createTable(createTableSQL);

        // Create index for faster lookups
        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_drawers_location ON drawers(world, x, y, z)";
        try (Statement stmt = DatabaseManager.getInstance().getConnection().createStatement()) {
            stmt.execute(createIndexSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create index", e);
        }
    }

    /**
     * Migrate from legacy JSON format to SQLite.
     */
    private void migrateFromJson() {
        File jsonFile = new File(plugin.getDataFolder(), "drawers.json");
        if (!jsonFile.exists()) {
            return;
        }

        plugin.getLogger().info("Found legacy drawers.json, migrating to SQLite...");

        try (Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, LegacyDrawerData>>() {
            }.getType();
            Map<String, LegacyDrawerData> dataMap = GSON.fromJson(reader, type);

            if (dataMap == null || dataMap.isEmpty()) {
                plugin.getLogger().info("No data in legacy JSON file, skipping migration.");
                renameJsonFile(jsonFile);
                return;
            }

            int migrated = 0;
            Connection conn = DatabaseManager.getInstance().getConnection();
            String insertSQL = "INSERT OR REPLACE INTO drawers (world, x, y, z, item_type, item_count, tier) VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                conn.setAutoCommit(false);

                for (LegacyDrawerData data : dataMap.values()) {
                    stmt.setString(1, data.world);
                    stmt.setInt(2, data.x);
                    stmt.setInt(3, data.y);
                    stmt.setInt(4, data.z);
                    stmt.setString(5, data.itemType);
                    stmt.setInt(6, data.itemCount);
                    stmt.setString(7, data.tier != null ? data.tier : "TIER_1");
                    stmt.addBatch();
                    migrated++;
                }

                stmt.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
            }

            plugin.getLogger().info("Successfully migrated " + migrated + " drawers from JSON to SQLite.");
            renameJsonFile(jsonFile);

        } catch (IOException | JsonSyntaxException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate from JSON", e);
        }
    }

    private void renameJsonFile(File jsonFile) {
        File migratedFile = new File(jsonFile.getParentFile(), "drawers.json.migrated");
        if (jsonFile.renameTo(migratedFile)) {
            plugin.getLogger().info("Renamed legacy JSON file to drawers.json.migrated");
        }

        // Also rename backup if exists
        File backupFile = new File(plugin.getDataFolder(), "drawers.json.backup");
        if (backupFile.exists()) {
            File migratedBackup = new File(backupFile.getParentFile(), "drawers.json.backup.migrated");
            backupFile.renameTo(migratedBackup);
        }
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

        // Insert into database
        insertDrawer(drawer);

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
        if (removed != null) {
            deleteDrawer(removed);
        }
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

    // ==================== DATABASE OPERATIONS ====================

    private void insertDrawer(Drawer drawer) {
        String sql = "INSERT OR REPLACE INTO drawers (world, x, y, z, item_type, item_count, tier) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            Location loc = drawer.getLocation();
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, drawer.getItemType() != null ? drawer.getItemType().name() : null);
            stmt.setInt(6, drawer.getItemCount());
            stmt.setString(7, drawer.getTier().name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to insert drawer", e);
        }
    }

    private void deleteDrawer(Drawer drawer) {
        String sql = "DELETE FROM drawers WHERE world = ? AND x = ? AND y = ? AND z = ?";

        try (PreparedStatement stmt = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            Location loc = drawer.getLocation();
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete drawer", e);
        }
    }

    public synchronized void saveAll() {
        if (plugin == null || !DatabaseManager.getInstance().isConnected())
            return;

        Connection conn = DatabaseManager.getInstance().getConnection();
        String sql = "INSERT OR REPLACE INTO drawers (world, x, y, z, item_type, item_count, tier) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);

            for (Drawer drawer : drawers.values()) {
                Location loc = drawer.getLocation();
                stmt.setString(1, loc.getWorld().getName());
                stmt.setInt(2, loc.getBlockX());
                stmt.setInt(3, loc.getBlockY());
                stmt.setInt(4, loc.getBlockZ());
                stmt.setString(5, drawer.getItemType() != null ? drawer.getItemType().name() : null);
                stmt.setInt(6, drawer.getItemCount());
                stmt.setString(7, drawer.getTier().name());
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
            dirty = false;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save drawers", e);
            try {
                conn.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
        }
    }

    public void loadAll() {
        if (plugin == null || !DatabaseManager.getInstance().isConnected())
            return;

        drawers.clear();

        String sql = "SELECT world, x, y, z, item_type, item_count, tier FROM drawers";
        Server server = plugin.getServer();

        try (Statement stmt = DatabaseManager.getInstance().getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String worldName = rs.getString("world");
                World world = server.getWorld(worldName);

                if (world == null) {
                    continue; // World not loaded
                }

                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String itemTypeName = rs.getString("item_type");
                int itemCount = rs.getInt("item_count");
                String tierName = rs.getString("tier");

                Location location = new Location(world, x, y, z);
                Material itemType = itemTypeName != null ? Material.getMaterial(itemTypeName) : null;
                DrawerTier tier = DrawerTier.getByName(tierName);

                Drawer drawer = new Drawer(location, itemType, itemCount, tier);
                drawers.put(locationKey(location), drawer);
            }

            plugin.getLogger().info("Loaded " + drawers.size() + " drawers from database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load drawers", e);
        }
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        saveAll();
        drawers.clear();
    }

    /**
     * Legacy drawer data structure for JSON migration.
     */
    private static class LegacyDrawerData {
        String world;
        int x, y, z;
        String itemType;
        int itemCount;
        String tier;
    }
}
