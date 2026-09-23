package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Locale;

public final class CrateEffectsMenu {

    private final CrateService service;
    private final CrateEditor editor;

    public CrateEffectsMenu(CrateService service, CrateEditor editor) {
        this.service = service;
        this.editor = editor;
    }

    public void open(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        CrateBlockEffects effects = crate.blockEffects();

        Gui gui = Gui.builder()
                .rows(5)
                .title(Mini.parse(Palette.title(Tr.t("Effets de la caisse"))))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, preview(crate, effects));

        gui.setItem(3, 2, animationButton(crate, effects, back));
        gui.setItem(3, 4, particleButton(crate, effects, back));
        gui.setItem(3, 6, radiusButton(crate, effects, back));
        gui.setItem(3, 8, heightButton(crate, effects, back));

        gui.setItem(4, 4, densityButton(crate, effects, back));
        gui.setItem(4, 6, speedButton(crate, effects, back));

        gui.setItem(5, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(5, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem preview(Crate crate, CrateBlockEffects effects) {
        return Guis.display(effects.animation().icon(),
                Palette.heading(effects.animation().displayName()), Lore.create()
                        .blank()
                        .text(effects.animation().description())
                        .blank()
                        .entry(Tr.t("Caisse"), crate.displayName())
                        .entry(Tr.t("Particule"), pretty(effects.particle()))
                        .entry(Tr.t("Rayon"), format(effects.radius()))
                        .entry(Tr.t("Hauteur"), format(effects.height()))
                        .count(Tr.t("Densité"), effects.density())
                        .entry(Tr.t("Vitesse"), format(effects.speed()) + "x")
                        .blank()
                        .hint(Tr.t("Posez la caisse pour voir le rendu"))
                        .build());
    }

    private GuiItem animationButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return Guis.button(Material.FIREWORK_ROCKET, Palette.heading(Tr.t("Figure")), Lore.create()
                .blank()
                .highlight(Tr.t("Actuelle"), effects.animation().displayName())
                .count(Tr.t("Figures disponibles"), CrateBlockAnimation.values().length)
                .blank()
                .action(Tr.t("Cliquer pour choisir"))
                .build(), player -> openAnimations(player, crate, back));
    }

    public void openAnimations(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        CrateBlockEffects effects = crate.blockEffects();

        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Figures"))))
                .create();
        Guis.paginationBar(gui, () -> reopen(player, crate.id(), back));

        for (CrateBlockAnimation animation : CrateBlockAnimation.values()) {
            boolean current = animation == effects.animation();
            gui.addPageItem(ItemBuilder.of(animation.icon())
                    .name(Mini.label(current
                            ? Palette.SUCCESS + "<b>" + animation.displayName() + "</b>"
                            : Palette.heading(animation.displayName())))
                    .loreComponents(Mini.labels(Lore.create()
                            .blank()
                            .text(animation.description())
                            .blank()
                            .state(Tr.t("Sélectionnée"), current, "oui", "non")
                            .blank()
                            .action(current ? Tr.t("Déjà active") : Tr.t("Cliquer pour appliquer"))
                            .build()))
                    .glow(current)
                    .asGuiItem(event -> {
                        Player viewer = (Player) event.getWhoClicked();
                        editor.setBlockEffects(crate.id(),
                                crate.blockEffects().withAnimation(animation));
                        Guis.success(viewer);
                        reopen(viewer, crate.id(), back);
                    }));
        }

        Guis.controls(gui);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem particleButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return Guis.button(Material.BLAZE_POWDER, Palette.heading(Tr.t("Particule")), Lore.create()
                .blank()
                .highlight(Tr.t("Actuelle"), pretty(effects.particle()))
                .count(Tr.t("Particules proposées"), CrateBlockAnimation.PALETTE.size())
                .blank()
                .action(Tr.t("Cliquer pour choisir"))
                .build(), player -> openParticles(player, crate, back));
    }

    public void openParticles(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        CrateBlockEffects effects = crate.blockEffects();

        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Particules"))))
                .create();
        Guis.paginationBar(gui, () -> reopen(player, crate.id(), back));

        for (Particle particle : CrateBlockAnimation.PALETTE) {
            boolean current = particle == effects.particle();
            gui.addPageItem(Guis.item(Material.GLASS_BOTTLE,
                    current
                            ? Palette.SUCCESS + "<b>" + pretty(particle) + "</b>"
                            : Palette.heading(pretty(particle)),
                    Lore.create()
                            .blank()
                            .entry(Tr.t("Identifiant"), particle.name())
                            .blank()
                            .state(Tr.t("Sélectionnée"), current, "oui", "non")
                            .blank()
                            .action(current ? Tr.t("Déjà active") : Tr.t("Cliquer pour appliquer"))
                            .build(),
                    current,
                    event -> {
                        Player viewer = (Player) event.getWhoClicked();
                        editor.setBlockEffects(crate.id(),
                                crate.blockEffects().withParticle(particle));
                        Guis.success(viewer);
                        reopen(viewer, crate.id(), back);
                    }));
        }

        Guis.controls(gui);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem radiusButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return stepper(Material.REPEATER, Tr.t("Rayon"), format(effects.radius()) + " blocs",
                CrateBlockEffects.MIN_RADIUS, CrateBlockEffects.MAX_RADIUS, effects.radius(), 0.1D,
                crate, back, value -> crate.blockEffects().withRadius(value));
    }

    private GuiItem heightButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return stepper(Material.SCAFFOLDING, Tr.t("Hauteur"), format(effects.height()) + " blocs",
                CrateBlockEffects.MIN_HEIGHT, CrateBlockEffects.MAX_HEIGHT, effects.height(), 0.1D,
                crate, back, value -> crate.blockEffects().withHeight(value));
    }

    private GuiItem densityButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return stepper(Material.SUGAR, Tr.t("Densité"), effects.density() + Tr.t(" particules par image"),
                CrateBlockEffects.MIN_DENSITY, CrateBlockEffects.MAX_DENSITY,
                effects.density(), 1.0D,
                crate, back, value -> crate.blockEffects().withDensity((int) Math.round(value)));
    }

    private GuiItem speedButton(Crate crate, CrateBlockEffects effects, Runnable back) {
        return stepper(Material.SUGAR_CANE, Tr.t("Vitesse"), format(effects.speed()) + "x",
                CrateBlockEffects.MIN_SPEED, CrateBlockEffects.MAX_SPEED, effects.speed(), 0.1D,
                crate, back, value -> crate.blockEffects().withSpeed(value));
    }

    private GuiItem stepper(Material icon, String label, String current, double min, double max,
                            double value, double step, Crate crate, Runnable back,
                            java.util.function.DoubleFunction<CrateBlockEffects> apply) {
        return ItemBuilder.of(icon)
                .name(Mini.label(Palette.heading(label)))
                .loreComponents(Mini.labels(Lore.create()
                        .blank()
                        .highlight(Tr.t("Actuel"), current)
                        .entry(Tr.t("Minimum"), format(min))
                        .entry(Tr.t("Maximum"), format(max))
                        .blank()
                        .click(Tr.t("Clic gauche"), "augmenter")
                        .denyClick(Tr.t("Clic droit"), "diminuer")
                        .hint(Tr.t("Shift pour un pas de ") + format(step * 5.0D))
                        .build()))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    double updated = stepped(value, step * (click.isShiftClick() ? 5.0D : 1.0D),
                            click.isRightClick(), min, max);
                    if (Double.isNaN(updated)) {
                        Guis.deny(player);
                        return;
                    }
                    editor.setBlockEffects(crate.id(), apply.apply(updated));
                    Guis.click(player);
                    reopen(player, crate.id(), back);
                });
    }

    private void reopen(Player player, String crateId, Runnable back) {
        service.crate(crateId).ifPresentOrElse(
                crate -> open(player, crate, back),
                () -> {
                    if (back != null) {
                        back.run();
                    } else {
                        player.closeInventory();
                    }
                });
    }

    static double stepped(double value, double delta, boolean decrease, double min, double max) {
        double updated = Math.round((decrease ? value - delta : value + delta) * 100.0D) / 100.0D;
        if (updated < min - 0.001D || updated > max + 0.001D) {
            return Double.NaN;
        }
        return Math.max(min, Math.min(max, updated));
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String pretty(Particle particle) {
        String name = particle.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
