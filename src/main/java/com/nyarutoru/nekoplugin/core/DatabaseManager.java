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
 */
public class DatabaseManager {

    private static volatile DatabaseManager instance;

    // Store connections for each feature database
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private NekoPlugin plugin;
    private File databaseFolder;

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
     *
     * @param featureName The name of the feature (used as database filename)
     * @return The SQLite connection for this feature
     */
    public Connection getConnection(String featureName) {
        return connections.computeIfAbsent(featureName, this::createConnection);
    }

    /**
     * Create a new database connection for a feature.
     */
    private Connection createConnection(String featureName) {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Connect to feature-specific database
            File dbFile = new File(databaseFolder, featureName + ".db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            Connection connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrent access
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
            }

            plugin.getLogger().info("SQLite database connected: " + dbFile.getName());
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
     */
    public void createTable(String featureName, String createSQL) {
        Connection conn = getConnection(featureName);
        if (conn == null)
            return;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create table for " + featureName, e);
        }
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
     */
    public void shutdown() {
        for (String featureName : connections.keySet()) {
            closeConnection(featureName);
        }
        plugin.getLogger().info("All database connections closed.");
    }

    /**
     * Get the database folder path.
     */
    public File getDatabaseFolder() {
        return databaseFolder;
    }
}
