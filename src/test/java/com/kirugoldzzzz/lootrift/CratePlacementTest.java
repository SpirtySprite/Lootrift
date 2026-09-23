package com.kirugoldzzzz.lootrift;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CratePlacementTest {

    private static CratePlacement at(float yaw) {
        return new CratePlacement("Commune", "world", 10, 64, -30, null, yaw);
    }

    @Test
    @DisplayName("Un angle est ramené dans le tour complet")
    void anAngleIsWrappedIntoOneTurn() {
        assertEquals(0.0F, CratePlacement.wrap(360.0F));
        assertEquals(90.0F, CratePlacement.wrap(450.0F));
        assertEquals(270.0F, CratePlacement.wrap(-90.0F));
        assertEquals(315.0F, CratePlacement.wrap(-765.0F));
    }

    @Test
    @DisplayName("Un angle est aimanté sur le pas de 45 degrés")
    void anAngleSnapsToTheStep() {
        assertEquals(0.0F, CratePlacement.snap(20.0F));
        assertEquals(45.0F, CratePlacement.snap(23.0F));
        assertEquals(90.0F, CratePlacement.snap(88.0F));
        assertEquals(0.0F, CratePlacement.snap(359.0F));
        assertEquals(315.0F, CratePlacement.snap(-44.0F));
    }

    @Test
    @DisplayName("Le constructeur ne garde jamais un angle hors du tour")
    void theConstructorNeverKeepsAnAngleOutsideTheTurn() {
        assertEquals(90.0F, at(450.0F).yaw());
        assertEquals(270.0F, at(-90.0F).yaw());
    }

    @Test
    @DisplayName("Huit rotations ramènent à l'orientation de départ")
    void eightTurnsComeBackToTheStart() {
        CratePlacement placement = at(0.0F);
        Set<Float> seen = new HashSet<>();
        for (int step = 0; step < 8; step++) {
            assertTrue(seen.add(placement.yaw()), "orientation répétée: " + placement.yaw());
            placement = placement.rotated();
        }
        assertEquals(0.0F, placement.yaw());
        assertEquals(8, seen.size());
    }

    @Test
    @DisplayName("Chaque pas de rotation porte un nom distinct")
    void everyStepHasItsOwnName() {
        Set<String> names = new HashSet<>();
        CratePlacement placement = at(0.0F);
        for (int step = 0; step < 8; step++) {
            assertTrue(names.add(placement.facing()), "nom répété: " + placement.facing());
            placement = placement.rotated();
        }
        assertEquals("sud", at(0.0F).facing());
        assertEquals("nord", at(180.0F).facing());
        assertEquals("ouest", at(90.0F).facing());
        assertEquals("est", at(270.0F).facing());
    }

    @Test
    @DisplayName("L'orientation et l'hologramme survivent l'un à l'autre")
    void yawAndHologramSurviveEachOther() {
        UUID hologram = UUID.randomUUID();
        CratePlacement placement = at(135.0F).withHologram(hologram);
        assertEquals(135.0F, placement.yaw());
        assertEquals(hologram, placement.hologram());

        CratePlacement turned = placement.withYaw(225.0F);
        assertEquals(225.0F, turned.yaw());
        assertEquals(hologram, turned.hologram());
        assertEquals(placement.id(), turned.id());
    }

    @Test
    @DisplayName("L'identifiant ignore l'orientation")
    void theIdentifierIgnoresTheYaw() {
        assertEquals(at(0.0F).id(), at(180.0F).id());
    }
}
