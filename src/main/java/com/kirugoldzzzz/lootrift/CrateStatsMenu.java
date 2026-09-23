package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CrateStatsMenu {

    private static final int TOP_LIMIT = 10;
    private static final int PULL_LIMIT = 10;

    private final CrateService service;

    public CrateStatsMenu(CrateService service) {
        this.service = service;
    }

    public void open(Player player, Crate crate, Runnable back) {
        Scheduling.async(() -> {
            Map<String, Integer> counts = service.historyRepository().countsByReward(crate.id());
            List<Map.Entry<String, Integer>> openers =
                    service.historyRepository().topWinners(crate.id(), TOP_LIMIT);
            List<CratePull> best = service.historyRepository().bestPulls(crate.id(), PULL_LIMIT);
            Scheduling.entity(player, () -> render(player, crate, counts, openers, best, back));
        });
    }

    private void render(Player player, Crate crate, Map<String, Integer> counts,
                        List<Map.Entry<String, Integer>> openers, List<CratePull> best,
                        Runnable back) {
        long start = System.nanoTime();
        int drawn = 0;
        for (int value : counts.values()) {
            drawn += value;
        }

        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Statistiques"))))
                .create();

        Guis.paginationBar(gui, back);
        gui.setItem(gui.getRows(), 2, overview(crate, counts.size(), drawn));
        gui.setItem(gui.getRows(), 4, openersIcon(openers));
        gui.setItem(gui.getRows(), 6, bestIcon(best));
        gui.setItem(gui.getRows(), 8, driftIcon(crate, counts, drawn));

        List<CrateReward> sorted = new ArrayList<>(crate.rewards());
        sorted.sort(Comparator
                .comparingInt((CrateReward reward) -> reward.rarity().tier()).reversed()
                .thenComparing(CrateReward::id));

        if (sorted.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.MUTED + Tr.t("<b>Aucune récompense</b>"), Lore.create()
                            .blank()
                            .text(Tr.t("Rien à mesurer sur cette caisse."))
                            .build()));
        }
        int totalDrawn = drawn;
        DeferredPage<CrateReward> page = Guis.deferred(gui, sorted, 45,
                reward -> entry(crate, reward, counts.getOrDefault(reward.id(), 0), totalDrawn));

        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem entry(Crate crate, CrateReward reward, int won, int drawn) {
        double theory = crate.chanceOf(reward);
        double actual = drawn == 0 ? 0.0D : won * 100.0D / drawn;
        double gap = actual - theory;
        Lore lore = Lore.create()
                .blank()
                .entry(Tr.t("Rareté"), reward.rarity().colored(reward.rarity().displayName()))
                .highlight(Tr.t("Chance annoncée"), CrateIcons.chance(theory))
                .entry(Tr.t("Chance observée"), drawn == 0 ? Tr.t("aucun tirage") : CrateIcons.chance(actual))
                .count(Tr.t("Fois obtenue"), won)
                .blank();
        if (drawn == 0) {
            lore.text(Tr.t("Pas encore assez de données."));
        } else if (Math.abs(gap) < 1.0D) {
            lore.action(Tr.t("Conforme à l'annonce"));
        } else if (gap > 0.0D) {
            lore.hint("Sortie " + CrateIcons.chance(gap) + Tr.t(" plus souvent que prévu"));
        } else {
            lore.warn("Sortie " + CrateIcons.chance(-gap) + Tr.t(" moins souvent que prévu"));
        }
        return ItemBuilder.of(CrateIcons.renamed(reward.display(),
                        reward.rarity().heading(reward.id()), lore.build()))
                .asGuiItem(event -> event.setCancelled(true));
    }

    private GuiItem overview(Crate crate, int distinct, int drawn) {
        return Guis.display(Material.KNOWLEDGE_BOOK, Palette.heading(crate.displayName()),
                Lore.create()
                        .blank()
                        .highlight(Tr.t("Identifiant"), crate.id())
                        .count(Tr.t("Récompenses configurées"), crate.rewards().size())
                        .count(Tr.t("Récompenses déjà sorties"), distinct)
                        .count(Tr.t("Tirages enregistrés"), drawn)
                        .count(Tr.t("Ouvertures comptées"), service.keyRepository().totalOpened(crate.id()))
                        .blank()
                        .text(Tr.t("Les mesures viennent de l'historique,"))
                        .text(Tr.t("purgé selon la rétention configurée."))
                        .build());
    }

    private GuiItem openersIcon(List<Map.Entry<String, Integer>> openers) {
        Lore lore = Lore.create().blank();
        if (openers.isEmpty()) {
            lore.text(Tr.t("Personne n'a encore ouvert cette caisse."));
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : openers) {
                lore.entry(rank++ + ". " + entry.getKey(), entry.getValue() + " tirages");
            }
        }
        return Guis.display(Material.PLAYER_HEAD, Palette.heading(Tr.t("Meilleurs ouvreurs")),
                lore.build());
    }

    private GuiItem bestIcon(List<CratePull> best) {
        Lore lore = Lore.create().blank();
        if (best.isEmpty()) {
            lore.text(Tr.t("Aucun gain légendaire ou mythique."));
        } else {
            for (CratePull pull : best) {
                lore.entry(pull.rarity().colored(pull.ownerName()), pull.rewardName());
            }
        }
        return Guis.display(Material.NETHER_STAR, Palette.heading(Tr.t("Plus beaux gains")),
                lore.build());
    }

    private GuiItem driftIcon(Crate crate, Map<String, Integer> counts, int drawn) {
        Lore lore = Lore.create()
                .blank()
                .text(Tr.t("Écart entre la chance annoncée"))
                .text(Tr.t("et la chance réellement observée."))
                .blank();
        if (drawn < 100) {
            lore.warn(Tr.t("Moins de cent tirages, un écart"));
            lore.warn(Tr.t("important reste normal."));
            return Guis.display(Material.COMPARATOR, Palette.heading(Tr.t("Fiabilité")), lore.build());
        }
        double worst = 0.0D;
        String culprit = null;
        for (CrateReward reward : crate.rewards()) {
            int won = counts.getOrDefault(reward.id(), 0);
            double gap = Math.abs(won * 100.0D / drawn - crate.chanceOf(reward));
            if (gap > worst) {
                worst = gap;
                culprit = reward.id();
            }
        }
        lore.count(Tr.t("Tirages mesurés"), drawn);
        if (culprit == null) {
            lore.action(Tr.t("Aucun écart notable"));
        } else {
            lore.highlight(Tr.t("Plus gros écart"), culprit);
            lore.entry(Tr.t("Amplitude"), CrateIcons.chance(worst));
            if (worst < 2.0D) {
                lore.action(Tr.t("Distribution saine"));
            } else {
                lore.hint(Tr.t("Écart possible sur un faible volume"));
            }
        }
        return Guis.display(Material.COMPARATOR, Palette.heading(Tr.t("Fiabilité")), lore.build());
    }
}
