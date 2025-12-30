package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

/**
 * Centralized SQLite database manager.
 * Provides connection management and table creation for all features.
 */
public class DatabaseManager {

    private static volatile DatabaseManager instance;
    private Connection connection;
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
     * Initialize the database connection.
     * Creates the database folder and file if they don't exist.
     */
    public void initialize(NekoPlugin plugin) {
        this.plugin = plugin;
        this.databaseFolder = new File(plugin.getDataFolder(), "database");

        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }

        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Connect to database
            File dbFile = new File(databaseFolder, "drawers.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrent access
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
            }

            plugin.getLogger().info("SQLite database connected: " + dbFile.getName());
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database!", e);
        }
    }

    /**
     * Get the database connection.
     * The connection is shared across all features.
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize(plugin);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database connection check failed", e);
        }
        return connection;
    }

    /**
     * Execute a table creation statement.
     */
    public void createTable(String createSQL) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSQL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create table", e);
        }
    }

    /**
     * Check if the database is connected.
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Shutdown the database connection.
     */
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close database connection", e);
            }
        }
    }

    /**
     * Get the database folder path.
     */
    public File getDatabaseFolder() {
        return databaseFolder;
    }
}
