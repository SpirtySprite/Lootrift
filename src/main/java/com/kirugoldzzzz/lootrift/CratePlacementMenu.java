package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ConfirmMenu;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.util.Locations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CratePlacementMenu {

    private static final Comparator<CratePlacement> ORDER = Comparator
            .comparing(CratePlacement::crate)
            .thenComparing(CratePlacement::world)
            .thenComparingInt(CratePlacement::x)
            .thenComparingInt(CratePlacement::y)
            .thenComparingInt(CratePlacement::z);

    private final CrateService service;
    private final CrateHolograms holograms;

    private final CrateModels models;

    public CratePlacementMenu(CrateService service, CrateHolograms holograms, CrateModels models) {
        this.service = service;
        this.holograms = holograms;
        this.models = models;
    }

    public void open(Player player, Runnable back) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title("Caisses posées")))
                .create();

        Guis.paginationBar(gui, back);
        gui.setItem(6, 4, summary());
        gui.setItem(6, 6, refreshButton(back));

        List<CratePlacement> placements = new ArrayList<>(service.placementRepository().all());
        placements.sort(ORDER);
        if (placements.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.MUTED + "<b>Aucune caisse posée</b>", Lore.create()
                            .blank()
                            .text("Un administrateur pose une caisse")
                            .text("en plaçant son bloc dans le monde.")
                            .blank()
                            .hint("Le bloc s'obtient avec /crate give")
                            .build()));
        }
        DeferredPage<CratePlacement> page = Guis.deferred(gui, placements, 45,
                placement -> icon(placement, back));

        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem summary() {
        return Guis.display(Material.COMPASS, Palette.heading("Vue d'ensemble"), Lore.create()
                .blank()
                .count("Emplacements", service.placementRepository().count())
                .count("Hologrammes actifs", holograms.active())
                .build());
    }

    private GuiItem refreshButton(Runnable back) {
        return Guis.button(Material.ITEM_FRAME, Palette.heading("Rafraîchir les hologrammes"), Lore.create()
                .blank()
                .text("Repose les hologrammes manquants")
                .text("et retire ceux qui n'ont plus de caisse.")
                .blank()
                .action("Cliquer pour rafraîchir")
                .build(), player -> {
            holograms.refreshAll();
            Guis.success(player);
            open(player, back);
        });
    }

    private GuiItem icon(CratePlacement placement, Runnable back) {
        Crate crate = service.crate(placement.crate()).orElse(null);
        return ItemBuilder.of(crate == null ? Material.BARRIER : crate.block())
                .name(Mini.label(crate == null
                        ? Palette.DANGER + "<b>Caisse inconnue</b>"
                        : Palette.heading(crate.displayName())))
                .loreComponents(Mini.labels(Lore.create()
                        .blank()
                        .highlight("Monde", placement.world())
                        .entry("Position", placement.coordinates())
                        .state("Hologramme", placement.hasHologram(), "actif", "aucun")
                        .entry("Orientation", placement.facing() + " ("
                                + Math.round(placement.yaw()) + "°)")
                        .blank()
                        .click("Clic gauche", "se téléporter")
                        .click("Clic droit", "pivoter de 45°")
                        .denyClick("Shift + clic droit", "retirer l'emplacement")
                        .build()))
                .asGuiItem(event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click.isShiftClick() && click.isRightClick()) {
                        confirmRemove(viewer, placement, back);
                        return;
                    }
                    if (click.isRightClick()) {
                        rotate(viewer, placement, back);
                        return;
                    }
                    teleport(viewer, placement);
                });
    }

    private void rotate(Player player, CratePlacement placement, Runnable back) {
        Crate crate = service.crate(placement.crate()).orElse(null);
        if (crate == null) {
            Guis.deny(player);
            return;
        }
        CratePlacement turned = placement.rotated();
        service.placementRepository().save(turned);
        models.rotate(turned, crate);
        Guis.click(player);
        open(player, back);
    }

    private void teleport(Player player, CratePlacement placement) {
        Location location = placement.location().orElse(null);
        if (location == null) {
            Guis.deny(player);
            player.sendMessage(Mini.label("<muted>" + Palette.ARROW
                            + " Le monde <text><world> <muted>n'est pas chargé.",
                    Mini.value("world", placement.world())));
            return;
        }
        Guis.click(player);
        player.closeInventory();
        player.teleportAsync(Locations.centered(location).add(0, 1, 0));
    }

    private void confirmRemove(Player player, CratePlacement placement, Runnable back) {
        Guis.click(player);
        Crate crate = service.crate(placement.crate()).orElse(null);
        ConfirmMenu.create("Retirer un emplacement")
                .subject(crate == null ? Material.BARRIER : crate.block())
                .confirmLabel("Retirer")
                .question("Retirer cet emplacement ?")
                .details(Lore.create()
                        .highlight("Caisse", placement.crate())
                        .entry("Position", placement.describe())
                        .blank()
                        .warn("Le bloc reste en place dans le monde")
                        .build())
                .onConfirm(viewer -> {
                    holograms.remove(placement);
                    models.remove(placement);
                    service.placementRepository().remove(placement.id());
                    Guis.success(viewer);
                    Messages.send(viewer, "crates.removed");
                    open(viewer, back);
                })
                .onCancel(viewer -> open(viewer, back))
                .open(player);
    }
}
