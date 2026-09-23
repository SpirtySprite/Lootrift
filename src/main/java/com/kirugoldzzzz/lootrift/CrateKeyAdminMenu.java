package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ChatPrompts;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.item.ItemReturn;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CrateKeyAdminMenu {

    private static final int TOP_OPENERS = 5;
    private static final int MAX_AMOUNT = 10_000;

    private final CrateService service;
    private final Wallet economy;

    public CrateKeyAdminMenu(CrateService service, Wallet economy) {
        this.service = service;
        this.economy = economy;
    }

    public void open(Player player, Runnable back) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(5)
                .title(Mini.parse(Palette.title(Tr.t("Distribution de clés"))))
                .create();

        Guis.paginationBar(gui, back);

        List<Crate> crates = service.crates();
        if (crates.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.DANGER + Tr.t("<b>Aucune caisse</b>"), Lore.create()
                            .blank()
                            .text(Tr.t("Créez d'abord une caisse dans"))
                            .text(Tr.t("l'éditeur."))
                            .build()));
        }
        DeferredPage<Crate> page = Guis.deferred(gui, crates, 36, crate -> crateEntry(crate, back));
        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem crateEntry(Crate crate, Runnable back) {
        int circulation = service.keyRepository().circulation(crate.id());
        int opened = service.keyRepository().totalOpened(crate.id());
        return ItemBuilder.of(CrateIcons.renamed(crate.icon(),
                        Palette.heading(crate.displayName()), Lore.create()
                                .blank()
                                .highlight(Tr.t("Identifiant"), crate.id())
                                .count(Tr.t("Clés virtuelles en circulation"), circulation)
                                .count(Tr.t("Ouvertures enregistrées"), opened)
                                .blank()
                                .action(Tr.t("Cliquer pour distribuer"))
                                .build()))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    Guis.click(player);
                    openCrate(player, crate, back);
                });
    }

    public void openCrate(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(5)
                .title(Mini.parse(Palette.title(Tr.t("Clés"))))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, statsIcon(crate));

        gui.setItem(3, 2, giveOneButton(crate, back));
        gui.setItem(3, 4, giveAllButton(crate, back));
        gui.setItem(3, 6, selfPhysicalButton(crate, back));
        gui.setItem(3, 8, selfBlockButton(crate, back));

        gui.setItem(5, Guis.BACK_SLOT, Guis.backButton(() -> open(player, back)));
        gui.setItem(5, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem statsIcon(Crate crate) {
        Lore lore = Lore.create()
                .blank()
                .highlight(Tr.t("Identifiant"), crate.id())
                .count(Tr.t("Clés virtuelles en circulation"), service.keyRepository().circulation(crate.id()))
                .count(Tr.t("Ouvertures enregistrées"), service.keyRepository().totalOpened(crate.id()))
                .count(Tr.t("Caisses posées"), service.placementRepository().count(crate.id()));

        List<Map.Entry<UUID, Integer>> top = service.keyRepository()
                .topOpeners(crate.id(), TOP_OPENERS);
        if (!top.isEmpty()) {
            lore.blank().text(Tr.t("Meilleurs ouvreurs"));
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                lore.entry(rank++ + ". " + economy.nameOf(entry.getKey()), entry.getValue());
            }
        }
        return Guis.display(Material.TRIPWIRE_HOOK, Palette.heading(crate.displayName()),
                lore.build());
    }

    private GuiItem giveOneButton(Crate crate, Runnable back) {
        return Guis.button(Material.PLAYER_HEAD, Palette.heading(Tr.t("Donner à un joueur")),
                Lore.create()
                        .blank()
                        .text(Tr.t("Crédite des clés virtuelles à un"))
                        .text(Tr.t("joueur, même hors ligne."))
                        .blank()
                        .action(Tr.t("Cliquer pour saisir un pseudo"))
                        .build(),
                player -> promptName(player, crate, back));
    }

    private void promptName(Player player, Crate crate, Runnable back) {
        ChatPrompts.open(player, Tr.t("le pseudo du joueur"), input -> {
            String name = input == null ? "" : input.trim();
            if (name.isEmpty()) {
                Guis.deny(player);
                openCrate(player, crate, back);
                return;
            }
            Optional<UUID> target = economy.resolve(name);
            if (target.isEmpty()) {
                Guis.deny(player);
                Messages.send(player, "general.unknown-player", Mini.value("player", name));
                openCrate(player, crate, back);
                return;
            }
            promptAmount(player, crate, Tr.t("Cles pour ") + economy.nameOf(target.get()),
                    amount -> grant(player, crate, target.get(), amount, back), back);
        });
    }

    private GuiItem giveAllButton(Crate crate, Runnable back) {
        int online = Bukkit.getOnlinePlayers().size();
        return Guis.button(Material.BEACON, Palette.heading(Tr.t("Donner à tous")),
                Lore.create()
                        .blank()
                        .count(Tr.t("Joueurs connectés"), online)
                        .blank()
                        .text(Tr.t("Crédite la même quantité de clés"))
                        .text(Tr.t("virtuelles à tous les connectés."))
                        .blank()
                        .action(Tr.t("Cliquer pour choisir la quantité"))
                        .build(),
                player -> promptAmount(player, crate, Tr.t("Clés pour tous"),
                        amount -> grantAll(player, crate, amount, back), back));
    }

    private GuiItem selfPhysicalButton(Crate crate, Runnable back) {
        return Guis.button(Material.CHEST_MINECART, Palette.heading(Tr.t("Clés physiques")),
                Lore.create()
                        .blank()
                        .text(Tr.t("Vous remet des clés physiques,"))
                        .text(Tr.t("échangeables entre joueurs."))
                        .blank()
                        .warn(Tr.t("Le surplus est mis de côté si"))
                        .warn(Tr.t("votre inventaire est plein."))
                        .blank()
                        .action(Tr.t("Cliquer pour choisir la quantité"))
                        .build(),
                player -> promptAmount(player, crate, Tr.t("Clés physiques"),
                        amount -> {
                            service.givePhysicalKeys(player, crate, amount);
                            Guis.success(player);
                            CrateLog.player(CrateLog.KEY_PHYSICAL, player, crate, amount,
                                    amount + Tr.t(" clés physiques créées"));
                            Messages.send(player, "crates.keys-given",
                                    Mini.value("amount", String.valueOf(amount)),
                                    Mini.value("player", player.getName()),
                                    Mini.styled("crate", crate.displayName()));
                            openCrate(player, crate, back);
                        }, back));
    }

    private GuiItem selfBlockButton(Crate crate, Runnable back) {
        return Guis.button(Material.GRASS_BLOCK, Palette.heading(Tr.t("Bloc de caisse")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Bloc"), crate.block().name())
                        .blank()
                        .text(Tr.t("Vous remet le bloc à poser pour"))
                        .text(Tr.t("matérialiser cette caisse."))
                        .blank()
                        .action(Tr.t("Cliquer pour en recevoir un"))
                        .build(),
                player -> {
                    ItemReturn.give(player, List.of(CrateKeys.blockItem(crate, 1)));
                    Guis.success(player);
                    Messages.send(player, "crates.block-given",
                            Mini.styled("crate", crate.displayName()));
                    openCrate(player, crate, back);
                });
    }

    private void promptAmount(Player player, Crate crate, String title,
                              java.util.function.IntConsumer onAmount, Runnable back) {
        ChatPrompts.open(player, title, input -> {
            int amount = Numbers.parseCount(input).orElse(-1);
            if (amount < 1 || amount > MAX_AMOUNT) {
                Guis.deny(player);
                openCrate(player, crate, back);
                return;
            }
            onAmount.accept(amount);
        });
    }

    private void grant(Player admin, Crate crate, UUID target, int amount, Runnable back) {
        service.giveVirtualKeys(target, crate, amount);
        String name = economy.nameOf(target);
        Guis.success(admin);
        CrateLog.about(CrateLog.KEY_GRANT, admin, target, name, crate, amount,
                Tr.t("via menu admin"));
        Messages.send(admin, "crates.keys-given",
                Mini.value("amount", String.valueOf(amount)),
                Mini.value("player", name),
                Mini.styled("crate", crate.displayName()));
        Scheduling.entity(admin, () -> openCrate(admin, crate, back));
    }

    private void grantAll(Player admin, Crate crate, int amount, Runnable back) {
        int served = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            service.giveVirtualKeys(online.getUniqueId(), crate, amount);
            served++;
        }
        Guis.success(admin);
        CrateLog.player(CrateLog.KEY_GRANT, admin, crate, amount,
                served + Tr.t(" joueurs servis via menu admin"));
        Messages.send(admin, "crates.keys-given-all",
                Mini.value("amount", String.valueOf(amount)),
                Mini.value("players", String.valueOf(served)),
                Mini.styled("crate", crate.displayName()));
        Scheduling.entity(admin, () -> openCrate(admin, crate, back));
    }
}
