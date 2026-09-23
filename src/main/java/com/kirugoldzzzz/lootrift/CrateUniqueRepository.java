package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.common.storage.SqlRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrateUniqueRepository extends SqlRepository<CrateUniqueRepository.Key> {

    private final Map<Key, Boolean> claimed = new ConcurrentHashMap<>();

    public CrateUniqueRepository(Database database) {
        super(database);
    }

    @Override
    protected void schema(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS crate_unique ("
                + "owner TEXT NOT NULL,"
                + "crate TEXT NOT NULL,"
                + "reward TEXT NOT NULL,"
                + "at INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(owner, crate, reward))");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_unique_owner "
                + "ON crate_unique(owner, crate)");
    }

    @Override
    protected void readAll(Connection connection) throws SQLException {
        claimed.clear();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT owner, crate, reward FROM crate_unique")) {
            while (results.next()) {
                try {
                    claimed.put(new Key(UUID.fromString(results.getString(1)),
                            results.getString(2).toLowerCase(Locale.ROOT),
                            results.getString(3)), Boolean.TRUE);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    protected void persistAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO crate_unique(owner, crate, reward, at) VALUES(?,?,?,?) "
                        + "ON CONFLICT(owner, crate, reward) DO NOTHING")) {
            long now = System.currentTimeMillis();
            for (Key key : keys) {
                statement.setString(1, key.owner().toString());
                statement.setString(2, key.crate());
                statement.setString(3, key.reward());
                statement.setLong(4, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    protected void eraseAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM crate_unique WHERE owner=? AND crate=? AND reward=?")) {
            for (Key key : keys) {
                statement.setString(1, key.owner().toString());
                statement.setString(2, key.crate());
                statement.setString(3, key.reward());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean has(UUID owner, String crate, String reward) {
        return claimed.containsKey(new Key(owner, crate.toLowerCase(Locale.ROOT), reward));
    }

    public void mark(UUID owner, String crate, String reward) {
        Key key = new Key(owner, crate.toLowerCase(Locale.ROOT), reward);
        if (claimed.putIfAbsent(key, Boolean.TRUE) == null) {
            markDirty(key);
        }
    }

    public int countFor(UUID owner, String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        int total = 0;
        for (Key key : claimed.keySet()) {
            if (key.owner().equals(owner) && key.crate().equals(id)) {
                total++;
            }
        }
        return total;
    }

    public void forgetCrate(String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        for (Key key : List.copyOf(claimed.keySet())) {
            if (key.crate().equals(id)) {
                claimed.remove(key);
                markRemoved(key);
            }
        }
    }

    public void reset(UUID owner, String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        for (Key key : List.copyOf(claimed.keySet())) {
            if (key.owner().equals(owner) && key.crate().equals(id)) {
                claimed.remove(key);
                markRemoved(key);
            }
        }
    }

    public record Key(UUID owner, String crate, String reward) {
    }
}
