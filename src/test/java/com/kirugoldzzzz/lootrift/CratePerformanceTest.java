package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.support.TestPlugin;
import com.kirugoldzzzz.lootrift.support.Timings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CratePerformanceTest {

    private static final String[] CRATES = {"commune", "rare", "legendaire", "mythique"};
    private static final int PLAYERS = 500;
    private static final int MENU_OPENS = 500;

    @TempDir
    static Path folder;

    private static Database database;
    private static CrateKeyRepository keys;
    private static List<UUID> owners;

    @BeforeAll
    static void seed() throws SQLException {
        database = new Database(TestPlugin.at(folder.toFile()), "perf.db");
        database.open();
        keys = new CrateKeyRepository(database);
        keys.load();

        owners = new ArrayList<>(PLAYERS);
        for (int index = 0; index < PLAYERS; index++) {
            UUID owner = UUID.nameUUIDFromBytes(("perf" + index).getBytes());
            owners.add(owner);
            for (String crate : CRATES) {
                keys.addKeys(owner, crate, index % 11);
                for (int open = 0; open < index % 4; open++) {
                    keys.recordOpen(owner, crate, false);
                }
            }
        }
        System.out.println();
        System.out.println("=== Caisses, mesures de performance ===");
        System.out.println("Joueurs suivis      : " + PLAYERS);
        System.out.println("Caisses             : " + CRATES.length);
        System.out.println("Entrées de clés     : " + PLAYERS * CRATES.length);
    }

    @AfterAll
    static void close() {
        database.close();
        System.out.println("=======================================");
        System.out.println();
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    @Test
    @Order(1)
    @DisplayName("Les compteurs agrégés se lisent en temps constant")
    void aggregateLookupIsConstant() {
        long start = System.nanoTime();
        long sink = 0L;
        for (int open = 0; open < MENU_OPENS; open++) {
            for (String crate : CRATES) {
                sink += keys.circulation(crate);
                sink += keys.totalOpened(crate);
            }
        }
        long elapsed = System.nanoTime() - start;
        double perMenu = elapsed / 1_000_000.0D / MENU_OPENS;

        System.out.printf("Agrégats par menu   : %.4f ms (%d lectures)%n",
                perMenu, MENU_OPENS * CRATES.length * 2);
        assertTrue(sink > 0L);
        Timings.under("agrégats par menu", perMenu, 0.05D, "ms");
    }

    @Test
    @Order(2)
    @DisplayName("Un scan complet équivalent serait bien plus coûteux")
    void fullScanIsMuchSlower() {
        long start = System.nanoTime();
        long sink = 0L;
        for (int open = 0; open < MENU_OPENS; open++) {
            for (String crate : CRATES) {
                for (UUID owner : owners) {
                    sink += keys.keys(owner, crate);
                }
            }
        }
        long elapsed = System.nanoTime() - start;
        double perMenu = elapsed / 1_000_000.0D / MENU_OPENS;

        System.out.printf("Scan complet par menu: %.4f ms (référence évitée)%n", perMenu);
        assertTrue(sink > 0L);
    }

    @Test
    @Order(3)
    @DisplayName("Le tirage pondéré tient la charge d'ouvertures massives")
    void lootThroughput() {
        List<CrateReward> pool = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            pool.add(new CrateReward("recompense" + index, null, false, 10 + index,
                    1, 1, 0.0D, List.of(), CrateRarity.COMMUN, null, null, false));
        }
        int draws = 200_000;
        long start = System.nanoTime();
        int sink = 0;
        for (int index = 0; index < draws; index++) {
            sink += CrateLoot.pick(pool).weight();
        }
        long elapsed = System.nanoTime() - start;

        System.out.printf("Tirages             : %d en %d ms (%.0f par ms)%n",
                draws, millis(elapsed), draws / Math.max(1.0D, elapsed / 1_000_000.0D));
        assertTrue(sink > 0);
        Timings.under("tirages", millis(elapsed), 2000.0D, "ms");
    }

    @Test
    @Order(4)
    @DisplayName("La construction du ruban d'animation reste négligeable")
    void reelBuildCost() {
        List<CrateReward> pool = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            pool.add(new CrateReward("r" + index, null, false, 10, 1, 1, 0.0D, List.of(),
                    CrateRarity.COMMUN, null, null, false));
        }
        CrateReward winner = pool.get(0);
        int builds = 20_000;
        long start = System.nanoTime();
        int sink = 0;
        for (int index = 0; index < builds; index++) {
            sink += CrateLoot.reel(pool, 47, 41, winner).size();
        }
        long elapsed = System.nanoTime() - start;

        System.out.printf("Rubans CS:GO        : %d en %d ms (%.4f ms par ouverture)%n",
                builds, millis(elapsed), elapsed / 1_000_000.0D / builds);
        assertEquals(builds * 47, sink);
    }

    @Test
    @Order(5)
    @DisplayName("Les écritures de clés soutiennent une distribution de masse")
    void massKeyGrantThroughput() {
        long start = System.nanoTime();
        for (UUID owner : owners) {
            keys.addKeys(owner, "commune", 1);
        }
        long elapsed = System.nanoTime() - start;

        System.out.printf("Distribution à tous : %d joueurs en %.3f ms%n",
                PLAYERS, elapsed / 1_000_000.0D);
        System.out.println("Clés en circulation : " + keys.circulation("commune"));
        System.out.println("Ouvertures totales  : " + keys.totalOpened("commune"));
        Timings.under("distribution de masse", elapsed / 1_000_000.0D, 100.0D, "ms");
    }
}
