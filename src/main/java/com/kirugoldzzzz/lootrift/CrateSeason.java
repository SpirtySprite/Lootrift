package com.kirugoldzzzz.lootrift;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public record CrateSeason(long from, long until) {

    public static final CrateSeason ALWAYS = new CrateSeason(0L, 0L);

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static CrateSeason read(ConfigurationSection section, ZoneId zone) {
        if (section == null) {
            return ALWAYS;
        }
        return new CrateSeason(value(section.get("from"), zone), value(section.get("until"), zone));
    }

    static long value(Object raw, ZoneId zone) {
        if (raw instanceof java.util.Date date) {
            LocalDateTime written = LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneOffset.UTC);
            return written.atZone(zone).toInstant().toEpochMilli();
        }
        return parse(raw == null ? null : raw.toString(), zone);
    }

    static long parse(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String text = raw.strip();
        for (DateTimeFormatter format : DATE_TIMES) {
            try {
                return LocalDateTime.parse(text, format).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(text, format).atStartOfDay(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        return 0L;
    }

    public boolean always() {
        return from <= 0L && until <= 0L;
    }

    public boolean open(long now) {
        return (from <= 0L || now >= from) && (until <= 0L || now < until);
    }

    public boolean upcoming(long now) {
        return from > 0L && now < from;
    }

    public String show(long at, ZoneId zone) {
        return at <= 0L ? "" : SHOWN.format(java.time.Instant.ofEpochMilli(at).atZone(zone));
    }
}
