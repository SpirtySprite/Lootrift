package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.common.storage.SqlRepository;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.block.Block;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CratePlacementRepository extends SqlRepository<String> {

    private final Map<String, CratePlacement> placements = new ConcurrentHashMap<>();

    public CratePlacementRepository(Database database) {
        super(database);
    }

    @Override
    protected void schema(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS crate_placements ("
                + "id TEXT PRIMARY KEY,"
                + "crate TEXT NOT NULL,"
                + "world TEXT NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL,"
                + "hologram TEXT)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_placements_crate "
                + "ON crate_placements(crate)");
        if (!hasColumn(statement, "yaw")) {
            statement.execute("ALTER TABLE crate_placements ADD COLUMN yaw REAL NOT NULL DEFAULT 0");
        }
    }

    private boolean hasColumn(Statement statement, String column) throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(crate_placements)")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void readAll(Connection connection) throws SQLException {
        placements.clear();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT id, crate, world, x, y, z, hologram, yaw FROM crate_placements")) {
            while (results.next()) {
                CratePlacement placement = new CratePlacement(
                        results.getString(2),
                        results.getString(3),
                        results.getInt(4),
                        results.getInt(5),
                        results.getInt(6),
                        parse(results.getString(7)),
                        (float) results.getDouble(8));
                placements.put(results.getString(1), placement);
            }
        }
    }

    @Override
    protected void persistAll(Connection connection, Set<String> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO crate_placements(id, crate, world, x, y, z, hologram, yaw) "
                        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                        + "crate=excluded.crate, world=excluded.world, x=excluded.x, "
                        + "y=excluded.y, z=excluded.z, hologram=excluded.hologram, yaw=excluded.yaw")) {
            for (String id : keys) {
                CratePlacement placement = placements.get(id);
                if (placement == null) {
                    continue;
                }
                statement.setString(1, id);
                statement.setString(2, placement.crate());
                statement.setString(3, placement.world());
                statement.setInt(4, placement.x());
                statement.setInt(5, placement.y());
                statement.setInt(6, placement.z());
                statement.setString(7, placement.hologram() == null
                        ? null : placement.hologram().toString());
                statement.setDouble(8, placement.yaw());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    protected void eraseAll(Connection connection, Set<String> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM crate_placements WHERE id=?")) {
            for (String id : keys) {
                statement.setString(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public Optional<CratePlacement> at(Block block) {
        return Optional.ofNullable(placements.get(CratePlacement.idOf(block)));
    }

    public Optional<CratePlacement> byId(String id) {
        return Optional.ofNullable(placements.get(id));
    }

    public boolean contains(Block block) {
        return placements.containsKey(CratePlacement.idOf(block));
    }

    public void save(CratePlacement placement) {
        placements.put(placement.id(), placement);
        markDirty(placement.id());
    }

    public boolean remove(String id) {
        if (placements.remove(id) == null) {
            return false;
        }
        markRemoved(id);
        return true;
    }

    public List<CratePlacement> all() {
        return List.copyOf(placements.values());
    }

    public Collection<CratePlacement> view() {
        return Collections.unmodifiableCollection(placements.values());
    }

    public List<CratePlacement> of(String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        List<CratePlacement> matching = new ArrayList<>();
        for (CratePlacement placement : placements.values()) {
            if (placement.crate().equals(id)) {
                matching.add(placement);
            }
        }
        return matching;
    }

    public int count() {
        return placements.size();
    }

    public int count(String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        int total = 0;
        for (CratePlacement placement : placements.values()) {
            if (placement.crate().equals(id)) {
                total++;
            }
        }
        return total;
    }

    public List<CratePlacement> removeAll(String crate) {
        List<CratePlacement> removed = of(crate);
        for (CratePlacement placement : removed) {
            remove(placement.id());
        }
        return removed;
    }

    private static UUID parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }
}
