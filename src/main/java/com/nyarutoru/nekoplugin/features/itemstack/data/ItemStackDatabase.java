package com.nyarutoru.nekoplugin.features.itemstack.data;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * SQLite database manager for stacked items.
 * Uses centralized DatabaseManager for connection management.
 */
public class ItemStackDatabase {

    private static final String FEATURE_NAME = "itemstack";
    private static volatile ItemStackDatabase instance;
    private final NekoPlugin plugin;
    private final Map<UUID, StackedItemEntity> cache = new ConcurrentHashMap<>();

    private ItemStackDatabase(NekoPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStackDatabase getInstance() {
        if (instance == null) {
            synchronized (ItemStackDatabase.class) {
                if (instance == null) {
                    throw new IllegalStateException("ItemStackDatabase not initialized");
                }
            }
        }
        return instance;
    }

    public static void initialize(NekoPlugin plugin) {
        if (instance == null) {
            synchronized (ItemStackDatabase.class) {
                if (instance == null) {
                    instance = new ItemStackDatabase(plugin);
                    instance.setupDatabase();
                }
            }
        }
    }

    private void setupDatabase() {
        // Create table using DatabaseManager
        String createTableSQL = """
                    CREATE TABLE IF NOT EXISTS stacked_items (
                        id TEXT PRIMARY KEY,
                        world_name TEXT NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        item_type TEXT NOT NULL,
                        stack_size INTEGER NOT NULL,
                        item_data BLOB NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """;

        DatabaseManager.getInstance().createTable(FEATURE_NAME, createTableSQL);

        // Create indexes
        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_location ON stacked_items(world_name, x, y, z)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_world ON stacked_items(world_name)");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create indexes", e);
            }
        }

        plugin.getLogger().info("ItemStack database initialized");
    }

    /**
     * Save a stacked item to the database.
     */
    public void saveStack(StackedItemEntity stack) {
        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn == null)
            return;

        try {
            String sql = """
                        INSERT OR REPLACE INTO stacked_items (id, world_name, x, y, z, item_type, stack_size, item_data, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, stack.getId().toString());
                pstmt.setString(2, stack.getLocation().getWorld().getName());
                pstmt.setDouble(3, stack.getLocation().getX());
                pstmt.setDouble(4, stack.getLocation().getY());
                pstmt.setDouble(5, stack.getLocation().getZ());
                pstmt.setString(6, stack.getItemType().name());
                pstmt.setInt(7, stack.getStackSize());
                pstmt.setBytes(8, serializeItemStack(stack.getItemTemplate()));

                long now = System.currentTimeMillis();
                pstmt.setLong(9, now);
                pstmt.setLong(10, now);

                pstmt.executeUpdate();
            }

            cache.put(stack.getId(), stack);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stacked item", e);
        }
    }

    /**
     * Load a stack by ID.
     */
    public StackedItemEntity loadStack(UUID id) {
        // Check cache first
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn == null)
            return null;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM stacked_items WHERE id = ?")) {
            pstmt.setString(1, id.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return createEntityFromResultSet(rs);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load stacked item", e);
        }

        return null;
    }

    /**
     * Find stacks within radius of a location.
     */
    public List<StackedItemEntity> findNearby(Location center, double radius) {
        List<StackedItemEntity> nearby = new ArrayList<>();
        String worldName = center.getWorld().getName();

        double minX = center.getX() - radius;
        double maxX = center.getX() + radius;
        double minY = center.getY() - radius;
        double maxY = center.getY() + radius;
        double minZ = center.getZ() - radius;
        double maxZ = center.getZ() + radius;

        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn == null)
            return nearby;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM stacked_items WHERE world_name = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?")) {
            pstmt.setString(1, worldName);
            pstmt.setDouble(2, minX);
            pstmt.setDouble(3, maxX);
            pstmt.setDouble(4, minY);
            pstmt.setDouble(5, maxY);
            pstmt.setDouble(6, minZ);
            pstmt.setDouble(7, maxZ);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    StackedItemEntity entity = createEntityFromResultSet(rs);
                    if (entity.getLocation().distance(center) <= radius) {
                        nearby.add(entity);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to find nearby stacks", e);
        }

        return nearby;
    }

    /**
     * Remove a stack from the database.
     */
    public void removeStack(UUID id) {
        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn == null)
            return;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM stacked_items WHERE id = ?")) {
            pstmt.setString(1, id.toString());
            pstmt.executeUpdate();

            cache.remove(id);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove stacked item", e);
        }
    }

    /**
     * Load all stacks from the database.
     */
    public Map<UUID, StackedItemEntity> loadAll() {
        Map<UUID, StackedItemEntity> stacks = new HashMap<>();

        Connection conn = DatabaseManager.getInstance().getConnection(FEATURE_NAME);
        if (conn == null)
            return stacks;

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM stacked_items")) {

            while (rs.next()) {
                StackedItemEntity entity = createEntityFromResultSet(rs);
                stacks.put(entity.getId(), entity);
                cache.put(entity.getId(), entity);
            }

            plugin.getLogger().info("Loaded " + stacks.size() + " stacked items from database");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load stacked items", e);
        }

        return stacks;
    }

    /**
     * Save all cached stacks.
     */
    public void saveAll() {
        int saved = 0;
        for (StackedItemEntity stack : cache.values()) {
            saveStack(stack);
            saved++;
        }
        if (saved > 0) {
            plugin.getLogger().info("Auto-saved " + saved + " stacked items");
        }
    }

    /**
     * Shutdown and cleanup.
     */
    public void shutdown() {
        saveAll();
        cache.clear();
        // Connection is managed by DatabaseManager, no need to close here
    }

    /**
     * Create entity from ResultSet.
     */
    private StackedItemEntity createEntityFromResultSet(ResultSet rs) throws Exception {
        UUID id = UUID.fromString(rs.getString("id"));
        String worldName = rs.getString("world_name");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        int stackSize = rs.getInt("stack_size");
        byte[] itemData = rs.getBytes("item_data");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World not found: " + worldName);
        }

        Location location = new Location(world, x, y, z);
        ItemStack itemTemplate = deserializeItemStack(itemData);

        return new StackedItemEntity(id, location, itemTemplate, stackSize);
    }

    /**
     * Serialize ItemStack to byte array.
     */
    @SuppressWarnings("deprecation")
    private byte[] serializeItemStack(ItemStack item) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return outputStream.toByteArray();
        }
    }

    /**
     * Deserialize ItemStack from byte array.
     */
    @SuppressWarnings("deprecation")
    private ItemStack deserializeItemStack(byte[] data) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        }
    }

    public Map<UUID, StackedItemEntity> getCache() {
        return cache;
    }
}
