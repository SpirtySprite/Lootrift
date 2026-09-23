package com.kirugoldzzzz.lootrift;

import org.bukkit.Particle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateParticleTest {

    @Test
    @DisplayName("Aucune particule de la palette ne réclame de données")
    void noPaletteParticleAsksForData() {
        List<String> demanding = new ArrayList<>();
        for (Particle particle : CrateBlockAnimation.PALETTE) {
            if (particle.getDataType() != Void.class) {
                demanding.add(particle.name() + " veut " + particle.getDataType().getSimpleName());
            }
        }
        assertTrue(demanding.isEmpty(), "particules impossibles à jouer: " + demanding);
    }

    @Test
    @DisplayName("La palette reste fournie et sans doublon")
    void thePaletteStaysWideAndFreeOfDuplicates() {
        assertTrue(CrateBlockAnimation.PALETTE.size() >= 40,
                "palette trop courte: " + CrateBlockAnimation.PALETTE.size());
        assertEquals(CrateBlockAnimation.PALETTE.size(),
                CrateBlockAnimation.PALETTE.stream().distinct().count());
    }

    @Test
    @DisplayName("Une particule qui réclame des données est refusée")
    void aParticleThatNeedsDataIsRefused() {
        assertFalse(CrateBlockAnimation.usable(Particle.DUST));
        assertFalse(CrateBlockAnimation.usable(Particle.BLOCK));
        assertFalse(CrateBlockAnimation.usable(Particle.ITEM));
        assertFalse(CrateBlockAnimation.usable(null));
        assertTrue(CrateBlockAnimation.usable(Particle.END_ROD));
    }

    @Test
    @DisplayName("Un nom de particule injouable retombe sur la valeur de repli")
    void anUnplayableNameFallsBackToTheDefault() {
        assertEquals(Particle.END_ROD,
                CrateBlockAnimation.particle("DUST", Particle.END_ROD));
        assertEquals(Particle.END_ROD,
                CrateBlockAnimation.particle("inexistante", Particle.END_ROD));
        assertEquals(Particle.END_ROD, CrateBlockAnimation.particle(null, Particle.END_ROD));
        assertEquals(Particle.FLAME, CrateBlockAnimation.particle("flame", Particle.END_ROD));
    }

    @Test
    @DisplayName("Une configuration injouable est corrigée à la lecture")
    void anUnplayableConfigurationIsCorrectedOnRead() {
        CrateBlockEffects effects = new CrateBlockEffects(CrateBlockAnimation.HALO,
                Particle.DUST, 0.7D, 1.2D, 12, 1.0D);
        assertEquals(Particle.END_ROD, effects.particle());
        assertTrue(CrateBlockAnimation.usable(effects.particle()));
    }
}
