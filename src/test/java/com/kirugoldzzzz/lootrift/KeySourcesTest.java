package com.kirugoldzzzz.lootrift;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeySourcesTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void dropsReadPercentagesTargetsAndDefaults() throws Exception {
        YamlConfiguration yaml = yaml("""
                drops:
                  - trigger: kill
                    target: zombie
                    chance: 0.5%
                    crate: Common
                  - trigger: mine
                    chance: 0.02
                    crate: rare
                    amount: 3
                  - trigger: kill
                    chance: 0
                    crate: rare
                  - trigger: kill
                    chance: 5%
                """);
        List<KeySources.Drop> drops = KeySources.readDrops(yaml.getMapList("drops"));

        assertEquals(2, drops.size());
        KeySources.Drop zombie = drops.get(0);
        assertEquals(0.005D, zombie.chance(), 1.0E-9);
        assertEquals("common", zombie.crate());
        assertTrue(zombie.matches(KeySources.Trigger.KILL, "ZOMBIE"));
        assertFalse(zombie.matches(KeySources.Trigger.KILL, "SKELETON"));
        assertFalse(zombie.matches(KeySources.Trigger.MINE, "ZOMBIE"));

        KeySources.Drop anyBlock = drops.get(1);
        assertEquals(3, anyBlock.amount());
        assertTrue(anyBlock.matches(KeySources.Trigger.MINE, "STONE"));
    }

    @Test
    void playtimeNeedsToBeEnabledWithACrate() throws Exception {
        assertFalse(KeySources.readPlaytime(null).enabled());
        assertFalse(KeySources.readPlaytime(yaml("enabled: false\ncrate: common").getRoot()).enabled());
        assertFalse(KeySources.readPlaytime(yaml("enabled: true").getRoot()).enabled());
        KeySources.Playtime playtime = KeySources.readPlaytime(yaml("enabled: true\ncrate: Vote\nevery-minutes: 0")
                .getRoot());
        assertTrue(playtime.enabled());
        assertEquals("vote", playtime.crate());
        assertEquals(1, playtime.everyMinutes());
    }
}
