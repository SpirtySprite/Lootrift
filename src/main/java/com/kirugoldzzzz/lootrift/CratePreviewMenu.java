package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CratePreviewMenu {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 28;
    private static final int SUMMARY_COLUMN = 5;
    private static final int BACK_COLUMN = 1;
    private static final int DAILY_COLUMN = 2;
    private static final int PREVIOUS_COLUMN = 3;
    private static final int OPEN_COLUMN = 5;
    private static final int NEXT_COLUMN = 7;
    private static final int BUY_COLUMN = 8;
    private static final int CLOSE_COLUMN = 9;

    private final CrateService service;
    private final CrateOpener opener;

    public CratePreviewMenu(CrateService service, CrateOpener opener) {
        this.service = service;
        this.opener = opener;
    }

    public void open(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        CrateRarity theme = theme(crate);
        PaginatedGui gui = PaginatedGui.builder()
                .rows(ROWS)
                .title(Mini.parse(theme.title(Card.small("Aperçu")) + Palette.MUTED + " » " + crate.displayName()))
                .create();

        frame(gui, theme);
        gui.setItem(1, SUMMARY_COLUMN, summary(player, crate, theme));
        if (back != null) {
            gui.setItem(ROWS, BACK_COLUMN, Guis.backButton(back));
        }
        if (crate.dailyKey()) {
            gui.setItem(ROWS, DAILY_COLUMN, dailyButton(player, crate, back));
        }
        gui.setItem(ROWS, OPEN_COLUMN, openButton(player, crate, back));
        if (crate.purchasable()) {
            gui.setItem(ROWS, BUY_COLUMN, buyButton(player, crate, back));
        }
        gui.setItem(ROWS, CLOSE_COLUMN, Guis.closeButton());

        List<CrateReward> sorted = new ArrayList<>(crate.rewards());
        sorted.sort(Comparator
                .comparingInt((CrateReward reward) -> reward.rarity().tier()).reversed()
                .thenComparingInt(CrateReward::weight)
                .thenComparing(CrateReward::id));

        if (sorted.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.DANGER + "<b>" + Card.small("Caisse vide") + "</b>", Card.of(Palette.ERROR_HEX)
                            .tag("Aperçu")
                            .blank()
                            .line("Aucune récompense n'est")
                            .line("encore configurée ici.")
                            .build()));
        }
        DeferredPage<CrateReward> page = Guis.deferred(gui, sorted, PAGE_SIZE, reward -> {
            boolean unlocked = !reward.restricted() || player.hasPermission(reward.permission());
            return ItemBuilder.of(CrateIcons.preview(crate, reward, unlocked))
                    .asGuiItem(event -> event.setCancelled(true));
        });

        Guis.arrows(gui, page, () -> {
            frameArrows(gui, theme);
            gui.update();
        });
        frameArrows(gui, theme);
        gui.open(player);
        Guis.opened(start);
    }

    static CrateRarity theme(Crate crate) {
        if (crate.rewards().isEmpty()) {
            return CrateRarity.RARE;
        }
        CrateRarity top = CrateRarity.COMMUN;
        for (CrateReward reward : crate.rewards()) {
            if (reward.rarity().atLeast(top)) {
                top = reward.rarity();
            }
        }
        return top;
    }

    static Material framePane(CrateRarity theme, int row, int column) {
        boolean edge = row == 1 || row == ROWS;
        boolean accent = edge ? column % 2 == 1 : row == ROWS / 2 || row == ROWS / 2 + 1;
        return accent ? theme.pane() : Material.BLACK_STAINED_GLASS_PANE;
    }

    private static GuiItem pane(CrateRarity theme, int row, int column) {
        return Guis.display(framePane(theme, row, column), " ", List.of());
    }

    private static void frame(PaginatedGui gui, CrateRarity theme) {
        for (int column = 1; column <= 9; column++) {
            gui.setItem(1, column, pane(theme, 1, column));
            gui.setItem(ROWS, column, pane(theme, ROWS, column));
        }
        for (int row = 2; row < ROWS; row++) {
            gui.setItem(row, 1, pane(theme, row, 1));
            gui.setItem(row, 9, pane(theme, row, 9));
        }
    }

    private static void frameArrows(PaginatedGui gui, CrateRarity theme) {
        if (!gui.hasPrevious()) {
            gui.setItem(ROWS, PREVIOUS_COLUMN, pane(theme, ROWS, PREVIOUS_COLUMN));
        }
        if (!gui.hasNext()) {
            gui.setItem(ROWS, NEXT_COLUMN, pane(theme, ROWS, NEXT_COLUMN));
        }
    }

    private GuiItem summary(Player player, Crate crate, CrateRarity theme) {
        int opened = service.keyRepository().opened(player.getUniqueId(), crate.id());
        Card card = Card.of(theme.hex())
                .tag("Caisse")
                .section("Contenu")
                .count(Card.AMOUNT, "Récompenses", crate.rewards().size())
                .stat(Card.STAR, "Animation", crate.animation().displayName());
        if (crate.rolls() > 1) {
            card.count(Card.CHANCE, "Tirages par ouverture", crate.rolls());
        }
        card.count(Card.PLAYER, "Vos ouvertures", opened);

        card.section("Raretés");
        for (CrateRarity rarity : CrateRarity.values()) {
            int count = crate.countOf(rarity);
            if (count > 0) {
                card.stat(rarity.color(), rarity.icon(), rarity.displayName(),
                        count + Palette.MUTED + " · " + rarity.color() + CrateIcons.chance(rarityChance(crate, rarity)));
            }
        }

        if (crate.pityEnabled()) {
            int streak = Math.min(service.keyRepository().streak(player.getUniqueId(), crate.id()), crate.pityAfter());
            int left = crate.pityAfter() - streak + 1;
            CrateRarity floor = crate.pityFloor();
            card.section("Garantie " + floor.displayName())
                    .progress(streak, crate.pityAfter())
                    .line(left <= 1
                            ? floor.color() + "<b>" + Card.small("Garantie à la prochaine ouverture") + "</b>"
                            : "Garantie dans " + floor.color() + left + Palette.TEXT + " ouvertures");
        }
        if (!crate.milestones().isEmpty()) {
            card.section("Paliers");
            for (CrateMilestone milestone : crate.milestones()) {
                int target = milestone.repeating()
                        ? milestone.opens() - opened % milestone.opens()
                        : milestone.opens() - opened;
                if (!milestone.repeating() && target <= 0) {
                    card.stat(Palette.SUCCESS, Palette.CHECK, milestone.label(), "atteint");
                } else {
                    card.stat(Card.FLAG, milestone.label(), "dans " + Math.max(1, target) + " ouvertures");
                }
            }
        }
        return ItemBuilder.of(CrateIcons.renamed(crate.icon(), Palette.heading(crate.displayName()), card.build()))
                .glow(true)
                .asGuiItem(event -> event.setCancelled(true));
    }

    static double rarityChance(Crate crate, CrateRarity rarity) {
        double total = 0.0D;
        for (CrateReward reward : crate.rewards()) {
            if (reward.rarity() == rarity) {
                total += crate.chanceOf(reward);
            }
        }
        return total;
    }

    private static String heading(String text) {
        return Card.title(Palette.PRIMARY_HEX, Palette.SECONDARY_HEX, Card.small(text));
    }

    private GuiItem dailyButton(Player player, Crate crate, Runnable back) {
        long waiting = service.dailyRemaining(player, crate);
        if (waiting > 0L) {
            return Guis.display(Material.GRAY_DYE, Palette.MUTED + "<b>" + Card.small("Clé du jour prise") + "</b>",
                    Card.of(Palette.MUTED_HEX)
                            .tag("Clé du jour")
                            .blank()
                            .waiting("Prochaine clé dans " + Palette.WARNING + Numbers.duration(waiting))
                            .build());
        }
        return Guis.glowing(Material.SUNFLOWER, heading("Clé du jour"), Card.of(Palette.WARNING_HEX)
                .tag("Cadeau")
                .section("Description")
                .line("Une clé offerte toutes")
                .line("les vingt-quatre heures.")
                .blank()
                .click("pour réclamer votre clé")
                .build(), true, viewer -> {
            if (service.claimDaily(viewer, crate)) {
                Guis.success(viewer);
            } else {
                Guis.deny(viewer);
            }
            open(viewer, crate, back);
        });
    }

    private GuiItem buyButton(Player player, Crate crate, Runnable back) {
        double balance = service.economy().balance(player.getUniqueId());
        int affordable = crate.price() <= 0.0D ? 0 : (int) Math.floor(balance / crate.price());
        Card card = Card.of(Palette.MONEY_HEX)
                .tag("Boutique de clés")
                .section("Prix")
                .money("Prix par clé", crate.price())
                .money("Votre solde", balance)
                .count(Card.DONE, "Clés abordables", affordable)
                .blank();
        if (affordable <= 0) {
            card.deny("Solde insuffisant");
            return Guis.display(Material.GRAY_DYE, Palette.MUTED + "<b>" + Card.small("Acheter une clé") + "</b>",
                    card.build());
        }
        card.click("Clic gauche", "pour acheter une clé")
                .click("Maj + clic", "pour en acheter dix");
        return Guis.item(Material.GOLD_INGOT,
                heading("Acheter une clé"),
                card.build(),
                event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    if (!service.economy().playerOperationsAllowed()) {
                        Guis.deny(viewer);
                        Messages.send(viewer, "economy.maintenance");
                        return;
                    }
                    int wanted = event.getClick().isShiftClick() ? 10 : 1;
                    if (service.buyKey(viewer, crate, Math.min(wanted, affordable))) {
                        Guis.money(viewer);
                    } else {
                        Guis.deny(viewer);
                        Messages.send(viewer, "crates.cannot-afford",
                                Mini.styled("crate", crate.displayName()));
                    }
                    open(viewer, crate, back);
                });
    }

    private GuiItem openButton(Player player, Crate crate, Runnable back) {
        int keys = service.totalKeys(player, crate);
        if (keys <= 0) {
            return Guis.display(Material.GRAY_DYE, Palette.MUTED + "<b>" + Card.small("Aucune clé") + "</b>",
                    Card.of(Palette.ERROR_HEX)
                            .tag("Ouverture")
                            .section("Description")
                            .line("Il vous faut une clé pour")
                            .line("ouvrir cette caisse.")
                            .blank()
                            .deny("Aucune clé disponible")
                            .build());
        }
        long waiting = service.cooldownRemaining(player, crate);
        Card card = Card.of(Palette.SUCCESS_HEX)
                .tag("Ouverture")
                .section("Vos clés")
                .count(Card.DONE, "Clés disponibles", keys)
                .blank();
        if (waiting > 0L) {
            card.waiting("Disponible dans " + Palette.WARNING + Numbers.duration(waiting));
        } else {
            card.click("Clic gauche", "pour ouvrir une caisse")
                    .click("Maj + clic", "pour en ouvrir jusqu'à "
                            + Math.min(keys, CrateService.BULK_LIMIT));
        }
        return Guis.item(Material.TRIPWIRE_HOOK,
                heading("Ouvrir maintenant"),
                card.build(),
                waiting <= 0L,
                event -> {
                    Player viewer = (Player) event.getWhoClicked();
                    if (event.getClick().isShiftClick()) {
                        opener.openBulk(viewer, crate, CrateService.BULK_LIMIT, back);
                        return;
                    }
                    opener.open(viewer, crate, back);
                });
    }
}
