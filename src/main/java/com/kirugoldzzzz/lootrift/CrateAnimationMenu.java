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
import org.bukkit.entity.Player;

public final class CrateAnimationMenu {

    private final CrateEditor editor;

    public CrateAnimationMenu(CrateEditor editor) {
        this.editor = editor;
    }

    public void openBulk(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Animations groupées"))))
                .create();
        Guis.paginationBar(gui, back);

        for (CrateBulkAnimation animation : CrateBulkAnimation.values()) {
            boolean current = animation == crate.bulkAnimation();
            gui.addPageItem(ItemBuilder.of(animation.icon())
                    .name(Mini.label(current
                            ? Palette.SUCCESS + "<b>" + animation.displayName() + "</b>"
                            : Palette.heading(animation.displayName())))
                    .loreComponents(Mini.labels(Lore.create()
                            .blank()
                            .text(animation.description())
                            .blank()
                            .entry(Tr.t("Cadence"), animation.instant()
                                    ? Tr.t("instantanée") : animation.ticksPerReveal() + Tr.t(" ticks par gain"))
                            .state(Tr.t("Sélectionnée"), current, Tr.t("oui"), Tr.t("non"))
                            .blank()
                            .action(current ? Tr.t("Déjà active") : Tr.t("Cliquer pour appliquer"))
                            .build()))
                    .glow(current)
                    .asGuiItem(event -> {
                        Player viewer = (Player) event.getWhoClicked();
                        editor.setBulkAnimation(crate.id(), animation);
                        Guis.success(viewer);
                        if (back != null) {
                            back.run();
                        }
                    }));
        }

        Guis.controls(gui);
        gui.open(player);
        Guis.opened(start);
    }

    public void open(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();

        Gui gui = Gui.builder()
                .rows(3)
                .title(Mini.parse(Palette.title(Tr.t("Animation"))))
                .create();
        Guis.fill(gui);

        CrateAnimationType[] types = CrateAnimationType.values();
        int first = 5 - (types.length - 1) / 2 - (types.length % 2 == 0 ? 1 : 0);
        for (int index = 0; index < types.length; index++) {
            int column = Math.max(1, Math.min(9, first + index));
            gui.setItem(2, column, icon(crate, types[index], back));
        }

        gui.setItem(3, 1, Guis.backButton(back));
        gui.setItem(3, 9, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem icon(Crate crate, CrateAnimationType type, Runnable back) {
        boolean selected = crate.animation() == type;

        Lore lore = Lore.create().blank();
        for (String line : type.description()) {
            lore.text(line);
        }
        lore.blank().action(selected ? Tr.t("Animation actuelle") : Tr.t("Cliquer pour choisir"));

        return ItemBuilder.of(type.icon())
                .name(Mini.label(selected
                        ? Palette.heading(type.displayName())
                        : Palette.TEXT + type.displayName()))
                .loreComponents(Mini.labels(lore.build()))
                .glow(selected)
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    editor.setAnimation(crate.id(), type);
                    Guis.success(player);
                    back.run();
                });
    }
}
