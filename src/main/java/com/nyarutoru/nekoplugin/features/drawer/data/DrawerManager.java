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

    private static final String DATABASE_NAME = "drawers";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long AUTO_SAVE_TICKS = 20L * 60 * 5; // 5 minutes
    private static volatile DrawerManager instance;

    private final Map<String, Drawer> drawers = new ConcurrentHashMap<>();
    private NekoPlugin plugin;
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

        DatabaseManager.getInstance().createTable(DATABASE_NAME, createTableSQL);

        // Create index for faster lookups
        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_drawers_location ON drawers(world, x, y, z)";
        Connection conn = getConnection();
        if (conn == null)
            return;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createIndexSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create index", e);
        }
    }

    /**
     * Get the database connection for drawers.
     */
    private Connection getConnection() {
        return DatabaseManager.getInstance().getConnection(DATABASE_NAME);
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
            Connection conn = getConnection();
            if (conn == null)
                return;

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
        // Run on global timer - saveAll() is fully async so no blocking
        SchedulerUtils.runGlobalTimer(() -> {
            if (dirty) {
                saveAll(); // Fully async, won't block
            }
        }, AUTO_SAVE_TICKS, AUTO_SAVE_TICKS);
    }

    private String locationKey(Location location) {
        return LocationUtils.getLocationKey(location);
    }

    public Drawer createDrawer(Location location, DrawerTier tier) {
        if (location == null) {
            plugin.getLogger().warning("Cannot create drawer at null location");
            return null;
        }
        
        if (location.getWorld() == null) {
            plugin.getLogger().warning("Cannot create drawer in unloaded world");
            return null;
        }
        
        if (tier == null) {
            plugin.getLogger().warning("Cannot create drawer with null tier");
            return null;
        }
        
        String key = locationKey(location);
        if (drawers.containsKey(key)) {
            plugin.getLogger().warning("Drawer already exists at " + location);
            return null;
        }

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
        if (drawer == null) {
            return;
        }
        
        Location loc = drawer.getLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Cannot insert drawer with null location or world");
            return;
        }
        
        String sql = "INSERT OR REPLACE INTO drawers (world, x, y, z, item_type, item_count, tier) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = getConnection();
        if (conn == null)
            return;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, drawer.getItemType() != null ? drawer.getItemType().name() : null);
            stmt.setInt(6, drawer.getItemCount());
            stmt.setString(7, drawer.getTier() != null ? drawer.getTier().name() : "TIER_1");
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to insert drawer at " + loc, e);
        }
    }

    private void deleteDrawer(Drawer drawer) {
        if (drawer == null) {
            return;
        }
        
        Location loc = drawer.getLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Cannot delete drawer with null location or world");
            return;
        }
        
        String sql = "DELETE FROM drawers WHERE world = ? AND x = ? AND y = ? AND z = ?";
        Connection conn = getConnection();
        if (conn == null)
            return;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete drawer at " + loc, e);
        }
    }

    /**
     * Saves all drawers to database asynchronously.
     * This method is FULLY ASYNC - it does not block the server thread at all.
     * Database operations are batched for performance.
     */
    public void saveAll() {
        if (plugin == null || !DatabaseManager.getInstance().isConnected(DATABASE_NAME))
            return;

        if (!dirty) {
            return; // Nothing to save
        }

        // Create snapshot of current drawer state (thread-safe copy)
        // This is fast and doesn't block much
        final Map<String, Drawer> snapshot = new HashMap<>(drawers);
        dirty = false; // Reset dirty flag immediately

        // Execute all database work async to prevent blocking
        SchedulerUtils.runAsync(() -> {
            Connection conn = getConnection();
            if (conn == null) {
                plugin.getLogger().warning("Failed to get database connection for drawer auto-save");
                return;
            }

            String sql = "INSERT OR REPLACE INTO drawers (world, x, y, z, item_type, item_count, tier) VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);

                int batchSize = 0;
                for (Drawer drawer : snapshot.values()) {
                    Location loc = drawer.getLocation();
                    stmt.setString(1, loc.getWorld().getName());
                    stmt.setInt(2, loc.getBlockX());
                    stmt.setInt(3, loc.getBlockY());
                    stmt.setInt(4, loc.getBlockZ());
                    stmt.setString(5, drawer.getItemType() != null ? drawer.getItemType().name() : null);
                    stmt.setInt(6, drawer.getItemCount());
                    stmt.setString(7, drawer.getTier().name());
                    stmt.addBatch();
                    batchSize++;

                    // Execute batch every 100 items to prevent memory issues
                    if (batchSize >= 100) {
                        stmt.executeBatch();
                        batchSize = 0;
                    }
                }

                // Execute remaining items
                if (batchSize > 0) {
                    stmt.executeBatch();
                }

                conn.commit();
                conn.setAutoCommit(true);

                // Log success (async, won't spam console)
                plugin.getLogger().info("Auto-saved " + snapshot.size() + " drawers asynchronously.");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save drawers asynchronously", e);
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
                }
            }
        });
    }

    public void loadAll() {
        if (plugin == null || !DatabaseManager.getInstance().isConnected(DATABASE_NAME)) {
            if (plugin != null) {
                plugin.getLogger().warning("Cannot load drawers: plugin or database not initialized");
            }
            return;
        }

        drawers.clear();

        String sql = "SELECT world, x, y, z, item_type, item_count, tier FROM drawers";
        Server server = plugin.getServer();
        Connection conn = getConnection();
        if (conn == null) {
            plugin.getLogger().warning("Cannot load drawers: database connection unavailable");
            return;
        }

        int loaded = 0;
        int skipped = 0;

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                try {
                    String worldName = rs.getString("world");
                    if (worldName == null || worldName.equals("unknown")) {
                        skipped++;
                        continue; // Skip invalid world data
                    }
                    
                    World world = server.getWorld(worldName);

                    if (world == null) {
                        plugin.getLogger().info("Skipping drawer in unloaded world: " + worldName);
                        skipped++;
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
                    loaded++;
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load drawer from row, skipping", e);
                    skipped++;
                }
            }

            plugin.getLogger().info("Loaded " + loaded + " drawers from database" + 
                    (skipped > 0 ? " (skipped " + skipped + " invalid entries)" : "") + ".");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load drawers from database", e);
        }
    }

    public void shutdown() {
        // On shutdown, save synchronously to ensure all data is saved
        saveAllSync();
        drawers.clear();

        // Close database connection
        DatabaseManager.getInstance().closeConnection(DATABASE_NAME);
    }

    /**
     * Synchronous save for shutdown - blocks until complete.
     * Only used during plugin disable to ensure data integrity.
     */
    private void saveAllSync() {
        if (plugin == null || !DatabaseManager.getInstance().isConnected(DATABASE_NAME))
            return;

        Connection conn = getConnection();
        if (conn == null)
            return;

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

            plugin.getLogger().info("Saved " + drawers.size() + " drawers on shutdown.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save drawers on shutdown", e);
            try {
                conn.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
        }
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
