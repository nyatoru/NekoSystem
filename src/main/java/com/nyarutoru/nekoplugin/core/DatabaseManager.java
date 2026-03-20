package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centralized SQLite database manager.
 * Provides connection management for multiple feature databases.
 * Each feature can have its own database file.
 * <p>
 * Features:
 * - Automatic connection validation and reconnection
 * - Connection pooling per feature
 * - WAL mode for better concurrent access
 * - Graceful shutdown with proper resource cleanup
 */
public class DatabaseManager {

    private static volatile DatabaseManager instance;

    // Store connections for each feature database
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private NekoPlugin plugin;
    private File databaseFolder;

    // Track failed connection attempts to avoid spam
    private final Map<String, Long> lastFailedConnection = new ConcurrentHashMap<>();
    private static final long RECONNECT_DELAY_MS = 5000; // 5 second delay between reconnect attempts

    private DatabaseManager() {
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the database manager.
     * Only creates the database folder, feature databases are initialized on
     * demand.
     */
    public void initialize(NekoPlugin plugin) {
        this.plugin = plugin;
        this.databaseFolder = new File(plugin.getDataFolder(), "database");

        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }

        plugin.getLogger().info("Database manager initialized.");
    }

    /**
     * Get or create a database connection for a specific feature.
     * Validates existing connections and attempts reconnection if needed.
     *
     * @param featureName The name of the feature (used as database filename)
     * @return The SQLite connection for this feature, or null if connection fails
     */
    public Connection getConnection(String featureName) {
        Connection existing = connections.get(featureName);

        // Validate existing connection
        if (existing != null) {
            try {
                if (!existing.isClosed()) {
                    // Connection is valid, test it with a quick query
                    if (existing.isValid(1)) {
                        return existing;
                    }
                }
                // Connection is closed or invalid, remove it and recreate
                connections.remove(featureName);
                existing.close(); // Clean up the closed connection
            } catch (SQLException e) {
                // Connection is broken, remove and recreate
                connections.remove(featureName);
                try {
                    existing.close();
                } catch (SQLException ex) {
                    // Ignore close errors
                }
                plugin.getLogger().log(java.util.logging.Level.FINE,
                    "Connection validation failed for " + featureName + ", reconnecting...", e);
            }
        }

        // Check if we're in reconnect cooldown
        Long lastFail = lastFailedConnection.get(featureName);
        if (lastFail != null && (System.currentTimeMillis() - lastFail) < RECONNECT_DELAY_MS) {
            return null; // Still in cooldown
        }

        // Create new connection
        Connection newConn = createConnection(featureName);
        if (newConn != null) {
            connections.put(featureName, newConn);
            lastFailedConnection.remove(featureName); // Clear failure tracking
        } else {
            lastFailedConnection.put(featureName, System.currentTimeMillis());
        }
        return newConn;
    }

    /**
     * Create a new database connection for a feature.
     */
    private Connection createConnection(String featureName) {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create feature-specific folder: database/feature-name/
            File featureFolder = new File(databaseFolder, featureName);
            if (!featureFolder.exists()) {
                featureFolder.mkdirs();
            }

            // Connect to feature-specific database: database/feature-name/feature.db
            File dbFile = new File(featureFolder, featureName + ".db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            Connection connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrent access
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
            }

            plugin.getLogger().info("SQLite database connected: " + featureName + "/" + dbFile.getName());
            return connection;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database: " + featureName, e);
        }
        return null;
    }

    /**
     * Execute a table creation statement on a feature database.
     * Automatically reconnects if the connection is lost.
     */
    public void createTable(String featureName, String createSQL) {
        Connection conn = getConnection(featureName);
        if (conn == null) {
            plugin.getLogger().log(Level.WARNING,
                "Cannot create table for " + featureName + ": no database connection");
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSQL);
            plugin.getLogger().log(Level.FINE, "Table created successfully for " + featureName);
        } catch (SQLException e) {
            // Check if connection was lost and try to reconnect once
            if (isConnectionLost(e)) {
                connections.remove(featureName); // Force reconnection
                plugin.getLogger().log(Level.INFO,
                    "Connection lost for " + featureName + ", attempting reconnect...", e);
                conn = getConnection(featureName);
                if (conn != null) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(createSQL);
                        plugin.getLogger().log(Level.FINE,
                            "Table created successfully after reconnect for " + featureName);
                        return;
                    } catch (SQLException e2) {
                        plugin.getLogger().log(Level.SEVERE,
                            "Failed to create table for " + featureName + " after reconnect", e2);
                    }
                }
            } else {
                plugin.getLogger().log(Level.SEVERE, "Failed to create table for " + featureName, e);
            }
        }
    }

    /**
     * Check if a SQLException indicates a lost connection.
     */
    private boolean isConnectionLost(SQLException e) {
        String sqlState = e.getSQLState();
        // SQLite error codes for connection issues
        return "08000".equals(sqlState) || // Connection exception
               "08003".equals(sqlState) || // Connection does not exist
               "08006".equals(sqlState);   // Connection failure
    }

    /**
     * Check if a feature's database is connected.
     */
    public boolean isConnected(String featureName) {
        Connection conn = connections.get(featureName);
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Close a specific feature's database connection.
     */
    public void closeConnection(String featureName) {
        Connection conn = connections.remove(featureName);
        if (conn != null) {
            try {
                conn.close();
                plugin.getLogger().info("Closed database connection: " + featureName);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close database: " + featureName, e);
            }
        }
    }

    /**
     * Shutdown all database connections.
     * Clears all connection state and failure tracking.
     */
    public void shutdown() {
        for (String featureName : connections.keySet()) {
            closeConnection(featureName);
        }
        connections.clear();
        lastFailedConnection.clear();
        plugin.getLogger().info("All database connections closed.");
    }
}
