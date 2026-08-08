package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

final class GraveRepository {
    private static final String DATABASE = "graves";
    private final NekoPlugin plugin;

    GraveRepository(NekoPlugin plugin) { this.plugin = plugin; }

    boolean initialize() {
        DatabaseManager.getInstance().createTable(DATABASE, """
            CREATE TABLE IF NOT EXISTS graves (
                id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, owner_name TEXT NOT NULL,
                death_world_id TEXT NOT NULL, death_world_name TEXT NOT NULL, death_x INTEGER NOT NULL, death_y INTEGER NOT NULL, death_z INTEGER NOT NULL,
                grave_world_id TEXT NOT NULL, grave_world_name TEXT NOT NULL, grave_x INTEGER NOT NULL, grave_y INTEGER NOT NULL, grave_z INTEGER NOT NULL,
                items BLOB NOT NULL, experience INTEGER NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL,
                state TEXT NOT NULL DEFAULT 'ACTIVE', disposition TEXT NOT NULL DEFAULT 'NONE'
            )
            """);
        Connection connection = connection();
        if (connection == null) return false;
        try {
            addColumnIfMissing(connection, "state", "TEXT NOT NULL DEFAULT 'ACTIVE'");
            addColumnIfMissing(connection, "disposition", "TEXT NOT NULL DEFAULT 'NONE'");
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate grave database", exception);
            return false;
        }
    }

    List<Grave> loadAll() {
        List<Grave> graves = new ArrayList<>();
        Connection connection = connection();
        if (connection == null) return graves;
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM graves"); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                graves.add(new Grave(UUID.fromString(result.getString("id")), UUID.fromString(result.getString("owner_id")),
                    result.getString("owner_name"), position(result, "death"), position(result, "grave"),
                    GraveItemCodec.decode(result.getBytes("items")), result.getInt("experience"),
                    result.getLong("created_at"), result.getLong("expires_at"),
                    Grave.State.valueOf(result.getString("state")), Grave.Disposition.valueOf(result.getString("disposition"))));
            }
        } catch (RuntimeException | SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load graves", exception);
        }
        return graves;
    }

    boolean save(GraveSnapshot grave) {
        Connection connection = connection();
        if (connection == null) return false;
        String sql = """
            INSERT OR REPLACE INTO graves (id, owner_id, owner_name, death_world_id, death_world_name, death_x, death_y, death_z,
                grave_world_id, grave_world_name, grave_x, grave_y, grave_z, items, experience, created_at, expires_at, state, disposition)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, grave.id().toString());
            statement.setString(index++, grave.ownerId().toString());
            statement.setString(index++, grave.ownerName());
            index = setPosition(statement, index, grave.deathPosition());
            index = setPosition(statement, index, grave.gravePosition());
            statement.setBytes(index++, grave.items());
            statement.setInt(index++, grave.experience());
            statement.setLong(index++, grave.createdAt());
            statement.setLong(index++, grave.expiresAt());
            statement.setString(index++, grave.state().name());
            statement.setString(index, grave.disposition().name());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save grave " + grave.id(), exception);
            return false;
        }
    }

    boolean delete(UUID id) {
        Connection connection = connection();
        if (connection == null) return false;
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM graves WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete grave " + id, exception);
            return false;
        }
    }

    void close() { DatabaseManager.getInstance().closeConnection(DATABASE); }
    private Connection connection() { return DatabaseManager.getInstance().getConnection(DATABASE); }

    private static void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, "graves", column)) {
            if (columns.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE graves ADD COLUMN " + column + " " + definition);
        }
    }

    private static GravePosition position(ResultSet result, String prefix) throws SQLException {
        return new GravePosition(UUID.fromString(result.getString(prefix + "_world_id")), result.getString(prefix + "_world_name"),
            result.getInt(prefix + "_x"), result.getInt(prefix + "_y"), result.getInt(prefix + "_z"));
    }

    private static int setPosition(PreparedStatement statement, int index, GravePosition position) throws SQLException {
        statement.setString(index++, position.worldId().toString());
        statement.setString(index++, position.worldName());
        statement.setInt(index++, position.x());
        statement.setInt(index++, position.y());
        statement.setInt(index++, position.z());
        return index;
    }
}
