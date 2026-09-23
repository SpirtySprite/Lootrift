package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ConfirmMenu;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.gui.ChatPrompts;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CrateAdminMenu {

    private final CrateService service;
    private final CrateEditor editor;
    private final CrateEditorMenu editorMenu;
    private final CratePlacementMenu placementMenu;
    private final CrateKeyAdminMenu keyMenu;
    private final CrateHolograms holograms;

    public CrateAdminMenu(CrateService service, CrateEditor editor, CrateEditorMenu editorMenu,
                          CratePlacementMenu placementMenu, CrateKeyAdminMenu keyMenu,
                          CrateHolograms holograms) {
        this.service = service;
        this.editor = editor;
        this.editorMenu = editorMenu;
        this.placementMenu = placementMenu;
        this.keyMenu = keyMenu;
        this.holograms = holograms;
    }

    public void open(Player player) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Administration des caisses"))))
                .create();

        Guis.paginationBar(gui, null);
        gui.setItem(gui.getRows(), 2, createButton());
        gui.setItem(gui.getRows(), 4, settingsButton());
        gui.setItem(gui.getRows(), 6, placementsButton());
        gui.setItem(gui.getRows(), 8, keysButton());

        List<Crate> crates = service.crates();
        if (crates.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.DANGER + Tr.t("<b>Aucune caisse</b>"), Lore.create()
                            .blank()
                            .text(Tr.t("Prenez un objet en main puis"))
                            .text(Tr.t("utilisez le bouton de création"))
                            .text(Tr.t("en bas à gauche."))
                            .build()));
        }
        DeferredPage<Crate> page = Guis.deferred(gui, crates, 45, this::crateIcon);
        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem crateIcon(Crate crate) {
        int placements = service.placementRepository().count(crate.id());
        int circulation = service.keyRepository().circulation(crate.id());
        int opened = service.keyRepository().totalOpened(crate.id());
        return ItemBuilder.of(CrateIcons.editorCrate(crate, placements, circulation, opened))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click.isShiftClick() && click.isRightClick()) {
                        confirmDelete(player, crate);
                        return;
                    }
                    if (click.isShiftClick() && click.isLeftClick()) {
                        String copy = editor.duplicateCrate(crate.id());
                        Guis.success(player);
                        Messages.send(player, "crates.editor-crate-copied",
                                Mini.value("crate", copy));
                        open(player);
                        return;
                    }
                    Guis.click(player);
                    editorMenu.open(player, crate, () -> open(player));
                });
    }

    private GuiItem createButton() {
        return Guis.button(Material.NETHER_STAR, Palette.heading(Tr.t("Nouvelle caisse")),
                Lore.create()
                        .blank()
                        .text(Tr.t("Utilise l'objet dans votre main"))
                        .text(Tr.t("comme icône de la caisse."))
                        .blank()
                        .text(Tr.t("Une clé et un bloc par défaut sont"))
                        .text(Tr.t("créés, à ajuster ensuite."))
                        .blank()
                        .action(Tr.t("Cliquer pour créer"))
                        .build(),
                player -> {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        Guis.deny(player);
                        Messages.send(player, "crates.editor-hold-item");
                        return;
                    }
                    String id = editor.createCrate(held.clone());
                    Guis.success(player);
                    Messages.send(player, "crates.editor-crate-created", Mini.value("crate", id));
                    service.crate(id).ifPresentOrElse(
                            crate -> editorMenu.open(player, crate, () -> open(player)),
                            () -> open(player));
                });
    }

    private GuiItem placementsButton() {
        return Guis.button(Material.LODESTONE, Palette.heading(Tr.t("Caisses posées")),
                Lore.create()
                        .blank()
                        .count(Tr.t("Emplacements enregistrés"), service.placementRepository().count())
                        .count(Tr.t("Hologrammes actifs"), holograms.active())
                        .blank()
                        .action(Tr.t("Cliquer pour gérer"))
                        .build(),
                player -> placementMenu.open(player, () -> open(player)));
    }

    private GuiItem keysButton() {
        return Guis.button(Material.TRIPWIRE_HOOK, Palette.heading(Tr.t("Distribuer des clés")),
                Lore.create()
                        .blank()
                        .text(Tr.t("Créditer des clés à un joueur,"))
                        .text(Tr.t("à tous les connectés, ou vous"))
                        .text(Tr.t("remettre des clés physiques."))
                        .blank()
                        .action(Tr.t("Cliquer pour ouvrir"))
                        .build(),
                player -> keyMenu.open(player, () -> open(player)));
    }

    private GuiItem settingsButton() {
        return Guis.button(Material.COMPARATOR, Palette.heading(Tr.t("Réglages généraux")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Titre du menu"), service.title())
                        .ratio(Tr.t("Lignes"), service.rows(), 6)
                        .state(Tr.t("Annonces"), service.broadcastEnabled(), "actives", Tr.t("coupées"))
                        .blank()
                        .action(Tr.t("Cliquer pour configurer"))
                        .build(),
                this::openSettings);
    }

    public void openSettings(Player player) {
        long start = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(4)
                .title(Mini.parse(Palette.title(Tr.t("Réglages des caisses"))))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, Guis.display(Material.KNOWLEDGE_BOOK,
                Palette.heading(Tr.t("Vue d'ensemble")), Lore.create()
                        .blank()
                        .count(Tr.t("Caisses"), service.crateCount())
                        .count(Tr.t("Récompenses"), service.rewardCount())
                        .count(Tr.t("Emplacements posés"), service.placementRepository().count())
                        .count(Tr.t("Joueurs suivis"), service.keyRepository().trackedPlayers())
                        .build()));

        gui.setItem(2, 2, titleButton());
        gui.setItem(2, 4, rowsButton());
        gui.setItem(2, 6, toggle("broadcast", Tr.t("Annonces globales"),
                Material.BELL, service.broadcastEnabled(),
                Tr.t("Diffuse les gains rares à tout le serveur.")));
        gui.setItem(2, 8, toggle("sounds", Tr.t("Sons"),
                Material.NOTE_BLOCK, service.effects().soundsEnabled(),
                Tr.t("Cliquetis, révélation et ambiance.")));

        gui.setItem(3, 3, toggle("particles", Tr.t("Particules"),
                Material.BLAZE_POWDER, service.effects().particlesEnabled(),
                Tr.t("Halo des caisses posées et révélations.")));
        gui.setItem(3, 5, toggle("fireworks", Tr.t("Feux d'artifice"),
                Material.FIREWORK_ROCKET, service.effects().fireworksEnabled(),
                Tr.t("Uniquement sur les paliers légendaire et mythique.")));
        gui.setItem(3, 7, toggle("titles", Tr.t("Titres plein écran"),
                Material.OAK_SIGN, service.effects().titlesEnabled(),
                Tr.t("Nom de la récompense affiché à la révélation.")));

        gui.setItem(4, Guis.BACK_SLOT, Guis.backButton(() -> open(player)));
        gui.setItem(4, Guis.CLOSE_SLOT, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem toggle(String key, String label, Material icon, boolean active,
                           String description) {
        return Guis.button(icon, Palette.heading(label), Lore.create()
                .blank()
                .text(description)
                .blank()
                .state(Tr.t("État"), active, Tr.t("activé"), Tr.t("désactivé"))
                .blank()
                .action(Tr.t("Cliquer pour ") + (active ? Tr.t("désactiver") : "activer"))
                .build(), player -> {
            editor.setSetting(key, !active);
            Guis.click(player);
            openSettings(player);
        });
    }

    private GuiItem titleButton() {
        return Guis.button(Material.NAME_TAG, Palette.heading(Tr.t("Titre du menu")), Lore.create()
                .blank()
                .highlight(Tr.t("Actuel"), service.title())
                .blank()
                .text(Tr.t("Les couleurs MiniMessage sont acceptées."))
                .blank()
                .action(Tr.t("Cliquer pour renommer"))
                .build(), player ->
                ChatPrompts.open(player, Tr.t("le titre du menu"), input -> {
                    if (input == null || input.isBlank()) {
                        Guis.deny(player);
                        openSettings(player);
                        return;
                    }
                    editor.setTitle(input.trim());
                    openSettings(player);
                }));
    }

    private GuiItem rowsButton() {
        return ItemBuilder.of(new ItemStack(Material.CHEST))
                .name(Mini.label(Palette.heading(Tr.t("Hauteur du menu"))))
                .loreComponents(Mini.labels(Lore.create()
                        .blank()
                        .ratio(Tr.t("Lignes"), service.rows(), 6)
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("ajouter une ligne"))
                        .denyClick(Tr.t("Clic droit"), Tr.t("retirer une ligne"))
                        .build()))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    int rows = service.rows() + (event.getClick().isRightClick() ? -1 : 1);
                    if (rows < 3 || rows > 6) {
                        Guis.deny(player);
                        return;
                    }
                    editor.setRows(rows);
                    Guis.click(player);
                    openSettings(player);
                });
    }

    private void confirmDelete(Player player, Crate crate) {
        Guis.click(player);
        int placements = service.placementRepository().count(crate.id());
        Lore details = Lore.create()
                .highlight(Tr.t("Identifiant"), crate.id())
                .count(Tr.t("Récompenses perdues"), crate.rewards().size())
                .count(Tr.t("Caisses posées retirées"), placements)
                .count(Tr.t("Clés virtuelles annulées"), service.keyRepository().circulation(crate.id()))
                .blank()
                .deny(Tr.t("Cette action est irréversible"));
        ConfirmMenu.create(Tr.t("Supprimer une caisse"))
                .subject(crate.icon())
                .confirmLabel(Tr.t("Supprimer"))
                .question(Tr.t("Supprimer cette caisse ?"))
                .details(details.build())
                .onConfirm(viewer -> {
                    editor.deleteCrate(crate.id());
                    Guis.success(viewer);
                    Messages.send(viewer, "crates.editor-crate-deleted",
                            Mini.value("crate", crate.id()));
                    open(viewer);
                })
                .onCancel(this::open)
                .open(player);
    }
}
