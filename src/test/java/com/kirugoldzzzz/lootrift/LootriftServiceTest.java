package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.api.LootriftApi;
import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.support.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootriftServiceTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("api".getBytes());

    @TempDir
    Path folder;

    private Database database;
    private CrateKeyRepository keys;
    private LootriftApi api;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(TestPlugin.at(folder.toFile()), "api.db");
        database.open();
        keys = new CrateKeyRepository(database);
        keys.load();
        CrateService service = new CrateService(keys, null, null, null, null);
        service.configure(null);
        api = new LootriftService(service, null);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void emptyConfigurationExposesNoCrates() {
        assertTrue(api.crates().isEmpty());
        assertFalse(api.exists("common"));
    }

    @Test
    void unknownCratesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> api.keys(PLAYER, "ghost"));
        assertThrows(IllegalArgumentException.class, () -> api.giveKeys(PLAYER, "ghost", 1));
        assertThrows(IllegalArgumentException.class, () -> api.takeKeys(PLAYER, "ghost", 1));
        assertThrows(IllegalArgumentException.class, () -> api.setKeys(PLAYER, "ghost", 1));
        assertThrows(IllegalArgumentException.class, () -> api.opened(PLAYER, "ghost"));
    }

    @Test
    void keyTotalsAreACopyOfTheStoredBalances() {
        keys.addKeys(PLAYER, "rare", 3);
        assertEquals(3, api.keys(PLAYER).get("rare"));
        assertThrows(UnsupportedOperationException.class, () -> api.keys(PLAYER).put("rare", 99));
    }
}
