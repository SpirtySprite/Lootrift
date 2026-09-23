package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.common.storage.SqlRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrateKeyRepository extends SqlRepository<CrateKeyRepository.Key> {

    private final Map<Key, State> states = new ConcurrentHashMap<>();
    private final Map<String, Totals> totals = new ConcurrentHashMap<>();

    public CrateKeyRepository(Database database) {
        super(database);
    }

    @Override
    protected void schema(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS crate_keys ("
                + "owner TEXT NOT NULL,"
                + "crate TEXT NOT NULL,"
                + "keys INTEGER NOT NULL DEFAULT 0,"
                + "opened INTEGER NOT NULL DEFAULT 0,"
                + "streak INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(owner, crate))");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_crate_keys_crate ON crate_keys(crate)");
        if (!hasColumn(statement, "last_daily")) {
            statement.execute("ALTER TABLE crate_keys ADD COLUMN last_daily INTEGER NOT NULL DEFAULT 0");
        }
    }

    private boolean hasColumn(Statement statement, String column) throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(crate_keys)")) {
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
        states.clear();
        totals.clear();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT owner, crate, keys, opened, streak, last_daily FROM crate_keys")) {
            while (results.next()) {
                try {
                    Key key = new Key(UUID.fromString(results.getString(1)),
                            results.getString(2).toLowerCase(Locale.ROOT));
                    State loaded = new State(results.getInt(3), results.getInt(4),
                            results.getInt(5), results.getLong(6));
                    states.put(key, loaded);
                    Totals aggregate = totals(key.crate());
                    aggregate.keys.addAndGet(loaded.keys);
                    aggregate.opened.addAndGet(loaded.opened);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    protected void persistAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO crate_keys(owner, crate, keys, opened, streak, last_daily) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(owner, crate) DO UPDATE SET keys=excluded.keys, "
                        + "opened=excluded.opened, streak=excluded.streak, last_daily=excluded.last_daily")) {
            for (Key key : keys) {
                State state = states.get(key);
                if (state == null) {
                    continue;
                }
                synchronized (state) {
                    statement.setString(1, key.owner().toString());
                    statement.setString(2, key.crate());
                    statement.setInt(3, state.keys);
                    statement.setInt(4, state.opened);
                    statement.setInt(5, state.streak);
                    statement.setLong(6, state.lastDaily);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    protected void eraseAll(Connection connection, Set<Key> keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM crate_keys WHERE owner=? AND crate=?")) {
            for (Key key : keys) {
                statement.setString(1, key.owner().toString());
                statement.setString(2, key.crate());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private State state(Key key) {
        return states.computeIfAbsent(key, ignored -> new State(0, 0, 0, 0L));
    }

    private Totals totals(String crate) {
        return totals.computeIfAbsent(crate, ignored -> new Totals());
    }

    public int keys(UUID owner, String crate) {
        State state = states.get(new Key(owner, normalize(crate)));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.keys;
        }
    }

    public int addKeys(UUID owner, String crate, int amount) {
        return exclusive(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = state(key);
            synchronized (state) {
                int before = state.keys;
                state.keys = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) before + amount));
                totals(key.crate()).keys.addAndGet(state.keys - before);
                markDirty(key);
                return state.keys;
            }
        });
    }

    public int addKeysDurable(UUID owner, String crate, int amount) {
        return change(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = state(key);
            synchronized (state) {
                int before = state.keys;
                int total = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) before + amount));
                int delta = total - before;
                rollback(() -> {
                    synchronized (state) {
                        state.keys = before;
                    }
                    totals(key.crate()).keys.addAndGet(-delta);
                });
                state.keys = total;
                totals(key.crate()).keys.addAndGet(delta);
                markDirty(key);
                return total;
            }
        });
    }

    public void setKeys(UUID owner, String crate, int amount) {
        exclusive(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = state(key);
            int delta;
            synchronized (state) {
                int before = state.keys;
                state.keys = Math.max(0, amount);
                delta = state.keys - before;
            }
            totals(key.crate()).keys.addAndGet(delta);
            markDirty(key);
            return null;
        });
    }

    public boolean takeKey(UUID owner, String crate) {
        return exclusive(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = states.get(key);
            if (state == null) {
                return false;
            }
            synchronized (state) {
                if (state.keys <= 0) {
                    return false;
                }
                state.keys--;
            }
            totals(key.crate()).keys.decrementAndGet();
            markDirty(key);
            return true;
        });
    }

    public int opened(UUID owner, String crate) {
        State state = states.get(new Key(owner, normalize(crate)));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.opened;
        }
    }

    public int streak(UUID owner, String crate) {
        State state = states.get(new Key(owner, normalize(crate)));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.streak;
        }
    }

    public void recordOpen(UUID owner, String crate, boolean satisfied) {
        exclusive(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = state(key);
            synchronized (state) {
                state.opened++;
                state.streak = satisfied ? 0 : state.streak + 1;
            }
            totals(key.crate()).opened.incrementAndGet();
            markDirty(key);
            return null;
        });
    }

    public long lastDaily(UUID owner, String crate) {
        State state = states.get(new Key(owner, normalize(crate)));
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            return state.lastDaily;
        }
    }

    public boolean claimDaily(UUID owner, String crate, long now, long period) {
        return exclusive(() -> {
            Key key = new Key(owner, normalize(crate));
            State state = state(key);
            synchronized (state) {
                if (now - state.lastDaily < period) {
                    return false;
                }
                state.lastDaily = now;
                state.keys++;
            }
            totals(key.crate()).keys.incrementAndGet();
            markDirty(key);
            return true;
        });
    }

    public Map<String, Integer> keysOf(UUID owner) {
        Map<String, Integer> owned = new LinkedHashMap<>();
        states.forEach((key, state) -> {
            if (!key.owner().equals(owner)) {
                return;
            }
            synchronized (state) {
                if (state.keys > 0) {
                    owned.put(key.crate(), state.keys);
                }
            }
        });
        return owned;
    }

    public int circulation(String crate) {
        Totals aggregate = totals.get(normalize(crate));
        return aggregate == null ? 0 : aggregate.keys.get();
    }

    public int totalOpened(String crate) {
        Totals aggregate = totals.get(normalize(crate));
        return aggregate == null ? 0 : aggregate.opened.get();
    }

    public List<Map.Entry<UUID, Integer>> topOpeners(String crate, int limit) {
        String id = normalize(crate);
        Map<UUID, Integer> counts = new HashMap<>();
        states.forEach((key, state) -> {
            if (!key.crate().equals(id)) {
                return;
            }
            synchronized (state) {
                if (state.opened > 0) {
                    counts.put(key.owner(), state.opened);
                }
            }
        });
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        return ranked.size() <= limit ? ranked : new ArrayList<>(ranked.subList(0, limit));
    }

    public void forgetCrate(String crate) {
        exclusive(() -> {
            String id = normalize(crate);
            totals.remove(id);
            for (Key key : List.copyOf(states.keySet())) {
                if (key.crate().equals(id)) {
                    states.remove(key);
                    markRemoved(key);
                }
            }
            return null;
        });
    }

    public int trackedPlayers() {
        return (int) states.keySet().stream().map(Key::owner).distinct().count();
    }

    private static String normalize(String crate) {
        return crate.toLowerCase(Locale.ROOT);
    }

    public record Key(UUID owner, String crate) {
    }

    private static final class Totals {

        private final java.util.concurrent.atomic.AtomicInteger keys =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger opened =
                new java.util.concurrent.atomic.AtomicInteger();
    }

    private static final class State {

        private int keys;
        private int opened;
        private int streak;
        private long lastDaily;

        private State(int keys, int opened, int streak, long lastDaily) {
            this.keys = keys;
            this.opened = opened;
            this.streak = streak;
            this.lastDaily = lastDaily;
        }
    }
}
