package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.common.storage.Store;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class CrateHistoryRepository implements Store {

    private static final int MAX_BUFFER = 50_000;
    private static final int BATCH = 2048;
    private static final int PURGE_CHUNK = 5_000;

    private final Database database;
    private final Queue<CratePull> buffered = new ConcurrentLinkedQueue<>();
    private final AtomicInteger buffer = new AtomicInteger();

    public CrateHistoryRepository(Database database) {
        this.database = database;
    }

    @Override
    public void load() {
        database.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS crate_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "at INTEGER NOT NULL,"
                        + "owner TEXT NOT NULL,"
                        + "owner_name TEXT NOT NULL DEFAULT '',"
                        + "crate TEXT NOT NULL,"
                        + "reward TEXT NOT NULL,"
                        + "reward_name TEXT NOT NULL DEFAULT '',"
                        + "rarity TEXT NOT NULL,"
                        + "amount INTEGER NOT NULL DEFAULT 1,"
                        + "money REAL NOT NULL DEFAULT 0)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_history_owner "
                        + "ON crate_history(owner, at DESC)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_history_crate "
                        + "ON crate_history(crate, at DESC)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_history_at "
                        + "ON crate_history(at)");
            }
        });
    }

    public void append(CratePull pull) {
        if (buffer.get() >= MAX_BUFFER) {
            return;
        }
        buffered.add(pull);
        buffer.incrementAndGet();
    }

    public int pending() {
        return buffer.get();
    }

    @Override
    public void flush() {
        int guard = MAX_BUFFER / BATCH + 1;
        while (!buffered.isEmpty() && guard-- > 0) {
            if (!writeBatch()) {
                return;
            }
        }
    }

    private boolean writeBatch() {
        List<CratePull> batch = new ArrayList<>(Math.min(buffer.get(), BATCH));
        CratePull pull;
        while (batch.size() < BATCH && (pull = buffered.poll()) != null) {
            buffer.decrementAndGet();
            batch.add(pull);
        }
        if (batch.isEmpty()) {
            return true;
        }
        boolean written = database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO crate_history(at, owner, owner_name, crate, reward, reward_name, "
                            + "rarity, amount, money) VALUES(?,?,?,?,?,?,?,?,?)")) {
                for (CratePull entry : batch) {
                    statement.setLong(1, entry.at());
                    statement.setString(2, entry.owner().toString());
                    statement.setString(3, entry.ownerName());
                    statement.setString(4, entry.crate());
                    statement.setString(5, entry.reward());
                    statement.setString(6, entry.rewardName());
                    statement.setString(7, entry.rarity().id());
                    statement.setInt(8, entry.amount());
                    statement.setDouble(9, entry.money());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
        if (!written) {
            batch.forEach(this::append);
        }
        return written;
    }

    @Override
    public void flushNow() {
        flush();
    }

    public List<CratePull> page(UUID owner, String crate, int limit, int offset) {
        List<CratePull> pulls = new ArrayList<>();
        String filter = crate == null ? "" : " AND crate = ?";
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT at, owner, owner_name, crate, reward, reward_name, rarity, amount, money "
                            + "FROM crate_history WHERE owner = ?" + filter
                            + " ORDER BY at DESC, id DESC LIMIT ? OFFSET ?")) {
                statement.setString(1, owner.toString());
                int index = 2;
                if (crate != null) {
                    statement.setString(index++, crate.toLowerCase(Locale.ROOT));
                }
                statement.setInt(index++, limit);
                statement.setInt(index, offset);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        pulls.add(read(results));
                    }
                }
            }
        });
        return pulls;
    }

    public List<CratePull> recent(String crate, int limit) {
        List<CratePull> pulls = new ArrayList<>();
        String filter = crate == null ? "" : " WHERE crate = ?";
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT at, owner, owner_name, crate, reward, reward_name, rarity, amount, money "
                            + "FROM crate_history" + filter + " ORDER BY at DESC, id DESC LIMIT ?")) {
                int index = 1;
                if (crate != null) {
                    statement.setString(index++, crate.toLowerCase(Locale.ROOT));
                }
                statement.setInt(index, limit);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        pulls.add(read(results));
                    }
                }
            }
        });
        return pulls;
    }

    public int countFor(UUID owner) {
        int[] count = new int[1];
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM crate_history WHERE owner = ?")) {
                statement.setString(1, owner.toString());
                try (ResultSet results = statement.executeQuery()) {
                    count[0] = results.next() ? results.getInt(1) : 0;
                }
            }
        });
        return count[0];
    }

    public Map<String, Integer> countsByReward(String crate) {
        Map<String, Integer> counts = new HashMap<>();
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT reward, COUNT(*) FROM crate_history WHERE crate = ? GROUP BY reward")) {
                statement.setString(1, crate.toLowerCase(Locale.ROOT));
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        counts.put(results.getString(1), results.getInt(2));
                    }
                }
            }
        });
        return counts;
    }

    public List<Map.Entry<String, Integer>> topWinners(String crate, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String filter = crate == null ? "" : " WHERE crate = ?";
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT owner_name, COUNT(*) AS total FROM crate_history" + filter
                            + " GROUP BY owner_name ORDER BY total DESC LIMIT ?")) {
                int index = 1;
                if (crate != null) {
                    statement.setString(index++, crate.toLowerCase(Locale.ROOT));
                }
                statement.setInt(index, limit);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        counts.put(results.getString(1), results.getInt(2));
                    }
                }
            }
        });
        return new ArrayList<>(counts.entrySet());
    }

    public List<CratePull> bestPulls(String crate, int limit) {
        List<CratePull> pulls = new ArrayList<>();
        String filter = crate == null ? "" : " AND crate = ?";
        database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT at, owner, owner_name, crate, reward, reward_name, rarity, amount, money "
                            + "FROM crate_history WHERE rarity IN ('legendaire','mythique')" + filter
                            + " ORDER BY at DESC LIMIT ?")) {
                int index = 1;
                if (crate != null) {
                    statement.setString(index++, crate.toLowerCase(Locale.ROOT));
                }
                statement.setInt(index, limit);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        pulls.add(read(results));
                    }
                }
            }
        });
        return pulls;
    }

    public int total() {
        int[] count = new int[1];
        database.read(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM crate_history")) {
                count[0] = results.next() ? results.getInt(1) : 0;
            }
        });
        return count[0];
    }

    public int purgeOlderThan(long cutoff) {
        return database.deleteInChunks("crate_history", "at < ?", statement -> bind(statement, cutoff), PURGE_CHUNK);
    }

    private static void bind(PreparedStatement statement, long cutoff) {
        try {
            statement.setLong(1, cutoff);
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static CratePull read(ResultSet results) throws SQLException {
        return new CratePull(
                results.getLong(1),
                UUID.fromString(results.getString(2)),
                results.getString(3),
                results.getString(4),
                results.getString(5),
                results.getString(6),
                CrateRarity.byId(results.getString(7), CrateRarity.COMMUN),
                results.getInt(8),
                results.getDouble(9));
    }
}
