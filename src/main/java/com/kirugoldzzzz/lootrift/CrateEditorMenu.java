package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ConfirmMenu;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.gui.ChatPrompts;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
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
                .title(Mini.parse(Palette.title("Éditeur de caisse")))
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
        return Guis.button(Material.NAME_TAG, Palette.heading("Nom de la caisse"), Lore.create()
                .blank()
                .highlight("Actuel", crate.displayName())
                .blank()
                .text("Les couleurs MiniMessage sont acceptées.")
                .blank()
                .action("Cliquer pour renommer")
                .build(), player ->
                ChatPrompts.open(player, "le nom de la caisse", input -> {
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
        return Guis.button(crate.icon().getType(), Palette.heading("Icône de la caisse"), Lore.create()
                .blank()
                .text("Affichée dans le menu des caisses")
                .text("et dans l'historique.")
                .blank()
                .action("Remplacer par l'objet en main")
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
        return Guis.button(shown, Palette.heading("Bloc de la caisse"), Lore.create()
                .blank()
                .entry("Bloc", crate.block().name())
                .blank()
                .text("Bloc posé dans le monde pour")
                .text("matérialiser la caisse.")
                .blank()
                .action("Définir depuis l'objet en main")
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
                        Palette.heading("Clé de la caisse"), Lore.create()
                                .blank()
                                .text("Modèle de clé physique remis")
                                .text("aux joueurs.")
                                .blank()
                                .click("Clic gauche", "remplacer par l'objet en main")
                                .click("Clic droit", "recevoir une clé pour l'inspecter")
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
                .entry("Actuelle", crate.animation().displayName())
                .blank();
        for (String line : crate.animation().description()) {
            lore.text(line);
        }
        return Guis.button(crate.animation().icon(), Palette.heading("Animation"), lore
                .blank()
                .action("Cliquer pour changer d'animation")
                .build(), player ->
                animationMenu.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem rollsButton(Crate crate, Runnable back) {
        return Guis.item(Material.CHEST,
                Palette.heading("Tirages par ouverture"),
                Lore.create()
                        .blank()
                        .ratio("Tirages", crate.rolls(), 9)
                        .blank()
                        .text("Nombre de récompenses remises")
                        .text("pour une seule clé.")
                        .blank()
                        .click("Clic gauche", "ajouter un tirage")
                        .denyClick("Clic droit", "retirer un tirage")
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
        return Guis.button(Material.BELL, Palette.heading("Annonces"), Lore.create()
                .blank()
                .state("Annonces", crate.broadcast(), "activées", "désactivées")
                .state("Réglage global", service.broadcastEnabled(), "activé", "désactivé")
                .blank()
                .text("Le réglage global settings.broadcast")
                .text("peut tout désactiver d'un coup.")
                .blank()
                .action("Cliquer pour inverser")
                .build(), player -> {
            editor.setBroadcast(crate.id(), !crate.broadcast());
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem permissionButton(Crate crate, Runnable back) {
        return Guis.item(Material.SHIELD,
                Palette.heading("Permission"),
                Lore.create()
                        .blank()
                        .entry("Permission", crate.permission() == null ? "aucune" : crate.permission())
                        .blank()
                        .text("Requise pour ouvrir la caisse.")
                        .blank()
                        .click("Clic gauche", "définir une permission")
                        .denyClick("Shift + clic droit", "retirer la permission")
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
                    ChatPrompts.open(player, "la permission", typed -> {
                        editor.setPermission(crate.id(), typed);
                        reopen(player, crate.id(), back);
                    });
                });
    }

    private GuiItem pityButton(Crate crate, Runnable back) {
        return Guis.item(Material.TOTEM_OF_UNDYING,
                Palette.heading("Pitié"),
                Lore.create()
                        .blank()
                        .entry("Après", crate.pityAfter() == 0
                                ? "désactivée"
                                : crate.pityAfter() + " ouvertures")
                        .entry("Palier garanti", crate.pityFloor().colored(crate.pityFloor().displayName()))
                        .blank()
                        .text("Garantit un palier minimum après")
                        .text("une série d'ouvertures décevantes.")
                        .blank()
                        .click("Clic gauche", "+5 ouvertures")
                        .click("Shift + clic gauche", "+25 ouvertures")
                        .denyClick("Clic droit", "-5 ouvertures")
                        .denyClick("Shift + clic droit", "-25 ouvertures")
                        .click("Clic molette", "changer le palier garanti")
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click == ClickType.MIDDLE) {
                        Guis.click(player);
                        rarityMenu.open(player, "Palier garanti", crate.pityFloor(), picked -> {
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
        return Guis.button(Material.WRITABLE_BOOK, Palette.heading("Statistiques"), Lore.create()
                .blank()
                .count("Ouvertures comptées", service.keyRepository().totalOpened(crate.id()))
                .blank()
                .text("Compare la chance annoncée de chaque")
                .text("récompense à celle réellement observée")
                .text("dans l'historique.")
                .blank()
                .action("Cliquer pour consulter")
                .build(), player -> statsMenu.open(player, crate,
                () -> reopen(player, crate.id(), back)));
    }

    private GuiItem priceButton(Crate crate, Runnable back) {
        Lore lore = Lore.create().blank();
        if (crate.purchasable()) {
            lore.money("Prix par clé", crate.price());
        } else {
            lore.text("Aucune vente, la clé ne s'achète pas.");
        }
        return Guis.button(Material.GOLD_INGOT, Palette.heading("Prix de la clé"), lore
                .blank()
                .text("Permet aux joueurs d'acheter une clé")
                .text("depuis l'aperçu de la caisse.")
                .blank()
                .text("Saisissez 0 pour retirer la vente.")
                .blank()
                .action("Cliquer pour définir")
                .build(), player -> ChatPrompts.open(player, "le prix de la clé", input -> {
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
        return Guis.button(Material.SUNFLOWER, Palette.heading("Clé quotidienne"), Lore.create()
                .blank()
                .state("Offerte", crate.dailyKey(), "oui", "non")
                .blank()
                .text("Chaque joueur peut réclamer une clé")
                .text("gratuite toutes les vingt quatre heures")
                .text("depuis l'aperçu de la caisse.")
                .blank()
                .action("Cliquer pour " + (crate.dailyKey() ? "retirer" : "activer"))
                .build(), player -> {
            editor.setDailyKey(crate.id(), !crate.dailyKey());
            Guis.click(player);
            reopen(player, crate.id(), back);
        });
    }

    private GuiItem bulkAnimationButton(Crate crate, Runnable back) {
        CrateBulkAnimation bulk = crate.bulkAnimation();
        return Guis.button(bulk.icon(), Palette.heading("Animation groupée"), Lore.create()
                .blank()
                .highlight("Actuelle", bulk.displayName())
                .text(bulk.description())
                .blank()
                .count("Motifs disponibles", CrateBulkAnimation.values().length)
                .blank()
                .action("Cliquer pour choisir")
                .build(), player -> animationMenu.openBulk(player, crate,
                () -> reopen(player, crate.id(), back)));
    }

    private GuiItem cooldownButton(Crate crate, Runnable back) {
        return Guis.item(Material.CLOCK,
                Palette.heading("Délai entre ouvertures"),
                Lore.create()
                        .blank()
                        .state("Actif", crate.throttled(), crate.cooldownSeconds() + "s", "aucun")
                        .blank()
                        .text("Empêche un joueur d'enchaîner")
                        .text("les ouvertures trop vite.")
                        .blank()
                        .click("Clic gauche", "+1 seconde")
                        .click("Shift + clic gauche", "+10 secondes")
                        .denyClick("Clic droit", "-1 seconde")
                        .denyClick("Shift + clic droit", "-10 secondes")
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
        return Guis.button(Material.FIREWORK_ROCKET, Palette.heading("Effets du bloc"),
                Lore.create()
                        .blank()
                        .highlight("Figure", effects.animation().displayName())
                        .entry("Particule", effects.particle().name())
                        .count("Densité", effects.density())
                        .blank()
                        .count("Figures disponibles", CrateBlockAnimation.values().length)
                        .blank()
                        .action("Cliquer pour régler")
                        .build(),
                player -> effectsMenu.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem hologramButton(Crate crate, Runnable back) {
        Lore lore = Lore.create()
                .blank()
                .state("Hologramme", crate.hologram(), "activé", "désactivé")
                .blank();
        if (crate.hologramLines().isEmpty()) {
            lore.text("Aucune ligne configurée");
        } else {
            for (String line : crate.hologramLines()) {
                lore.text(line);
            }
        }
        return Guis.item(Material.ITEM_FRAME,
                Palette.heading("Hologramme"),
                lore
                        .blank()
                        .click("Clic gauche", "inverser l'affichage")
                        .click("Clic droit", "ajouter une ligne")
                        .denyClick("Shift + clic droit", "effacer les lignes")
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
                        ChatPrompts.open(player, "la ligne", typed -> {
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
                .count("Récompenses", crate.rewards().size());
        if (crate.isEmpty()) {
            lore.blank().deny("Caisse inouvrable sans récompense");
        }
        return Guis.button(Material.BOOK, Palette.heading("Récompenses"), lore
                .blank()
                .text("Ajouter, régler ou retirer les gains")
                .text("et leurs probabilités.")
                .blank()
                .action("Cliquer pour ouvrir la liste")
                .build(), player ->
                rewardList.open(player, crate, () -> reopen(player, crate.id(), back)));
    }

    private GuiItem deleteButton(Crate crate, Runnable back) {
        return Guis.button(Material.BARRIER, Palette.DANGER + "<b>Supprimer la caisse</b>", Lore.create()
                .blank()
                .highlight("Identifiant", crate.id())
                .blank()
                .deny("Cette action est irréversible")
                .blank()
                .action("Cliquer pour supprimer")
                .build(), player -> confirmDelete(player, crate, back));
    }

    private void confirmDelete(Player player, Crate crate, Runnable back) {
        ConfirmMenu.create("Supprimer une caisse")
                .subject(crate.icon())
                .confirmLabel("Supprimer")
                .question("Supprimer cette caisse ?")
                .details(Lore.create()
                        .highlight("Identifiant", crate.id())
                        .count("Récompenses", crate.rewards().size())
                        .count("Caisses posées", service.placementRepository().count(crate.id()))
                        .count("Clés virtuelles", service.keyRepository().circulation(crate.id()))
                        .blank()
                        .deny("Les emplacements posés et les clés")
                        .deny("virtuelles seront également retirés")
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
