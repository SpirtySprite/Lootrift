package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.support.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateKeyRepositoryTest {

    private static final String CRATE = "commune";
    private static final String OTHER = "mythique";

    @TempDir
    Path folder;

    private Database database;
    private CrateKeyRepository repository;

    private final UUID alice = UUID.nameUUIDFromBytes("alice".getBytes());
    private final UUID bob = UUID.nameUUIDFromBytes("bob".getBytes());

    @BeforeEach
    void open() throws SQLException {
        database = new Database(TestPlugin.at(folder.toFile()), "crates.db");
        database.open();
        repository = new CrateKeyRepository(database);
        repository.load();
    }

    @AfterEach
    void close() {
        database.close();
    }

    private int scanCirculation(String crate, List<UUID> owners) {
        int total = 0;
        for (UUID owner : owners) {
            total += repository.keys(owner, crate);
        }
        return total;
    }

    private int scanOpened(String crate, List<UUID> owners) {
        int total = 0;
        for (UUID owner : owners) {
            total += repository.opened(owner, crate);
        }
        return total;
    }

    @Test
    @DisplayName("Les clés se créditent, se retirent et ne passent jamais sous zéro")
    void keysNeverGoNegative() {
        assertEquals(0, repository.keys(alice, CRATE));
        assertEquals(5, repository.addKeys(alice, CRATE, 5));
        assertEquals(2, repository.addKeys(alice, CRATE, -3));
        assertEquals(0, repository.addKeys(alice, CRATE, -99));
        assertEquals(0, repository.keys(alice, CRATE));
    }

    @Test
    @DisplayName("Le compteur agrégé suit exactement la somme réelle des clés")
    void circulationMatchesFullScan() {
        List<UUID> owners = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("joueur" + index).getBytes());
            owners.add(owner);
            repository.addKeys(owner, CRATE, index % 7);
            repository.addKeys(owner, OTHER, index % 3);
        }
        assertEquals(scanCirculation(CRATE, owners), repository.circulation(CRATE));
        assertEquals(scanCirculation(OTHER, owners), repository.circulation(OTHER));

        for (int index = 0; index < 200; index += 2) {
            repository.takeKey(owners.get(index), CRATE);
        }
        assertEquals(scanCirculation(CRATE, owners), repository.circulation(CRATE));

        repository.setKeys(owners.get(0), CRATE, 42);
        repository.setKeys(owners.get(1), CRATE, 0);
        assertEquals(scanCirculation(CRATE, owners), repository.circulation(CRATE));
    }

    @Test
    @DisplayName("Le compteur d'ouvertures suit exactement la somme réelle")
    void openedMatchesFullScan() {
        List<UUID> owners = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("ouvreur" + index).getBytes());
            owners.add(owner);
            for (int open = 0; open <= index % 5; open++) {
                repository.recordOpen(owner, CRATE, open % 2 == 0);
            }
        }
        assertEquals(scanOpened(CRATE, owners), repository.totalOpened(CRATE));
        assertEquals(0, repository.totalOpened(OTHER));
    }

    @Test
    @DisplayName("Prendre une clé absente échoue sans modifier le compteur")
    void takingMissingKeyDoesNotDrift() {
        repository.addKeys(alice, CRATE, 1);
        assertTrue(repository.takeKey(alice, CRATE));
        assertFalse(repository.takeKey(alice, CRATE));
        assertFalse(repository.takeKey(bob, CRATE));
        assertEquals(0, repository.circulation(CRATE));
    }

    @Test
    @DisplayName("Le compteur reste juste sous accès concurrent")
    void aggregateSurvivesConcurrency() throws InterruptedException {
        int threads = 8;
        int perThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int index = 0; index < threads; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("parallele" + index).getBytes());
            new Thread(() -> {
                try {
                    start.await();
                    for (int step = 0; step < perThread; step++) {
                        repository.addKeys(owner, CRATE, 2);
                        repository.takeKey(owner, CRATE);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30L, TimeUnit.SECONDS), "les threads n'ont pas terminé");
        assertEquals(0, failures.get());
        assertEquals(threads * perThread, repository.circulation(CRATE));
    }

    @Test
    @DisplayName("La pitié compte les ouvertures et se remet à zéro quand elle est satisfaite")
    void streakResetsWhenSatisfied() {
        for (int index = 0; index < 9; index++) {
            repository.recordOpen(alice, CRATE, false);
        }
        assertEquals(9, repository.streak(alice, CRATE));
        assertEquals(9, repository.opened(alice, CRATE));

        repository.recordOpen(alice, CRATE, true);
        assertEquals(0, repository.streak(alice, CRATE));
        assertEquals(10, repository.opened(alice, CRATE));
    }

    @Test
    @DisplayName("La clé du jour n'est réclamable qu'une fois par période")
    void dailyKeyRespectsPeriod() {
        long period = 1000L;
        long now = 10_000L;
        assertTrue(repository.claimDaily(alice, CRATE, now, period));
        assertEquals(1, repository.keys(alice, CRATE));

        assertFalse(repository.claimDaily(alice, CRATE, now + 500L, period));
        assertEquals(1, repository.keys(alice, CRATE));

        assertTrue(repository.claimDaily(alice, CRATE, now + period, period));
        assertEquals(2, repository.keys(alice, CRATE));
        assertEquals(2, repository.circulation(CRATE));
    }

    @Test
    @DisplayName("Oublier une caisse efface ses clés et remet son compteur à zéro")
    void forgetCrateClearsAggregate() {
        repository.addKeys(alice, CRATE, 10);
        repository.addKeys(bob, CRATE, 5);
        repository.addKeys(alice, OTHER, 3);
        assertEquals(15, repository.circulation(CRATE));

        repository.forgetCrate(CRATE);
        assertEquals(0, repository.circulation(CRATE));
        assertEquals(0, repository.keys(alice, CRATE));
        assertEquals(3, repository.circulation(OTHER));
    }

    @Test
    @DisplayName("Les clés survivent à un rechargement et les compteurs se reconstruisent")
    void aggregatesRebuildAfterReload() {
        repository.addKeys(alice, CRATE, 7);
        repository.addKeys(bob, CRATE, 4);
        repository.recordOpen(alice, CRATE, false);
        repository.recordOpen(alice, CRATE, false);
        repository.flushNow();

        CrateKeyRepository reloaded = new CrateKeyRepository(database);
        reloaded.load();

        assertEquals(7, reloaded.keys(alice, CRATE));
        assertEquals(11, reloaded.circulation(CRATE));
        assertEquals(2, reloaded.totalOpened(CRATE));
        assertEquals(2, reloaded.streak(alice, CRATE));
    }

    @Test
    @DisplayName("Les meilleurs ouvreurs sortent triés du plus actif au moins actif")
    void topOpenersAreSorted() {
        for (int index = 0; index < 5; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("top" + index).getBytes());
            for (int open = 0; open <= index; open++) {
                repository.recordOpen(owner, CRATE, false);
            }
        }
        List<java.util.Map.Entry<UUID, Integer>> top = repository.topOpeners(CRATE, 3);
        assertEquals(3, top.size());
        assertEquals(5, top.get(0).getValue());
        assertEquals(4, top.get(1).getValue());
        assertEquals(3, top.get(2).getValue());
    }

}
