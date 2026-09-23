package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrateRewardMenu {

    private static final int MAX_PER_ROW = 7;

    private final CrateService service;

    private CrateOpener opener;

    public CrateRewardMenu(CrateService service) {
        this.service = service;
    }

    public void bind(CrateOpener opener) {
        this.opener = opener;
    }

    public void open(Player player, CrateService.Session session, Runnable onFinish) {
        long start = System.nanoTime();
        Crate crate = session.crate();
        List<CrateService.Grant> grants = session.grants();

        Gui gui = Gui.builder()
                .rows(3)
                .title(Mini.parse(Palette.title(Tr.t("Butin"))))
                .create();
        Guis.fill(gui);

        int columns = Math.min(MAX_PER_ROW, Math.max(1, grants.size()));
        int first = 5 - (columns - 1) / 2 - (columns % 2 == 0 ? 1 : 0);
        for (int index = 0; index < columns; index++) {
            CrateService.Grant grant = grants.get(index);
            gui.setItem(2, Math.max(1, Math.min(9, first + index)),
                    ItemBuilder.of(CrateIcons.winner(grant.reward(), grant.amount()))
                            .asGuiItem(event -> event.setCancelled(true)));
        }

        gui.setItem(1, 5, summary(crate, session));
        gui.setItem(3, 1, reopenButton(player, crate, onFinish));
        gui.setItem(3, 5, Guis.button(Material.BOOK, Palette.heading(Tr.t("Voir les récompenses")),
                Lore.create()
                        .blank()
                        .text(Tr.t("Toutes les récompenses possibles"))
                        .text(Tr.t("de cette caisse et leurs chances."))
                        .blank()
                        .action(Tr.t("Cliquer pour ouvrir l'aperçu"))
                        .build(),
                viewer -> {
                    if (opener != null) {
                        opener.preview(viewer, crate, onFinish);
                    }
                }));
        gui.setItem(3, 9, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    public void openBulk(Player player, Crate crate, CrateService.Bulk bulk, Runnable onFinish) {
        long start = System.nanoTime();
        Map<CrateReward, Integer> totals = new LinkedHashMap<>();
        for (CrateService.Grant granted : bulk.grants()) {
            totals.merge(granted.reward(), granted.amount(), Integer::sum);
        }
        List<Map.Entry<CrateReward, Integer>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort(Comparator.comparingInt(
                (Map.Entry<CrateReward, Integer> entry) -> entry.getKey().rarity().tier()).reversed());

        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Butin groupé"))))
                .create();
        Guis.paginationBar(gui, onFinish);
        gui.setItem(gui.getRows(), 4, bulkSummary(crate, bulk, totals.size()));
        gui.setItem(gui.getRows(), 6, reopenButton(player, crate, onFinish));

        DeferredPage<Map.Entry<CrateReward, Integer>> page = Guis.deferred(gui, sorted, 45,
                entry -> ItemBuilder.of(CrateIcons.winner(entry.getKey(), entry.getValue()))
                        .asGuiItem(event -> event.setCancelled(true)));

        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem bulkSummary(Crate crate, CrateService.Bulk bulk, int distinct) {
        CrateRarity best = bulk.best();
        return Guis.display(Material.CHEST, Palette.heading(Tr.t("Ouverture groupée")), Lore.create()
                .blank()
                .entry(Tr.t("Caisse"), crate.displayName())
                .count(Tr.t("Caisses ouvertes"), bulk.opened())
                .count(Tr.t("Récompenses obtenues"), bulk.grants().size())
                .count(Tr.t("Objets distincts"), distinct)
                .entry(Tr.t("Meilleur tirage"), best.colored(best.displayName()))
                .blank()
                .text(Tr.t("Les gains sont déjà dans votre inventaire."))
                .build());
    }

    private GuiItem summary(Crate crate, CrateService.Session session) {
        Lore lore = Lore.create()
                .blank()
                .entry(Tr.t("Caisse"), crate.displayName())
                .count(Tr.t("Récompenses obtenues"), session.grants().size());
        CrateReward best = session.draw().best();
        if (best != null) {
            lore.entry(Tr.t("Meilleur tirage"),
                    best.rarity().colored(best.rarity().displayName()));
        }
        if (session.pity()) {
            lore.blank().hint(Tr.t("Tirage garanti par la pitié"));
        }
        lore.blank().text(Tr.t("Les gains sont déjà dans votre inventaire."));
        return Guis.display(Material.CHEST, Palette.heading(Tr.t("Ouverture terminée")), lore.build());
    }

    private GuiItem reopenButton(Player player, Crate crate, Runnable onFinish) {
        int remaining = service.totalKeys(player, crate);
        if (remaining <= 0 || opener == null) {
            return Guis.display(Material.GRAY_DYE, Palette.MUTED + Tr.t("<b>Plus de clé</b>"),
                    Lore.create()
                            .blank()
                            .text(Tr.t("Procurez-vous une clé pour"))
                            .text(Tr.t("ouvrir cette caisse à nouveau."))
                            .build());
        }
        return Guis.button(Material.TRIPWIRE_HOOK, Palette.heading(Tr.t("Rouvrir")),
                Lore.create()
                        .blank()
                        .count(Tr.t("Clés restantes"), remaining)
                        .blank()
                        .action(Tr.t("Cliquer pour relancer"))
                        .build(),
                viewer -> opener.open(viewer, crate, onFinish));
    }
}
