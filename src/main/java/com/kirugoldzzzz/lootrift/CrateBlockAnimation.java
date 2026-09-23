package com.kirugoldzzzz.lootrift;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CrateBlockAnimation {

    AUCUNE("aucune", "Aucune", Material.BARRIER,
            "Aucune particule autour de la caisse."),
    HALO("halo", "Halo", Material.END_ROD,
            "Un anneau qui tourne au-dessus du bloc."),
    ANNEAU_DOUBLE("anneau-double", "Double anneau", Material.ENDER_EYE,
            "Deux anneaux tournant en sens inverse."),
    SPIRALE("spirale", "Spirale", Material.STRING,
            "Une hélice qui monte le long du bloc."),
    DOUBLE_HELICE("double-helice", "Double hélice", Material.IRON_BARS,
            "Deux hélices entrelacées, façon ADN."),
    VORTEX("vortex", "Vortex", Material.ENDER_PEARL,
            "Une spirale qui se resserre en montant."),
    TORNADE("tornade", "Tornade", Material.WHITE_WOOL,
            "Un cône qui s'élargit vers le haut."),
    FONTAINE("fontaine", "Fontaine", Material.WATER_BUCKET,
            "Un jet qui retombe en cloche."),
    COLONNE("colonne", "Colonne", Material.BEACON,
            "Un faisceau vertical au centre du bloc."),
    ORBITE("orbite", "Orbite", Material.ENDER_EYE,
            "Quelques points en orbite inclinée."),
    SPHERE("sphere", "Sphère", Material.SLIME_BALL,
            "Une coquille sphérique scintillante."),
    CUBE("cube", "Cube", Material.GLASS,
            "Les douze arêtes d'un cube en rotation."),
    COURONNE("couronne", "Couronne", Material.GOLDEN_HELMET,
            "Un anneau hérissé de pointes."),
    ETOILE("etoile", "Étoile", Material.NETHER_STAR,
            "Une étoile à cinq branches qui tourne."),
    PULSATION("pulsation", "Pulsation", Material.HEART_OF_THE_SEA,
            "Un anneau qui enfle et se rétracte."),
    ONDE("onde", "Onde", Material.PRISMARINE_SHARD,
            "Des vagues concentriques qui s'éloignent."),
    PAPILLON("papillon", "Papillon", Material.PINK_PETALS,
            "Une courbe de Lissajous en huit."),
    PLUIE("pluie", "Pluie", Material.BLUE_ICE,
            "Des particules qui tombent autour du bloc."),
    TOURBILLON("tourbillon", "Tourbillon", Material.SOUL_SAND,
            "Un remous plaqué au sol."),
    ASCENSION("ascension", "Ascension", Material.FEATHER,
            "Des points qui montent en colonnes décalées."),
    ATOME("atome", "Atome", Material.AMETHYST_SHARD,
            "Trois anneaux inclinés comme des orbitales."),
    GALAXIE("galaxie", "Galaxie", Material.NETHER_STAR,
            "Deux bras spiraux qui tournent à plat."),
    DOME("dome", "Dôme", Material.TURTLE_HELMET,
            "Une demi sphère posée sur le bloc."),
    CROIX("croix", "Croix", Material.NETHERITE_SWORD,
            "Quatre branches qui pivotent."),
    LOSANGE("losange", "Losange", Material.DIAMOND,
            "Un losange vertical en rotation."),
    SABLIER("sablier", "Sablier", Material.SAND,
            "Deux cônes opposés qui se rejoignent."),
    SATURNE("saturne", "Saturne", Material.ENDER_EYE,
            "Une sphère ceinte d'un anneau plat."),
    SERPENTIN("serpentin", "Serpentin", Material.LEAD,
            "Une hélice qui ondule en montant."),
    BATTEMENT("battement", "Battement", Material.REDSTONE,
            "Deux pulsations rapprochées, comme un cœur."),
    COMETE("comete", "Comète", Material.FIRE_CHARGE,
            "Un point rapide qui laisse une traînée.");

    private static final List<Particle> CANDIDATES = List.of(
            Particle.END_ROD, Particle.FLAME, Particle.SOUL_FIRE_FLAME, Particle.HAPPY_VILLAGER,
            Particle.WITCH, Particle.PORTAL, Particle.DRAGON_BREATH, Particle.TOTEM_OF_UNDYING,
            Particle.ELECTRIC_SPARK, Particle.GLOW, Particle.CRIT, Particle.ENCHANTED_HIT,
            Particle.NOTE, Particle.HEART, Particle.CLOUD, Particle.FIREWORK,
            Particle.SNOWFLAKE, Particle.WAX_ON, Particle.SCULK_SOUL, Particle.CHERRY_LEAVES,
            Particle.COMPOSTER, Particle.SPORE_BLOSSOM_AIR, Particle.REVERSE_PORTAL,
            Particle.ASH, Particle.SMALL_FLAME, Particle.NAUTILUS, Particle.BUBBLE_POP,
            Particle.SOUL, Particle.SCULK_CHARGE_POP, Particle.DRIPPING_LAVA,
            Particle.FALLING_WATER, Particle.WHITE_ASH, Particle.CRIMSON_SPORE,
            Particle.WARPED_SPORE, Particle.SPIT, Particle.SPLASH,
            Particle.EGG_CRACK, Particle.GLOW_SQUID_INK, Particle.SQUID_INK,
            Particle.DRIPPING_HONEY, Particle.LANDING_HONEY, Particle.WAX_OFF,
            Particle.DUST_PLUME, Particle.TRIAL_SPAWNER_DETECTION,
            Particle.OMINOUS_SPAWNING, Particle.INFESTED, Particle.RAID_OMEN, Particle.TRIAL_OMEN);

    public static final List<Particle> PALETTE = CANDIDATES.stream()
            .filter(CrateBlockAnimation::usable)
            .toList();

    public static boolean usable(Particle particle) {
        return particle != null && particle.getDataType() == Void.class;
    }

    private static final double TAU = Math.PI * 2.0D;

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;

    CrateBlockAnimation(String id, String displayName, Material icon, String description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean silent() {
        return this == AUCUNE;
    }

    public void emit(Location center, Particle particle, double phase, double radius,
                     double height, int density) {
        if (silent() || density <= 0) {
            return;
        }
        World world = center.getWorld();
        switch (this) {
            case HALO -> ring(world, center, particle, phase, radius, height, density, 1);
            case ANNEAU_DOUBLE -> {
                ring(world, center, particle, phase, radius, height * 0.75D, density / 2, 1);
                ring(world, center, particle, -phase, radius * 0.7D, height * 1.15D, density / 2, 1);
            }
            case SPIRALE -> helix(world, center, particle, phase, radius, height, density, 1);
            case DOUBLE_HELICE -> helix(world, center, particle, phase, radius, height, density, 2);
            case VORTEX -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase * 1.6D + progress * TAU * 2.0D;
                    double shrink = radius * (1.0D - progress * 0.85D);
                    spawn(world, center, particle, Math.cos(angle) * shrink,
                            progress * height, Math.sin(angle) * shrink);
                }
            }
            case TORNADE -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase * 2.0D + progress * TAU * 2.5D;
                    double widen = radius * (0.15D + progress * 1.1D);
                    spawn(world, center, particle, Math.cos(angle) * widen,
                            progress * height, Math.sin(angle) * widen);
                }
            }
            case FONTAINE -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase + progress * TAU;
                    double arc = Math.sin(progress * Math.PI);
                    spawn(world, center, particle, Math.cos(angle) * radius * progress,
                            arc * height, Math.sin(angle) * radius * progress);
                }
            }
            case COLONNE -> {
                for (int index = 0; index < density; index++) {
                    double offset = (index / (double) density + phase / TAU) % 1.0D;
                    spawn(world, center, particle, 0.0D, offset * height, 0.0D);
                }
            }
            case ORBITE -> {
                int points = Math.max(1, density / 3);
                for (int index = 0; index < points; index++) {
                    double angle = phase * 1.4D + index * TAU / points;
                    spawn(world, center, particle, Math.cos(angle) * radius,
                            height * 0.5D + Math.sin(angle * 2.0D) * height * 0.4D,
                            Math.sin(angle) * radius);
                }
            }
            case SPHERE -> {
                for (int index = 0; index < density; index++) {
                    double t = (index + 0.5D) / density;
                    double inclination = Math.acos(1.0D - 2.0D * t);
                    double azimuth = TAU * 1.618033988749895D * index + phase;
                    double sin = Math.sin(inclination);
                    spawn(world, center, particle,
                            Math.cos(azimuth) * sin * radius,
                            height * 0.5D + Math.cos(inclination) * radius,
                            Math.sin(azimuth) * sin * radius);
                }
            }
            case CUBE -> cube(world, center, particle, phase, radius, height, density);
            case COURONNE -> {
                for (int index = 0; index < density; index++) {
                    double angle = phase + index * TAU / density;
                    double spike = index % 2 == 0 ? 1.0D : 0.55D;
                    spawn(world, center, particle, Math.cos(angle) * radius,
                            height * spike, Math.sin(angle) * radius);
                }
            }
            case ETOILE -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase + progress * TAU;
                    double star = radius * (0.45D + 0.55D * Math.abs(Math.cos(angle * 2.5D)));
                    spawn(world, center, particle, Math.cos(angle) * star,
                            height, Math.sin(angle) * star);
                }
            }
            case PULSATION -> {
                double breathe = radius * (0.35D + 0.65D * (0.5D + 0.5D * Math.sin(phase)));
                ring(world, center, particle, phase * 0.3D, breathe, height, density, 1);
            }
            case ONDE -> {
                int rings = Math.max(1, density / 8);
                for (int wave = 0; wave < rings; wave++) {
                    double travel = ((phase / TAU) + wave / (double) rings) % 1.0D;
                    ring(world, center, particle, phase, radius * travel,
                            height * 0.2D, Math.max(4, density / rings), 1);
                }
            }
            case PAPILLON -> {
                for (int index = 0; index < density; index++) {
                    double t = phase + index * TAU / density;
                    spawn(world, center, particle, Math.sin(t * 2.0D) * radius,
                            height * 0.5D + Math.sin(t * 3.0D) * height * 0.45D,
                            Math.sin(t) * radius);
                }
            }
            case PLUIE -> {
                for (int index = 0; index < density; index++) {
                    double angle = phase * 0.7D + index * TAU / density;
                    double fall = 1.0D - ((phase / TAU * 0.5D + index / (double) density) % 1.0D);
                    spawn(world, center, particle, Math.cos(angle) * radius,
                            fall * height, Math.sin(angle) * radius);
                }
            }
            case TOURBILLON -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase * 2.2D + progress * TAU * 3.0D;
                    spawn(world, center, particle, Math.cos(angle) * radius * progress,
                            0.15D, Math.sin(angle) * radius * progress);
                }
            }
            case ASCENSION -> {
                int columns = Math.max(2, density / 4);
                for (int index = 0; index < columns; index++) {
                    double angle = index * TAU / columns;
                    double rise = ((phase / TAU) + index / (double) columns) % 1.0D;
                    spawn(world, center, particle, Math.cos(angle) * radius,
                            rise * height, Math.sin(angle) * radius);
                }
            }
            case ATOME -> {
                int perRing = Math.max(2, density / 3);
                for (int ring = 0; ring < 3; ring++) {
                    double tilt = ring * Math.PI / 3.0D;
                    for (int index = 0; index < perRing; index++) {
                        double angle = phase * 1.3D + index * TAU / perRing;
                        double x = Math.cos(angle) * radius;
                        double flat = Math.sin(angle) * radius;
                        spawn(world, center, particle, x,
                                height * 0.5D + flat * Math.sin(tilt),
                                flat * Math.cos(tilt));
                    }
                }
            }
            case GALAXIE -> {
                int perArm = Math.max(2, density / 2);
                for (int arm = 0; arm < 2; arm++) {
                    double offset = arm * Math.PI;
                    for (int index = 0; index < perArm; index++) {
                        double progress = index / (double) perArm;
                        double angle = phase + offset + progress * TAU;
                        double reach = radius * progress;
                        spawn(world, center, particle, Math.cos(angle) * reach,
                                height * 0.35D, Math.sin(angle) * reach);
                    }
                }
            }
            case DOME -> {
                for (int index = 0; index < density; index++) {
                    double t = (index + 0.5D) / density;
                    double inclination = Math.acos(1.0D - t);
                    double azimuth = TAU * 1.618033988749895D * index + phase;
                    double sin = Math.sin(inclination);
                    spawn(world, center, particle,
                            Math.cos(azimuth) * sin * radius,
                            Math.cos(inclination) * height,
                            Math.sin(azimuth) * sin * radius);
                }
            }
            case CROIX -> {
                int perArm = Math.max(1, density / 4);
                for (int arm = 0; arm < 4; arm++) {
                    double angle = phase + arm * Math.PI / 2.0D;
                    for (int index = 1; index <= perArm; index++) {
                        double reach = radius * index / perArm;
                        spawn(world, center, particle, Math.cos(angle) * reach,
                                height * 0.6D, Math.sin(angle) * reach);
                    }
                }
            }
            case LOSANGE -> {
                int perEdge = Math.max(1, density / 4);
                double cos = Math.cos(phase);
                double sin = Math.sin(phase);
                for (int edge = 0; edge < 4; edge++) {
                    for (int index = 0; index < perEdge; index++) {
                        double t = index / (double) perEdge;
                        double along = edge % 2 == 0 ? t : 1.0D - t;
                        double flat = radius * (edge < 2 ? along : -along);
                        double lift = height * (edge % 2 == 0 ? 1.0D - t : t);
                        spawn(world, center, particle, flat * cos, lift, flat * sin);
                    }
                }
            }
            case SABLIER -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase * 1.5D + progress * TAU * 2.0D;
                    double waist = Math.abs(progress - 0.5D) * 2.0D;
                    spawn(world, center, particle, Math.cos(angle) * radius * waist,
                            progress * height, Math.sin(angle) * radius * waist);
                }
            }
            case SATURNE -> {
                int shell = Math.max(1, density / 2);
                for (int index = 0; index < shell; index++) {
                    double t = (index + 0.5D) / shell;
                    double inclination = Math.acos(1.0D - 2.0D * t);
                    double azimuth = TAU * 1.618033988749895D * index + phase;
                    double sin = Math.sin(inclination);
                    spawn(world, center, particle,
                            Math.cos(azimuth) * sin * radius * 0.6D,
                            height * 0.5D + Math.cos(inclination) * radius * 0.6D,
                            Math.sin(azimuth) * sin * radius * 0.6D);
                }
                ring(world, center, particle, phase * 1.4D, radius * 1.25D,
                        height * 0.5D, Math.max(1, density - shell), 1);
            }
            case SERPENTIN -> {
                for (int index = 0; index < density; index++) {
                    double progress = index / (double) density;
                    double angle = phase + progress * TAU * 3.0D;
                    double wobble = radius * (0.6D + 0.4D * Math.sin(progress * Math.PI * 4.0D));
                    spawn(world, center, particle, Math.cos(angle) * wobble,
                            progress * height, Math.sin(angle) * wobble);
                }
            }
            case BATTEMENT -> {
                double beat = Math.sin(phase) * Math.sin(phase * 2.0D);
                double pulse = radius * (0.4D + 0.6D * Math.abs(beat));
                ring(world, center, particle, phase * 0.2D, pulse, height * 0.5D, density, 1);
            }
            case COMETE -> {
                int trail = Math.max(2, density);
                for (int index = 0; index < trail; index++) {
                    double lag = index * 0.12D;
                    double angle = phase * 2.4D - lag;
                    double fade = 1.0D - index / (double) trail;
                    spawn(world, center, particle, Math.cos(angle) * radius,
                            height * (0.4D + 0.5D * fade), Math.sin(angle) * radius);
                }
            }
            default -> {
            }
        }
    }

    private static void ring(World world, Location center, Particle particle, double phase,
                             double radius, double height, int density, int turns) {
        int points = Math.max(1, density);
        for (int index = 0; index < points; index++) {
            double angle = phase + index * TAU * turns / points;
            spawn(world, center, particle, Math.cos(angle) * radius, height,
                    Math.sin(angle) * radius);
        }
    }

    private static void helix(World world, Location center, Particle particle, double phase,
                              double radius, double height, int density, int strands) {
        int points = Math.max(1, density / Math.max(1, strands));
        for (int strand = 0; strand < strands; strand++) {
            double offset = strand * Math.PI * 2.0D / strands;
            for (int index = 0; index < points; index++) {
                double progress = index / (double) points;
                double angle = phase + offset + progress * TAU * 2.0D;
                spawn(world, center, particle, Math.cos(angle) * radius,
                        progress * height, Math.sin(angle) * radius);
            }
        }
    }

    private static void cube(World world, Location center, Particle particle, double phase,
                             double radius, double height, int density) {
        double cos = Math.cos(phase);
        double sin = Math.sin(phase);
        int perEdge = Math.max(1, density / 12);
        double[][] corners = {
                {-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}};
        for (int corner = 0; corner < 4; corner++) {
            double[] from = corners[corner];
            double[] to = corners[(corner + 1) % 4];
            for (int index = 0; index < perEdge; index++) {
                double t = index / (double) perEdge;
                double x = from[0] + (to[0] - from[0]) * t;
                double z = from[1] + (to[1] - from[1]) * t;
                double rx = x * cos - z * sin;
                double rz = x * sin + z * cos;
                spawn(world, center, particle, rx, 0.1D, rz);
                spawn(world, center, particle, rx, height, rz);
            }
            double x = from[0] * cos - from[1] * sin;
            double z = from[0] * sin + from[1] * cos;
            for (int index = 0; index < perEdge; index++) {
                spawn(world, center, particle, x, 0.1D + index / (double) perEdge * height, z);
            }
        }
    }

    private static void spawn(World world, Location center, Particle particle,
                              double dx, double dy, double dz) {
        world.spawnParticle(particle, center.getX() + dx, center.getY() + dy,
                center.getZ() + dz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public CrateBlockAnimation next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrateBlockAnimation byId(String id, CrateBlockAnimation fallback) {
        return byId(id).orElse(fallback);
    }

    public static Optional<CrateBlockAnimation> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (CrateBlockAnimation animation : values()) {
            if (animation.id.equals(normalized)
                    || animation.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(animation);
            }
        }
        return Optional.empty();
    }

    public static Particle particle(String name, Particle fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            Particle resolved = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return usable(resolved) ? resolved : fallback;
        } catch (IllegalArgumentException failure) {
            return fallback;
        }
    }
}
