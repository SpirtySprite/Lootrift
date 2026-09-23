package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ChatPrompts;
import com.kirugoldzzzz.lootrift.common.gui.ConfirmMenu;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CrateEditorMenu {

    private static final int ROWS = 6;

    private final CrateService service;
    private final CrateEditor editor;
    private final CrateRewardListMenu rewardList;
    private final CrateAnimationMenu animationMenu;
    private final CrateRarityMenu rarityMenu;
    private final CrateEffectsMenu effectsMenu;
    private final CrateStatsMenu statsMenu;

    public CrateEditorMenu(CrateService service, CrateEditor editor, CrateRewardListMenu rewardList,
                           CrateAnimationMenu animationMenu, CrateRarityMenu rarityMenu,
                           CrateEffectsMenu effectsMenu, CrateStatsMenu statsMenu) {
        this.service = service;
        this.editor = editor;
        this.rewardList = rewardList;
        this.animationMenu = animationMenu;
        this.rarityMenu = rarityMenu;
        this.effectsMenu = effectsMenu;
        this.statsMenu = statsMenu;
    }

    public void open(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.title(Tr.t("Éditeur de caisse"))))
                .create();

        Guis.fill(gui);
        gui.setItem(1, 5, summaryIcon(crate));

        gui.setItem(2, 2, nameButton(crate, back));
        gui.setItem(2, 4, iconButton(crate, back));
        gui.setItem(2, 6, blockButton(crate, back));
        gui.setItem(2, 8, keyButton(crate, back));

        gui.setItem(3, 2, animationButton(crate, back));
        gui.setItem(3, 4, rollsButton(crate, back));
        gui.setItem(3, 6, broadcastButton(crate, back));
        gui.setItem(3, 8, permissionButton(crate, back));

        gui.setItem(4, 2, pityButton(crate, back));
        gui.setItem(4, 4, rewardsButton(crate, back));
        gui.setItem(4, 6, hologramButton(crate, back));
        gui.setItem(4, 8, effectsButton(crate, back));
        gui.setItem(5, 1, bulkAnimationButton(crate, back));

        gui.setItem(5, 3, cooldownButton(crate, back));
        gui.setItem(5, 5, priceButton(crate, back));
        gui.setItem(5, 7, dailyButton(crate, back));

        gui.setItem(ROWS, 3, statsButton(crate, back));
        gui.setItem(ROWS, 5, deleteButton(crate, back));
        gui.setItem(ROWS, Guis.BACK_SLOT, Guis.backButton(back));
        gui.setItem(ROWS, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem summaryIcon(Crate crate) {
        int placements = service.placementRepository().count(crate.id());
        int circulation = service.keyRepository().circulation(crate.id());
        int opened = service.keyRepository().totalOpened(crate.id());
        return ItemBuilder.of(CrateIcons.editorCrate(crate, placements, circulation, opened))
                .asGuiItem(event -> event.setCancelled(true));
    }

    private GuiItem nameButton(Crate crate, Runnable back) {
        return Guis.button(Material.NAME_TAG, Palette.heading(Tr.t("Nom de la caisse")), Lore.create()
                .blank()
                .highlight(Tr.t("Actuel"), crate.displayName())
                .blank()
                .text(Tr.t("Les couleurs MiniMessage sont acceptées."))
                .blank()
                .action(Tr.t("Cliquer pour renommer"))
                .build(), player ->
                ChatPrompts.open(player, Tr.t("le nom de la caisse"), input -> {
                    if (input == null || input.isBlank()) {
                        Guis.deny(player);
                        reopen(player, crate.id(), back);
                        return;
                    }
                    editor.renameCrate(crate.id(), input.trim());
                    reopen(player, crate.id(), back);
                }));
    }

    private GuiItem iconButton(Crate crate, Runnable back) {
        return Guis.button(crate.icon().getType(), Palette.heading(Tr.t("Icône de la caisse")), Lore.create()
                .blank()
                .text(Tr.t("Affichée dans le menu des caisses"))
                .text(Tr.t("et dans l'historique."))
                .blank()
                .action(Tr.t("Remplacer par l'objet en main"))
                .build(), player -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                Guis.deny(player);
                Messages.send(player, "crates.editor-hold-item");
                return;
            }
            editor.setIcon(crate.id(), held.clone());
            Guis.success(player);
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem blockButton(Crate crate, Runnable back) {
        Material shown = crate.block().isItem() ? crate.block() : Material.CHEST;
        return Guis.button(shown, Palette.heading(Tr.t("Bloc de la caisse")), Lore.create()
                .blank()
                .entry(Tr.t("Bloc"), crate.block().name())
                .blank()
                .text(Tr.t("Bloc posé dans le monde pour"))
                .text(Tr.t("matérialiser la caisse."))
                .blank()
                .action(Tr.t("Définir depuis l'objet en main"))
                .build(), player -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir() || !held.getType().isBlock()) {
                Guis.deny(player);
                Messages.send(player, "crates.editor-hold-item");
                return;
            }
            editor.setBlock(crate.id(), held.getType());
            Guis.success(player);
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem keyButton(Crate crate, Runnable back) {
        return ItemBuilder.of(CrateIcons.renamed(CrateIcons.key(crate, 1),
                        Palette.heading(Tr.t("Clé de la caisse")), Lore.create()
                                .blank()
                                .text(Tr.t("Modèle de clé physique remis"))
                                .text(Tr.t("aux joueurs."))
                                .blank()
                                .click(Tr.t("Clic gauche"), Tr.t("remplacer par l'objet en main"))
                                .click(Tr.t("Clic droit"), Tr.t("recevoir une clé pour l'inspecter"))
                                .build()))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    if (event.getClick().isRightClick()) {
                        service.givePhysicalKeys(player, crate, 1);
                        Guis.success(player);
                        return;
                    }
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        Guis.deny(player);
                        Messages.send(player, "crates.editor-hold-item");
                        return;
                    }
                    editor.setKeyItem(crate.id(), held.clone());
                    Guis.success(player);
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem animationButton(Crate crate, Runnable back) {
        Lore lore = Lore.create()
                .blank()
                .entry(Tr.t("Actuelle"), crate.animation().displayName())
                .blank();
        for (String line : crate.animation().description()) {
            lore.text(line);
        }
        return Guis.button(crate.animation().icon(), Palette.heading(Tr.t("Animation")), lore
                .blank()
                .action(Tr.t("Cliquer pour changer d'animation"))
                .build(), player ->
                animationMenu.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem rollsButton(Crate crate, Runnable back) {
        return Guis.item(Material.CHEST,
                Palette.heading(Tr.t("Tirages par ouverture")),
                Lore.create()
                        .blank()
                        .ratio(Tr.t("Tirages"), crate.rolls(), 9)
                        .blank()
                        .text(Tr.t("Nombre de récompenses remises"))
                        .text(Tr.t("pour une seule clé."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("ajouter un tirage"))
                        .denyClick(Tr.t("Clic droit"), Tr.t("retirer un tirage"))
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    int rolls = crate.rolls() + (event.getClick().isRightClick() ? -1 : 1);
                    if (rolls < 1 || rolls > 9) {
                        Guis.deny(player);
                        return;
                    }
                    editor.setRolls(crate.id(), rolls);
                    Guis.click(player);
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem broadcastButton(Crate crate, Runnable back) {
        return Guis.button(Material.BELL, Palette.heading(Tr.t("Annonces")), Lore.create()
                .blank()
                .state(Tr.t("Annonces"), crate.broadcast(), Tr.t("activées"), Tr.t("désactivées"))
                .state(Tr.t("Réglage global"), service.broadcastEnabled(), Tr.t("activé"), Tr.t("désactivé"))
                .blank()
                .text(Tr.t("Le réglage global settings.broadcast"))
                .text(Tr.t("peut tout désactiver d'un coup."))
                .blank()
                .action(Tr.t("Cliquer pour inverser"))
                .build(), player -> {
            editor.setBroadcast(crate.id(), !crate.broadcast());
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem permissionButton(Crate crate, Runnable back) {
        return Guis.item(Material.SHIELD,
                Palette.heading(Tr.t("Permission")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Permission"), crate.permission() == null ? Tr.t("aucune") : crate.permission())
                        .blank()
                        .text(Tr.t("Requise pour ouvrir la caisse."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("définir une permission"))
                        .denyClick(Tr.t("Shift + clic droit"), Tr.t("retirer la permission"))
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    Guis.click(player);
                    if (click.isShiftClick() && click.isRightClick()) {
                        editor.setPermission(crate.id(), null);
                        reopen(player, crate.id(), back);
                        return;
                    }
                    ChatPrompts.open(player, Tr.t("la permission"), typed -> {
                        editor.setPermission(crate.id(), typed);
                        reopen(player, crate.id(), back);
                    });
                });
    }

    private GuiItem pityButton(Crate crate, Runnable back) {
        return Guis.item(Material.TOTEM_OF_UNDYING,
                Palette.heading(Tr.t("Pitié")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Après"), crate.pityAfter() == 0
                                ? Tr.t("désactivée")
                                : crate.pityAfter() + Tr.t(" ouvertures"))
                        .entry(Tr.t("Palier garanti"), crate.pityFloor().colored(crate.pityFloor().displayName()))
                        .blank()
                        .text(Tr.t("Garantit un palier minimum après"))
                        .text(Tr.t("une série d'ouvertures décevantes."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("+5 ouvertures"))
                        .click(Tr.t("Shift + clic gauche"), Tr.t("+25 ouvertures"))
                        .denyClick(Tr.t("Clic droit"), "-5 ouvertures")
                        .denyClick(Tr.t("Shift + clic droit"), "-25 ouvertures")
                        .click(Tr.t("Clic molette"), Tr.t("changer le palier garanti"))
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click == ClickType.MIDDLE) {
                        Guis.click(player);
                        rarityMenu.open(player, Tr.t("Palier garanti"), crate.pityFloor(), picked -> {
                            editor.setPity(crate.id(), crate.pityAfter(), picked);
                            reopen(player, crate.id(), back);
                        }, () -> open(player, crate, back));
                        return;
                    }
                    int step = click.isShiftClick() ? 25 : 5;
                    int after = Math.max(0, crate.pityAfter() + (click.isRightClick() ? -step : step));
                    editor.setPity(crate.id(), after, crate.pityFloor());
                    Guis.click(player);
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem statsButton(Crate crate, Runnable back) {
        return Guis.button(Material.WRITABLE_BOOK, Palette.heading(Tr.t("Statistiques")), Lore.create()
                .blank()
                .count(Tr.t("Ouvertures comptées"), service.keyRepository().totalOpened(crate.id()))
                .blank()
                .text(Tr.t("Compare la chance annoncée de chaque"))
                .text(Tr.t("récompense à celle réellement observée"))
                .text("dans l'historique.")
                .blank()
                .action(Tr.t("Cliquer pour consulter"))
                .build(), player -> statsMenu.open(player, crate,
                () -> reopen(player, crate.id(), back)));
    }

    private GuiItem priceButton(Crate crate, Runnable back) {
        Lore lore = Lore.create().blank();
        if (crate.purchasable()) {
            lore.money(Tr.t("Prix par clé"), crate.price());
        } else {
            lore.text(Tr.t("Aucune vente, la clé ne s'achète pas."));
        }
        return Guis.button(Material.GOLD_INGOT, Palette.heading(Tr.t("Prix de la clé")), lore
                .blank()
                .text(Tr.t("Permet aux joueurs d'acheter une clé"))
                .text(Tr.t("depuis l'aperçu de la caisse."))
                .blank()
                .text(Tr.t("Saisissez 0 pour retirer la vente."))
                .blank()
                .action(Tr.t("Cliquer pour définir"))
                .build(), player -> ChatPrompts.open(player, Tr.t("le prix de la clé"), input -> {
            java.util.OptionalDouble parsed = Numbers.parseAmount(input);
            if (parsed.isEmpty()) {
                Guis.deny(player);
                reopen(player, crate.id(), back);
                return;
            }
            editor.setPrice(crate.id(), parsed.getAsDouble());
            Guis.success(player);
            reopen(player, crate.id(), back);
        }));
    }

    private GuiItem dailyButton(Crate crate, Runnable back) {
        return Guis.button(Material.SUNFLOWER, Palette.heading(Tr.t("Clé quotidienne")), Lore.create()
                .blank()
                .state(Tr.t("Offerte"), crate.dailyKey(), Tr.t("oui"), Tr.t("non"))
                .blank()
                .text(Tr.t("Chaque joueur peut réclamer une clé"))
                .text(Tr.t("gratuite toutes les vingt quatre heures"))
                .text(Tr.t("depuis l'aperçu de la caisse."))
                .blank()
                .action(Tr.t("Cliquer pour ") + (crate.dailyKey() ? "retirer" : "activer"))
                .build(), player -> {
            editor.setDailyKey(crate.id(), !crate.dailyKey());
            Guis.click(player);
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem bulkAnimationButton(Crate crate, Runnable back) {
        CrateBulkAnimation bulk = crate.bulkAnimation();
        return Guis.button(bulk.icon(), Palette.heading(Tr.t("Animation groupée")), Lore.create()
                .blank()
                .highlight(Tr.t("Actuelle"), bulk.displayName())
                .text(bulk.description())
                .blank()
                .count(Tr.t("Motifs disponibles"), CrateBulkAnimation.values().length)
                .blank()
                .action(Tr.t("Cliquer pour choisir"))
                .build(), player -> animationMenu.openBulk(player, crate,
                () -> reopen(player, crate.id(), back)));
    }

    private GuiItem cooldownButton(Crate crate, Runnable back) {
        return Guis.item(Material.CLOCK,
                Palette.heading(Tr.t("Délai entre ouvertures")),
                Lore.create()
                        .blank()
                        .state(Tr.t("Actif"), crate.throttled(), crate.cooldownSeconds() + "s", Tr.t("aucun"))
                        .blank()
                        .text(Tr.t("Empêche un joueur d'enchaîner"))
                        .text(Tr.t("les ouvertures trop vite."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("+1 seconde"))
                        .click(Tr.t("Shift + clic gauche"), Tr.t("+10 secondes"))
                        .denyClick(Tr.t("Clic droit"), "-1 seconde")
                        .denyClick(Tr.t("Shift + clic droit"), "-10 secondes")
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    int step = click.isShiftClick() ? 10 : 1;
                    int updated = crate.cooldownSeconds() + (click.isRightClick() ? -step : step);
                    if (updated < 0 || updated > 3600) {
                        Guis.deny(player);
                        return;
                    }
                    editor.setCooldown(crate.id(), updated);
                    Guis.click(player);
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem effectsButton(Crate crate, Runnable back) {
        CrateBlockEffects effects = crate.blockEffects();
        return Guis.button(Material.FIREWORK_ROCKET, Palette.heading(Tr.t("Effets du bloc")),
                Lore.create()
                        .blank()
                        .highlight(Tr.t("Figure"), effects.animation().displayName())
                        .entry(Tr.t("Particule"), effects.particle().name())
                        .count(Tr.t("Densité"), effects.density())
                        .blank()
                        .count(Tr.t("Figures disponibles"), CrateBlockAnimation.values().length)
                        .blank()
                        .action(Tr.t("Cliquer pour régler"))
                        .build(),
                player -> effectsMenu.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem hologramButton(Crate crate, Runnable back) {
        Lore lore = Lore.create()
                .blank()
                .state(Tr.t("Hologramme"), crate.hologram(), Tr.t("activé"), Tr.t("désactivé"))
                .blank();
        if (crate.hologramLines().isEmpty()) {
            lore.text(Tr.t("Aucune ligne configurée"));
        } else {
            for (String line : crate.hologramLines()) {
                lore.text(line);
            }
        }
        return Guis.item(Material.ITEM_FRAME,
                Palette.heading(Tr.t("Hologramme")),
                lore
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("inverser l'affichage"))
                        .click(Tr.t("Clic droit"), Tr.t("ajouter une ligne"))
                        .denyClick(Tr.t("Shift + clic droit"), Tr.t("effacer les lignes"))
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    Guis.click(player);
                    if (click.isShiftClick() && click.isRightClick()) {
                        editor.setHologramLines(crate.id(), List.of());
                        reopen(player, crate.id(), back);
                        return;
                    }
                    if (click.isRightClick()) {
                        ChatPrompts.open(player, Tr.t("la ligne"), typed -> {
                            if (!typed.isBlank()) {
                                List<String> lines = new ArrayList<>(crate.hologramLines());
                                lines.add(typed);
                                editor.setHologramLines(crate.id(), lines);
                            }
                            reopen(player, crate.id(), back);
                        });
                        return;
                    }
                    editor.setHologram(crate.id(), !crate.hologram());
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem rewardsButton(Crate crate, Runnable back) {
        Lore lore = Lore.create()
                .blank()
                .count(Tr.t("Récompenses"), crate.rewards().size());
        if (crate.isEmpty()) {
            lore.blank().deny(Tr.t("Caisse inouvrable sans récompense"));
        }
        return Guis.button(Material.BOOK, Palette.heading(Tr.t("Récompenses")), lore
                .blank()
                .text(Tr.t("Ajouter, régler ou retirer les gains"))
                .text(Tr.t("et leurs probabilités."))
                .blank()
                .action(Tr.t("Cliquer pour ouvrir la liste"))
                .build(), player ->
                rewardList.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem deleteButton(Crate crate, Runnable back) {
        return Guis.button(Material.BARRIER, Palette.DANGER + Tr.t("<b>Supprimer la caisse</b>"), Lore.create()
                .blank()
                .highlight(Tr.t("Identifiant"), crate.id())
                .blank()
                .deny(Tr.t("Cette action est irréversible"))
                .blank()
                .action(Tr.t("Cliquer pour supprimer"))
                .build(), player -> confirmDelete(player, crate, back));
    }

    private void confirmDelete(Player player, Crate crate, Runnable back) {
        ConfirmMenu.create(Tr.t("Supprimer une caisse"))
                .subject(crate.icon())
                .confirmLabel(Tr.t("Supprimer"))
                .question(Tr.t("Supprimer cette caisse ?"))
                .details(Lore.create()
                        .highlight(Tr.t("Identifiant"), crate.id())
                        .count(Tr.t("Récompenses"), crate.rewards().size())
                        .count(Tr.t("Caisses posées"), service.placementRepository().count(crate.id()))
                        .count(Tr.t("Clés virtuelles"), service.keyRepository().circulation(crate.id()))
                        .blank()
                        .deny(Tr.t("Les emplacements posés et les clés"))
                        .deny(Tr.t("virtuelles seront également retirés"))
                        .build())
                .onConfirm(viewer -> {
                    editor.deleteCrate(crate.id());
                    Guis.success(viewer);
                    Messages.send(viewer, "crates.editor-crate-deleted", Mini.value("crate", crate.id()));
                    back.run();
                })
                .onCancel(viewer -> open(viewer, crate, back))
                .open(player);
    }

    private void reopen(Player player, String crateId, Runnable back) {
        service.crate(crateId).ifPresentOrElse(crate -> open(player, crate, back), () -> {
            Guis.deny(player);
            back.run();
        });
    }
}
