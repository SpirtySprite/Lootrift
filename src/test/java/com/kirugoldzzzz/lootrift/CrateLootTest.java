package com.kirugoldzzzz.lootrift;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateLootTest {

    private static CrateReward reward(String id, int weight, CrateRarity rarity) {
        return new CrateReward(id, null, false, weight, 1, 1, 0.0D, List.of(),
                rarity, null, null, false);
    }

    private static List<CrateReward> pool() {
        return List.of(
                reward("commun", 700, CrateRarity.COMMUN),
                reward("rare", 250, CrateRarity.RARE),
                reward("epique", 45, CrateRarity.EPIQUE),
                reward("mythique", 5, CrateRarity.MYTHIQUE));
    }

    @Test
    @DisplayName("Le tirage pondéré respecte les poids configurés")
    void weightsAreRespected() {
        List<CrateReward> pool = pool();
        int draws = 400_000;
        Map<String, Integer> counts = new HashMap<>();
        for (int index = 0; index < draws; index++) {
            counts.merge(CrateLoot.pick(pool).id(), 1, Integer::sum);
        }
        int total = 1000;
        for (CrateReward reward : pool) {
            double expected = reward.weight() * 100.0D / total;
            double actual = counts.getOrDefault(reward.id(), 0) * 100.0D / draws;
            assertTrue(Math.abs(actual - expected) < 1.0D,
                    reward.id() + " attendu " + expected + "% obtenu " + actual + "%");
        }
    }

    @Test
    @DisplayName("Chaque récompense finit par sortir, aucune n'est inatteignable")
    void everyRewardIsReachable() {
        List<CrateReward> pool = pool();
        Map<String, Integer> counts = new HashMap<>();
        for (int index = 0; index < 200_000; index++) {
            counts.merge(CrateLoot.pick(pool).id(), 1, Integer::sum);
        }
        for (CrateReward reward : pool) {
            assertTrue(counts.getOrDefault(reward.id(), 0) > 0,
                    reward.id() + " n'est jamais sorti");
        }
    }

    @Test
    @DisplayName("Un tirage sur une seule récompense la renvoie toujours")
    void singleRewardPool() {
        CrateReward only = reward("seul", 1, CrateRarity.COMMUN);
        for (int index = 0; index < 100; index++) {
            assertSame(only, CrateLoot.pick(List.of(only)));
        }
    }

    @Test
    @DisplayName("Le ruban place le gain exactement à l'index demandé")
    void reelPlacesWinnerAtIndex() {
        List<CrateReward> pool = pool();
        CrateReward winner = reward("gagnant", 1, CrateRarity.LEGENDAIRE);
        for (int length = 1; length <= 64; length++) {
            for (int index = 0; index < length; index++) {
                List<CrateReward> reel = CrateLoot.reel(pool, length, index, winner);
                assertEquals(length, reel.size(), "longueur du ruban");
                assertSame(winner, reel.get(index),
                        "gain absent de l'index " + index + " sur " + length);
            }
        }
    }

    @Test
    @DisplayName("Le ruban ne contient jamais de case vide")
    void reelHasNoGaps() {
        List<CrateReward> reel = CrateLoot.reel(pool(), 50, 25,
                reward("gagnant", 1, CrateRarity.MYTHIQUE));
        for (CrateReward entry : reel) {
            assertNotNull(entry, "case vide dans le ruban");
        }
    }

    @Test
    @DisplayName("Les index lus par chaque animation restent dans le ruban")
    void animationIndexesStayInBounds() {
        record Spin(String name, int steps, int window, int centre) {
        }
        List<Spin> spins = List.of(
                new Spin("csgo", 38, 9, 4),
                new Spin("tombola", 40, 5, 2));

        for (Spin spin : spins) {
            int length = spin.steps() + spin.window();
            int winnerIndex = spin.steps() - 1 + spin.centre();
            int maxRead = spin.steps() - 1 + spin.window() - 1;

            assertTrue(maxRead < length,
                    spin.name() + " lit l'index " + maxRead + " hors du ruban de " + length);
            assertTrue(winnerIndex < length,
                    spin.name() + " place le gain hors du ruban");

            List<CrateReward> reel = CrateLoot.reel(pool(), length, winnerIndex,
                    reward("gagnant", 1, CrateRarity.MYTHIQUE));
            int centreAtLastStep = spin.steps() - 1 + spin.centre();
            assertEquals("gagnant", reel.get(centreAtLastStep).id(),
                    spin.name() + " ne révèle pas le gain au dernier pas");
        }
    }

    @Test
    @DisplayName("Une récompense sous permission est écartée du tirage")
    void permissionFiltersPool() {
        List<CrateReward> rewards = new ArrayList<>(pool());
        rewards.add(new CrateReward("vip", null, false, 100, 1, 1, 0.0D, List.of(),
                CrateRarity.LEGENDAIRE, "lootrift.vip", null, false));

        List<CrateReward> allowed = new ArrayList<>();
        for (CrateReward reward : rewards) {
            if (!reward.restricted()) {
                allowed.add(reward);
            }
        }
        assertEquals(rewards.size() - 1, allowed.size());
        assertFalse(allowed.stream().anyMatch(reward -> reward.id().equals("vip")));
    }

    @Test
    @DisplayName("La pitié se déclenche exactement au seuil, pas avant")
    void pityFiresAtThreshold() {
        CrateMilestone unique = new CrateMilestone(10, 0.0D, List.of(), null, false);
        assertFalse(unique.reachedAt(9));
        assertTrue(unique.reachedAt(10));
        assertFalse(unique.reachedAt(11));

        CrateMilestone repeating = new CrateMilestone(25, 0.0D, List.of(), null, true);
        assertFalse(repeating.reachedAt(24));
        assertTrue(repeating.reachedAt(25));
        assertFalse(repeating.reachedAt(26));
        assertTrue(repeating.reachedAt(50));
        assertTrue(repeating.reachedAt(500));
        assertFalse(repeating.reachedAt(0));
    }

    @Test
    @DisplayName("Un poids nul ou négatif est ramené à un")
    void weightIsAlwaysPositive() {
        assertEquals(1, reward("zero", 0, CrateRarity.COMMUN).weight());
        assertEquals(1, reward("negatif", -50, CrateRarity.COMMUN).weight());
    }

    @Test
    @DisplayName("Une quantité maximale inférieure au minimum est corrigée")
    void amountRangeIsOrdered() {
        CrateReward reward = new CrateReward("x", null, false, 10, 5, 2, 0.0D, List.of(),
                CrateRarity.COMMUN, null, null, false);
        assertEquals(5, reward.minAmount());
        assertEquals(5, reward.maxAmount());
        assertEquals(5, reward.rollAmount());
    }

    @Test
    @DisplayName("Une récompense unique ne sort qu'une fois dans un même tirage multiple")
    void uniqueRewardIsNeverDrawnTwiceInOneOpening() {
        CrateReward unique = new CrateReward("unique", null, false, 1_000_000, 1, 1, 0.0D, List.of(),
                CrateRarity.MYTHIQUE, null, null, true);
        CrateReward filler = reward("commun", 1, CrateRarity.COMMUN);
        Crate crate = new Crate("test", "Test", null, Material.CHEST, null, null,
                List.of(unique, filler), 5, false, null, 0, null, false, null, null, 0, 0.0D, false,
                null, null, null);
        for (int attempt = 0; attempt < 200; attempt++) {
            CrateLoot.Draw draw = CrateLoot.drawFrom(crate, crate.rewards(), 0);
            assertEquals(1L, draw.rewards().stream().filter(CrateReward::solo).count());
            assertEquals(5, draw.rewards().size());
        }
        assertTrue(CrateLoot.drawFrom(crate, List.of(), 0).isEmpty());
        CrateLoot.Draw onlyUnique = CrateLoot.drawFrom(crate, List.of(unique), 0);
        assertEquals(List.of(unique), onlyUnique.rewards());
    }
}
