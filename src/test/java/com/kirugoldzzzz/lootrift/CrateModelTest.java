package com.kirugoldzzzz.lootrift;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateModelTest {

    @Test
    @DisplayName("Le mode personnel est coupé par défaut et se relit tel quel")
    void personalModeDefaultsOffAndSurvivesARoundTrip() {
        assertFalse(CrateModel.read(section("blueprint", "coffre_1")).personal());
        assertFalse(CrateModel.none().personal());

        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection target = configuration.createSection("model");
        target.set("blueprint", "coffre_2");
        target.set("personal", true);
        assertTrue(CrateModel.read(target).personal());
    }

    private static ConfigurationSection section(String... pairs) {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection created = configuration.createSection("model");
        for (int index = 0; index < pairs.length; index += 2) {
            created.set(pairs[index], pairs[index + 1]);
        }
        return created;
    }

    @Test
    @DisplayName("Sans section, aucun modèle n'est activé")
    void noSectionMeansNoModel() {
        CrateModel model = CrateModel.read(null);
        assertFalse(model.enabled());
        assertNull(model.blueprint());
        assertEquals("aucun", model.describe());
    }

    @Test
    @DisplayName("Un identifiant vide ou blanc ne compte pas comme un modèle")
    void blankIdentifiersDoNotCount() {
        assertFalse(CrateModel.read(section("blueprint", "")).enabled());
        assertFalse(CrateModel.read(section("blueprint", "   ")).enabled());
        assertTrue(CrateModel.read(section("blueprint", "coffre_1")).enabled());
    }

    @Test
    @DisplayName("Les identifiants sont débarrassés de leurs espaces")
    void identifiersAreTrimmed() {
        CrateModel model = CrateModel.read(section(
                "blueprint", "  coffre_3  ", "idle", " idle_mouve ", "opening", "opening_rare"));
        assertEquals("coffre_3", model.blueprint());
        assertEquals("idle_mouve", model.idle());
        assertEquals("opening_rare", model.opening());
        assertNull(model.closing());
    }

    @Test
    @DisplayName("L'échelle, le décalage et la tenue restent dans leurs bornes")
    void numbersStayInsideTheirBounds() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection wild = configuration.createSection("model");
        wild.set("blueprint", "coffre_5");
        wild.set("scale", 99.0D);
        wild.set("offset", -40.0D);
        wild.set("open-delay-ticks", 100000L);
        wild.set("close-delay-ticks", 100000L);
        CrateModel high = CrateModel.read(wild);
        assertEquals(CrateModel.MAX_SCALE, high.scale());
        assertEquals(-CrateModel.MAX_OFFSET, high.offset());
        assertEquals(CrateModel.MAX_DELAY_TICKS, high.openDelayTicks());
        assertEquals(CrateModel.MAX_DELAY_TICKS, high.closeDelayTicks());

        wild.set("scale", 0.0D);
        wild.set("open-delay-ticks", -5L);
        wild.set("close-delay-ticks", -5L);
        CrateModel low = CrateModel.read(wild);
        assertEquals(CrateModel.MIN_SCALE, low.scale());
        assertEquals(CrateModel.MIN_DELAY_TICKS, low.openDelayTicks());
        assertEquals(CrateModel.MIN_DELAY_TICKS, low.closeDelayTicks());
    }

    @Test
    @DisplayName("Écrire puis relire redonne le même modèle")
    void writingThenReadingGivesTheSameModel() {
        CrateModel original = new CrateModel("coffre_4", "idle_mouve", "opening", "closing",
                1.5D, 0.25D, 30L, 50L, true);
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection target = configuration.createSection("model");
        original.write(target);
        assertEquals(original, CrateModel.read(target));
    }

    @Test
    @DisplayName("Le défaut partagé n'est jamais nul dans une caisse")
    void theSharedDefaultIsNeverNullInsideACrate() {
        assertFalse(CrateModel.none().enabled());
        assertEquals(CrateModel.none(), CrateModel.read(null));
    }
}
