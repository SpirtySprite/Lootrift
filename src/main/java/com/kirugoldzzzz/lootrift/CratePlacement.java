package com.kirugoldzzzz.lootrift;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record CratePlacement(String crate, String world, int x, int y, int z, UUID hologram,
                            float yaw) {

    public static final float ROTATION_STEP = 45.0F;

    public CratePlacement {
        crate = crate.toLowerCase(Locale.ROOT);
        yaw = wrap(yaw);
    }

    public static float wrap(float value) {
        float wrapped = value % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static float snap(float value) {
        return wrap(Math.round(wrap(value) / ROTATION_STEP) * ROTATION_STEP);
    }

    public static CratePlacement of(Crate crate, Block block) {
        return of(crate, block, 0.0F);
    }

    public static CratePlacement of(Crate crate, Block block, float yaw) {
        return new CratePlacement(crate.id(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), null, snap(yaw));
    }

    public static String idOf(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String idOf(Block block) {
        return idOf(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String id() {
        return idOf(world, x, y, z);
    }

    public Optional<Location> location() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? Optional.empty() : Optional.of(new Location(loaded, x, y, z));
    }

    public Optional<Location> center() {
        return location().map(location -> location.add(0.5D, 0.5D, 0.5D));
    }

    public Optional<Location> base() {
        return location().map(location -> {
            Location anchor = location.add(0.5D, 0.0D, 0.5D);
            anchor.setYaw(yaw);
            return anchor;
        });
    }

    public CratePlacement withHologram(UUID entity) {
        return new CratePlacement(crate, world, x, y, z, entity, yaw);
    }

    public CratePlacement withYaw(float value) {
        return new CratePlacement(crate, world, x, y, z, hologram, snap(value));
    }

    public CratePlacement rotated() {
        return withYaw(yaw + ROTATION_STEP);
    }

    public String facing() {
        int index = Math.round(wrap(yaw) / ROTATION_STEP) % 8;
        return switch (index) {
            case 1 -> "sud-ouest";
            case 2 -> "ouest";
            case 3 -> "nord-ouest";
            case 4 -> "nord";
            case 5 -> "nord-est";
            case 6 -> "est";
            case 7 -> "sud-est";
            default -> "sud";
        };
    }

    public boolean hasHologram() {
        return hologram != null;
    }

    public String coordinates() {
        return x + ", " + y + ", " + z;
    }

    public String describe() {
        return world + " " + coordinates();
    }
}
