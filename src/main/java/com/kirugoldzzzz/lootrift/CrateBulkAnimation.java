package com.kirugoldzzzz.lootrift;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CrateBulkAnimation {

    AUCUNE("aucune", "Aucune", Material.BARRIER, 0,
            "Le butin apparaît d'un coup, sans mise en scène."),
    DOMINO("domino", "Domino", Material.OAK_BUTTON, 3,
            "Les gains se révèlent dans l'ordre de lecture."),
    INVERSE("inverse", "Inverse", Material.OBSERVER, 3,
            "La révélation part du dernier gain vers le premier."),
    AVALANCHE("avalanche", "Avalanche", Material.SNOW_BLOCK, 2,
            "Les colonnes se remplissent de haut en bas."),
    VAGUE("vague", "Vague", Material.PRISMARINE, 3,
            "Chaque ligne est balayée en sens alterné."),
    SPIRALE("spirale", "Spirale", Material.NAUTILUS_SHELL, 3,
            "La révélation s'enroule des bords vers le centre."),
    EXPLOSION("explosion", "Explosion", Material.TNT, 2,
            "Les gains jaillissent du centre vers les bords."),
    IMPLOSION("implosion", "Implosion", Material.END_CRYSTAL, 2,
            "Les bords se révèlent d'abord, le centre en dernier."),
    MIROIR("miroir", "Miroir", Material.GLASS_PANE, 3,
            "Les deux extrémités convergent vers le milieu."),
    RIDEAU("rideau", "Rideau", Material.WHITE_BANNER, 3,
            "Le haut et le bas se rejoignent au centre."),
    DIAGONALE("diagonale", "Diagonale", Material.SCAFFOLDING, 3,
            "La révélation descend en diagonale."),
    SCANNER("scanner", "Scanner", Material.SPYGLASS, 2,
            "Une colonne balaie la grille de gauche à droite."),
    RAFALE("rafale", "Rafale", Material.FIREWORK_ROCKET, 1,
            "Les gains éclatent dans un ordre imprévisible.");

    private static final int SHUFFLE_STRIDE = 7;

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int ticksPerReveal;
    private final String description;

    CrateBulkAnimation(String id, String displayName, Material icon, int ticksPerReveal,
                       String description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.ticksPerReveal = ticksPerReveal;
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

    public int ticksPerReveal() {
        return ticksPerReveal;
    }

    public String description() {
        return description;
    }

    public boolean instant() {
        return ticksPerReveal <= 0;
    }

    public List<Integer> order(List<Integer> slots) {
        List<Integer> ordered = new ArrayList<>(slots);
        switch (this) {
            case INVERSE -> java.util.Collections.reverse(ordered);
            case AVALANCHE, SCANNER -> ordered.sort(Comparator
                    .comparingInt(CrateBulkAnimation::column)
                    .thenComparingInt(CrateBulkAnimation::row));
            case VAGUE -> ordered.sort(Comparator
                    .comparingInt(CrateBulkAnimation::row)
                    .thenComparingInt(slot -> row(slot) % 2 == 0
                            ? column(slot) : -column(slot)));
            case DIAGONALE -> ordered.sort(Comparator
                    .comparingInt((Integer slot) -> row(slot) + column(slot))
                    .thenComparingInt(CrateBulkAnimation::row));
            case EXPLOSION -> ordered.sort(Comparator
                    .comparingInt(CrateBulkAnimation::distanceFromCentre)
                    .thenComparingInt(slot -> slot));
            case IMPLOSION -> ordered.sort(Comparator
                    .comparingInt(CrateBulkAnimation::distanceFromCentre).reversed()
                    .thenComparingInt(slot -> slot));
            case SPIRALE -> ordered.sort(Comparator
                    .comparingInt(CrateBulkAnimation::ring)
                    .thenComparingInt(CrateBulkAnimation::angleAround));
            case MIROIR -> {
                List<Integer> mirrored = new ArrayList<>(ordered.size());
                int low = 0;
                int high = ordered.size() - 1;
                while (low <= high) {
                    mirrored.add(ordered.get(low));
                    if (low != high) {
                        mirrored.add(ordered.get(high));
                    }
                    low++;
                    high--;
                }
                return mirrored;
            }
            case RIDEAU -> {
                ordered.sort(Comparator.comparingInt(CrateBulkAnimation::row));
                List<Integer> curtain = new ArrayList<>(ordered.size());
                int low = 0;
                int high = ordered.size() - 1;
                while (low <= high) {
                    curtain.add(ordered.get(low));
                    if (low != high) {
                        curtain.add(ordered.get(high));
                    }
                    low++;
                    high--;
                }
                return curtain;
            }
            case RAFALE -> {
                int size = ordered.size();
                int stride = SHUFFLE_STRIDE;
                while (size > 1 && greatestDivisor(stride, size) != 1) {
                    stride++;
                }
                List<Integer> scattered = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    scattered.add(ordered.get(index * stride % size));
                }
                return scattered;
            }
            default -> {
            }
        }
        return ordered;
    }

    private static int greatestDivisor(int left, int right) {
        while (right != 0) {
            int next = left % right;
            left = right;
            right = next;
        }
        return left;
    }

    private static int row(int slot) {
        return slot / 9;
    }

    private static int column(int slot) {
        return slot % 9;
    }

    private static int distanceFromCentre(int slot) {
        return Math.abs(row(slot) - 2) + Math.abs(column(slot) - 4);
    }

    private static int ring(int slot) {
        return Math.max(Math.abs(row(slot) - 2), Math.abs(column(slot) - 4));
    }

    private static int angleAround(int slot) {
        return (int) Math.round(Math.toDegrees(
                Math.atan2(row(slot) - 2.0D, column(slot) - 4.0D)) + 360.0D) % 360;
    }

    public CrateBulkAnimation next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CrateBulkAnimation byId(String id, CrateBulkAnimation fallback) {
        return byId(id).orElse(fallback);
    }

    public static Optional<CrateBulkAnimation> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (CrateBulkAnimation animation : values()) {
            if (animation.id.equals(normalized)
                    || animation.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(animation);
            }
        }
        return Optional.empty();
    }
}
