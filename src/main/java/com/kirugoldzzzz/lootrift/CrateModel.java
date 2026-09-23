package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;

public record CrateModel(String blueprint, String idle, String opening, String closing,
                         double scale, double offset,
                         long openDelayTicks, long closeDelayTicks, boolean personal) {

    public static final double MIN_SCALE = 0.1D;
    public static final double MAX_SCALE = 8.0D;
    public static final double MAX_OFFSET = 3.0D;
    public static final long MIN_DELAY_TICKS = 0L;
    public static final long MAX_DELAY_TICKS = 400L;
    public static final long IDLE_RETURN_TICKS = 15L;

    private static final CrateModel NONE =
            new CrateModel(null, null, null, null, 1.0D, 0.0D, 40L, 40L, false);

    public CrateModel {
        blueprint = trimmed(blueprint);
        idle = trimmed(idle);
        opening = trimmed(opening);
        closing = trimmed(closing);
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);
        offset = clamp(offset, -MAX_OFFSET, MAX_OFFSET);
        openDelayTicks = (long) clamp(openDelayTicks, MIN_DELAY_TICKS, MAX_DELAY_TICKS);
        closeDelayTicks = (long) clamp(closeDelayTicks, MIN_DELAY_TICKS, MAX_DELAY_TICKS);
    }

    public static CrateModel none() {
        return NONE;
    }

    public static CrateModel read(ConfigurationSection section) {
        if (section == null) {
            return NONE;
        }
        return new CrateModel(section.getString("blueprint"),
                section.getString("idle"),
                section.getString("opening"),
                section.getString("closing"),
                section.getDouble("scale", 1.0D),
                section.getDouble("offset", 0.0D),
                section.getLong("open-delay-ticks", 40L),
                section.getLong("close-delay-ticks", 40L),
                section.getBoolean("personal", false));
    }

    public void write(ConfigurationSection section) {
        section.set("blueprint", blueprint);
        section.set("idle", idle);
        section.set("opening", opening);
        section.set("closing", closing);
        section.set("scale", scale);
        section.set("offset", offset);
        section.set("open-delay-ticks", openDelayTicks);
        section.set("close-delay-ticks", closeDelayTicks);
        section.set("personal", personal);
    }

    public boolean enabled() {
        return blueprint != null;
    }

    public CrateModel withBlueprint(String value) {
        return new CrateModel(value, idle, opening, closing, scale, offset,
                openDelayTicks, closeDelayTicks, personal);
    }

    public String describe() {
        return enabled() ? blueprint : Tr.t("aucun");
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
