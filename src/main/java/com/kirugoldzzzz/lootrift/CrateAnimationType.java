package com.kirugoldzzzz.lootrift;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CrateAnimationType {

    CSGO("csgo", "Défilement CS:GO", Material.ENDER_EYE, 3, 120,
            List.of("Un ruban d'objets défile horizontalement",
                    "et ralentit jusqu'à s'arrêter sur le gain.")),
    ROULETTE("roulette", "Roulette", Material.CLOCK, 5, 130,
            List.of("Un curseur tourne autour du menu",
                    "et ralentit jusqu'au gain.")),
    CASCADE("cascade", "Cascade", Material.WATER_BUCKET, 6, 110,
            List.of("Les objets tombent colonne par colonne",
                    "avant de révéler le gain au centre.")),
    ROUE("roue", "Roue", Material.SUNFLOWER, 5, 120,
            List.of("Une roue 3x3 tourne autour du centre",
                    "puis se referme sur le gain.")),
    PULSE("pulse", "Pulsation", Material.NETHER_STAR, 3, 90,
            List.of("Un objet unique clignote de plus en plus",
                    "lentement avant de se figer.")),
    TOMBOLA("tombola", "Tombola", Material.HOPPER, 5, 125,
            List.of("Un ruban vertical défile et ralentit",
                    "jusqu'au gain, au centre.")),
    ECLAIR("eclair", "Éclair", Material.LIGHTNING_ROD, 3, 100,
            List.of("Neuf cases clignotent ensemble",
                    "puis se figent sur le gain.")),
    HORLOGE("horloge", "Horloge", Material.CLOCK, 5, 130,
            List.of("Une aiguille fait le tour du cadran",
                    "et s'arrête sur le gain.")),
    VAGUE("vague", "Vague", Material.PRISMARINE, 5, 120,
            List.of("Une vague balaie les lignes",
                    "et se pose sur la bonne.")),
    ZOOM("zoom", "Zoom", Material.SPYGLASS, 5, 110,
            List.of("Les anneaux se referment depuis",
                    "les bords vers le centre.")),
    MOSAIQUE("mosaique", "Mosaïque", Material.GLOW_ITEM_FRAME, 6, 135,
            List.of("La grille se fige case par case",
                    "jusqu'à ne laisser que le gain.")),
    INSTANT("instant", "Instantané", Material.REDSTONE_TORCH, 3, 0,
            List.of("Aucune animation, le gain est remis",
                    "immédiatement."));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int rows;
    private final int frames;
    private final List<String> description;

    CrateAnimationType(String id, String displayName, Material icon, int rows, int frames,
                       List<String> description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.rows = rows;
        this.frames = frames;
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

    public int rows() {
        return rows;
    }

    public int frames() {
        return frames;
    }

    public List<String> description() {
        return description;
    }

    public boolean instant() {
        return frames <= 0;
    }

    public CrateAnimationType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrateAnimationType byId(String id, CrateAnimationType fallback) {
        return byId(id).orElse(fallback);
    }

    public static Optional<CrateAnimationType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (CrateAnimationType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
