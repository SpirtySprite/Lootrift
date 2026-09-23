package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class CrateHistoryMenu {

    private static final int LIMIT = 90;
    private static final int PAGE_SIZE = 45;

    private final CrateService service;

    public CrateHistoryMenu(CrateService service) {
        this.service = service;
    }

    public void open(Player player, String crateFilter, Runnable back) {
        openFor(player, player.getUniqueId(), player.getName(), crateFilter, back);
    }

    public void openFor(Player viewer, OfflinePlayer target, Runnable back) {
        String name = target.getName() == null ? "Inconnu" : target.getName();
        openFor(viewer, target.getUniqueId(), name, null, back);
    }

    private void openFor(Player viewer, UUID owner, String ownerName, String crateFilter,
                         Runnable back) {
        Scheduling.async(() -> {
            List<CratePull> pulls = service.historyRepository().page(owner, crateFilter, LIMIT, 0);
            Scheduling.entity(viewer, () -> render(viewer, ownerName, pulls, back));
        });
    }

    private void render(Player viewer, String ownerName, List<CratePull> pulls, Runnable back) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title("Historique")))
                .create();

        Guis.paginationBar(gui, back);
        gui.setItem(gui.getRows(), 5, Guis.display(Material.PLAYER_HEAD,
                Palette.heading(ownerName), Lore.create()
                        .blank()
                        .count("Tirages affichés", pulls.size())
                        .text("Les " + LIMIT + " plus récents.")
                        .build()));

        if (pulls.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.MUTED + "<b>Aucun tirage</b>", Lore.create()
                            .blank()
                            .text("Aucune caisse n'a encore été")
                            .text("ouverte par ce joueur.")
                            .build()));
        }
        DeferredPage<CratePull> page = Guis.deferred(gui, pulls, PAGE_SIZE, pull -> {
            String crateName = service.crate(pull.crate())
                    .map(Crate::displayName)
                    .orElse(pull.crate());
            return ItemBuilder.of(CrateIcons.pull(pull, crateName))
                    .asGuiItem(event -> event.setCancelled(true));
        });
        Guis.controls(gui, page);
        gui.open(viewer);
        Guis.opened(start);
    }
}
