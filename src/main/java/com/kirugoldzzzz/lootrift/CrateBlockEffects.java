package com.kirugoldzzzz.lootrift;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

public record CrateBlockEffects(CrateBlockAnimation animation, Particle particle,
                                double radius, double height, int density, double speed) {

    public static final double MIN_RADIUS = 0.2D;
    public static final double MAX_RADIUS = 3.0D;
    public static final double MIN_HEIGHT = 0.2D;
    public static final double MAX_HEIGHT = 4.0D;
    public static final int MIN_DENSITY = 1;
    public static final int MAX_DENSITY = 48;
    public static final double MIN_SPEED = 0.1D;
    public static final double MAX_SPEED = 4.0D;

    private static final CrateBlockEffects DEFAULT = new CrateBlockEffects(
            CrateBlockAnimation.HALO, Particle.END_ROD, 0.7D, 1.2D, 12, 1.0D);

    public CrateBlockEffects {
        animation = animation == null ? CrateBlockAnimation.AUCUNE : animation;
        particle = CrateBlockAnimation.usable(particle) ? particle : Particle.END_ROD;
        radius = clamp(radius, MIN_RADIUS, MAX_RADIUS);
        height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        density = (int) clamp(density, MIN_DENSITY, MAX_DENSITY);
        speed = clamp(speed, MIN_SPEED, MAX_SPEED);
    }

    public static CrateBlockEffects defaults() {
        return DEFAULT;
    }

    public static CrateBlockEffects read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        return new CrateBlockEffects(
                CrateBlockAnimation.byId(section.getString("animation"), CrateBlockAnimation.HALO),
                CrateBlockAnimation.particle(section.getString("particle"), Particle.END_ROD),
                section.getDouble("radius", DEFAULT.radius()),
                section.getDouble("height", DEFAULT.height()),
                section.getInt("density", DEFAULT.density()),
                section.getDouble("speed", DEFAULT.speed()));
    }

    public void write(ConfigurationSection section) {
        section.set("animation", animation.id());
        section.set("particle", particle.name());
        section.set("radius", radius);
        section.set("height", height);
        section.set("density", density);
        section.set("speed", speed);
    }

    public boolean silent() {
        return animation.silent();
    }

    public void emit(Location center, double phase) {
        animation.emit(center, particle, phase * speed, radius, height, density);
    }

    public CrateBlockEffects withAnimation(CrateBlockAnimation value) {
        return new CrateBlockEffects(value, particle, radius, height, density, speed);
    }

    public CrateBlockEffects withParticle(Particle value) {
        return new CrateBlockEffects(animation, value, radius, height, density, speed);
    }

    public CrateBlockEffects withRadius(double value) {
        return new CrateBlockEffects(animation, particle, value, height, density, speed);
    }

    public CrateBlockEffects withHeight(double value) {
        return new CrateBlockEffects(animation, particle, radius, value, density, speed);
    }

    public CrateBlockEffects withDensity(int value) {
        return new CrateBlockEffects(animation, particle, radius, height, value, speed);
    }

    public CrateBlockEffects withSpeed(double value) {
        return new CrateBlockEffects(animation, particle, radius, height, density, value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
