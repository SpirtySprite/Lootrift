package com.kirugoldzzzz.lootrift;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateSeasonTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    void bothDateStylesParse() {
        assertEquals(at(2026, 12, 24, 18, 30), CrateSeason.parse("2026-12-24 18:30", UTC));
        assertEquals(at(2026, 12, 24, 18, 30), CrateSeason.parse("24/12/2026 18:30", UTC));
        assertEquals(at(2026, 12, 24, 0, 0), CrateSeason.parse("2026-12-24", UTC));
        assertEquals(0L, CrateSeason.parse("next christmas", UTC));
        assertEquals(0L, CrateSeason.parse(null, UTC));
    }

    @Test
    void seasonOpensAndClosesOnTime() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("from: 2026-12-24\nuntil: 2026-12-27 00:00\n");
        CrateSeason christmas = CrateSeason.read(yaml.getRoot(), UTC);

        assertFalse(christmas.open(at(2026, 12, 23, 23, 59)));
        assertTrue(christmas.upcoming(at(2026, 12, 23, 23, 59)));
        assertTrue(christmas.open(at(2026, 12, 24, 0, 0)));
        assertTrue(christmas.open(at(2026, 12, 26, 23, 59)));
        assertFalse(christmas.open(at(2026, 12, 27, 0, 0)));
        assertFalse(christmas.upcoming(at(2026, 12, 28, 0, 0)));
        assertEquals("24/12/2026 00:00", christmas.show(christmas.from(), UTC));
    }

    @Test
    void missingSeasonIsAlwaysOpen() {
        assertTrue(CrateSeason.read(null, UTC).always());
        assertTrue(CrateSeason.ALWAYS.open(0L));
        assertTrue(new CrateSeason(0L, at(2030, 1, 1, 0, 0)).open(at(2026, 1, 1, 0, 0)));
    }
}
