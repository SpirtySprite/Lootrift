package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public enum CrateRarity {

    COMMUN("commun", Tr.t("Commun"), Palette.MUTED_HEX, "#C9D1D9", "◇", Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            () -> Sound.ENTITY_ITEM_PICKUP, () -> Particle.CLOUD, false, false),
    PEU_COMMUN("peu-commun", Tr.t("Peu commun"), "#4ADE80", "#BBF7D0", "◆", Material.LIME_STAINED_GLASS_PANE,
            () -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP, () -> Particle.HAPPY_VILLAGER, false, false),
    RARE("rare", Tr.t("Rare"), "#38BDF8", Tr.t("#BAE6FD"), "✦", Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            () -> Sound.BLOCK_NOTE_BLOCK_CHIME, () -> Particle.END_ROD, false, false),
    EPIQUE("epique", Tr.t("Épique"), "#A78BFA", "#F0ABFC", "❖", Material.PURPLE_STAINED_GLASS_PANE,
            () -> Sound.BLOCK_BEACON_POWER_SELECT, () -> Particle.WITCH, true, false),
    LEGENDAIRE("legendaire", Tr.t("Légendaire"), "#FBBF24", "#FEF08A", "✪", Material.ORANGE_STAINED_GLASS_PANE,
            () -> Sound.UI_TOAST_CHALLENGE_COMPLETE, () -> Particle.TOTEM_OF_UNDYING, true, true),
    MYTHIQUE("mythique", Tr.t("Mythique"), "#F87171", "#FDBA74", "✹", Material.RED_STAINED_GLASS_PANE,
            () -> Sound.ENTITY_ENDER_DRAGON_GROWL, () -> Particle.FLAME, true, true);

    private final String id;
    private final String displayName;
    private final String hex;
    private final String accent;
    private final String icon;
    private final Material pane;
    private final Supplier<Sound> sound;
    private final Supplier<Particle> particle;
    private final boolean announced;
    private final boolean firework;

    CrateRarity(String id, String displayName, String hex, String accent, String icon, Material pane,
                Supplier<Sound> sound, Supplier<Particle> particle, boolean announced, boolean firework) {
        this.id = id;
        this.displayName = displayName;
        this.hex = hex;
        this.accent = accent;
        this.icon = icon;
        this.pane = pane;
        this.sound = sound;
        this.particle = particle;
        this.announced = announced;
        this.firework = firework;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String hex() {
        return hex;
    }

    public String accent() {
        return accent;
    }

    public String icon() {
        return icon;
    }

    public String badge() {
        return color() + icon + " " + Card.small(displayName);
    }

    public String title(String text) {
        return Card.title(hex, accent, text);
    }

    public String color() {
        return "<" + hex + ">";
    }

    public String colored(String text) {
        return color() + text;
    }

    public String heading(String text) {
        return color() + "<b>" + text + "</b>";
    }

    public Material pane() {
        return pane;
    }

    public Sound sound() {
        return sound.get();
    }

    public Particle particle() {
        return particle.get();
    }

    public boolean announced() {
        return announced;
    }

    public boolean firework() {
        return firework;
    }

    public int tier() {
        return ordinal();
    }

    public boolean atLeast(CrateRarity other) {
        return ordinal() >= other.ordinal();
    }

    public static CrateRarity byId(String id, CrateRarity fallback) {
        return byId(id).orElse(fallback);
    }

    public static Optional<CrateRarity> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (CrateRarity rarity : values()) {
            if (rarity.id.equals(normalized) || rarity.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }

    public CrateRarity next() {
        return values()[Math.min(values().length - 1, ordinal() + 1)];
    }

    public CrateRarity previous() {
        return values()[Math.max(0, ordinal() - 1)];
    }
}
