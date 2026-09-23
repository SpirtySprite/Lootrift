package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CrateAnimationType {

    CSGO("csgo", Tr.t("Défilement CS:GO"), Material.ENDER_EYE, 3, 120,
            List.of(Tr.t("Un ruban d'objets défile horizontalement"),
                    Tr.t("et ralentit jusqu'à s'arrêter sur le gain."))),
    ROULETTE("roulette", Tr.t("Roulette"), Material.CLOCK, 5, 130,
            List.of(Tr.t("Un curseur tourne autour du menu"),
                    Tr.t("et ralentit jusqu'au gain."))),
    CASCADE("cascade", Tr.t("Cascade"), Material.WATER_BUCKET, 6, 110,
            List.of(Tr.t("Les objets tombent colonne par colonne"),
                    Tr.t("avant de révéler le gain au centre."))),
    ROUE("roue", Tr.t("Roue"), Material.SUNFLOWER, 5, 120,
            List.of(Tr.t("Une roue 3x3 tourne autour du centre"),
                    Tr.t("puis se referme sur le gain."))),
    PULSE("pulse", Tr.t("Pulsation"), Material.NETHER_STAR, 3, 90,
            List.of(Tr.t("Un objet unique clignote de plus en plus"),
                    Tr.t("lentement avant de se figer."))),
    TOMBOLA("tombola", Tr.t("Tombola"), Material.HOPPER, 5, 125,
            List.of(Tr.t("Un ruban vertical défile et ralentit"),
                    Tr.t("jusqu'au gain, au centre."))),
    ECLAIR("eclair", Tr.t("Éclair"), Material.LIGHTNING_ROD, 3, 100,
            List.of(Tr.t("Neuf cases clignotent ensemble"),
                    Tr.t("puis se figent sur le gain."))),
    HORLOGE("horloge", Tr.t("Horloge"), Material.CLOCK, 5, 130,
            List.of(Tr.t("Une aiguille fait le tour du cadran"),
                    Tr.t("et s'arrête sur le gain."))),
    VAGUE("vague", Tr.t("Vague"), Material.PRISMARINE, 5, 120,
            List.of(Tr.t("Une vague balaie les lignes"),
                    Tr.t("et se pose sur la bonne."))),
    ZOOM("zoom", Tr.t("Zoom"), Material.SPYGLASS, 5, 110,
            List.of(Tr.t("Les anneaux se referment depuis"),
                    Tr.t("les bords vers le centre."))),
    MOSAIQUE("mosaique", Tr.t("Mosaïque"), Material.GLOW_ITEM_FRAME, 6, 135,
            List.of(Tr.t("La grille se fige case par case"),
                    Tr.t("jusqu'à ne laisser que le gain."))),
    INSTANT("instant", Tr.t("Instantané"), Material.REDSTONE_TORCH, 3, 0,
            List.of(Tr.t("Aucune animation, le gain est remis"),
                    Tr.t("immédiatement.")));

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
