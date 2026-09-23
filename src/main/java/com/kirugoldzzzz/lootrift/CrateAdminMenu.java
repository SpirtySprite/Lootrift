package com.kirugoldzzzz.lootrift;

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
                .title(Mini.parse(Palette.title("Administration des caisses")))
                .create();

        Guis.paginationBar(gui, null);
        gui.setItem(gui.getRows(), 2, createButton());
        gui.setItem(gui.getRows(), 4, settingsButton());
        gui.setItem(gui.getRows(), 6, placementsButton());
        gui.setItem(gui.getRows(), 8, keysButton());

        List<Crate> crates = service.crates();
        if (crates.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.DANGER + "<b>Aucune caisse</b>", Lore.create()
                            .blank()
                            .text("Prenez un objet en main puis")
                            .text("utilisez le bouton de création")
                            .text("en bas à gauche.")
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
        return Guis.button(Material.NETHER_STAR, Palette.heading("Nouvelle caisse"),
                Lore.create()
                        .blank()
                        .text("Utilise l'objet dans votre main")
                        .text("comme icône de la caisse.")
                        .blank()
                        .text("Une clé et un bloc par défaut sont")
                        .text("créés, à ajuster ensuite.")
                        .blank()
                        .action("Cliquer pour créer")
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
        return Guis.button(Material.LODESTONE, Palette.heading("Caisses posées"),
                Lore.create()
                        .blank()
                        .count("Emplacements enregistrés", service.placementRepository().count())
                        .count("Hologrammes actifs", holograms.active())
                        .blank()
                        .action("Cliquer pour gérer")
                        .build(),
                player -> placementMenu.open(player, () -> open(player)));
    }

    private GuiItem keysButton() {
        return Guis.button(Material.TRIPWIRE_HOOK, Palette.heading("Distribuer des clés"),
                Lore.create()
                        .blank()
                        .text("Créditer des clés à un joueur,")
                        .text("à tous les connectés, ou vous")
                        .text("remettre des clés physiques.")
                        .blank()
                        .action("Cliquer pour ouvrir")
                        .build(),
                player -> keyMenu.open(player, () -> open(player)));
    }

    private GuiItem settingsButton() {
        return Guis.button(Material.COMPARATOR, Palette.heading("Réglages généraux"),
                Lore.create()
                        .blank()
                        .entry("Titre du menu", service.title())
                        .ratio("Lignes", service.rows(), 6)
                        .state("Annonces", service.broadcastEnabled(), "actives", "coupées")
                        .blank()
                        .action("Cliquer pour configurer")
                        .build(),
                this::openSettings);
    }

    public void openSettings(Player player) {
        long start = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(4)
                .title(Mini.parse(Palette.title("Réglages des caisses")))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, Guis.display(Material.KNOWLEDGE_BOOK,
                Palette.heading("Vue d'ensemble"), Lore.create()
                        .blank()
                        .count("Caisses", service.crateCount())
                        .count("Récompenses", service.rewardCount())
                        .count("Emplacements posés", service.placementRepository().count())
                        .count("Joueurs suivis", service.keyRepository().trackedPlayers())
                        .build()));

        gui.setItem(2, 2, titleButton());
        gui.setItem(2, 4, rowsButton());
        gui.setItem(2, 6, toggle("broadcast", "Annonces globales",
                Material.BELL, service.broadcastEnabled(),
                "Diffuse les gains rares à tout le serveur."));
        gui.setItem(2, 8, toggle("sounds", "Sons",
                Material.NOTE_BLOCK, service.effects().soundsEnabled(),
                "Cliquetis, révélation et ambiance."));

        gui.setItem(3, 3, toggle("particles", "Particules",
                Material.BLAZE_POWDER, service.effects().particlesEnabled(),
                "Halo des caisses posées et révélations."));
        gui.setItem(3, 5, toggle("fireworks", "Feux d'artifice",
                Material.FIREWORK_ROCKET, service.effects().fireworksEnabled(),
                "Uniquement sur les paliers légendaire et mythique."));
        gui.setItem(3, 7, toggle("titles", "Titres plein écran",
                Material.OAK_SIGN, service.effects().titlesEnabled(),
                "Nom de la récompense affiché à la révélation."));

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
                .state("État", active, "activé", "désactivé")
                .blank()
                .action("Cliquer pour " + (active ? "désactiver" : "activer"))
                .build(), player -> {
            editor.setSetting(key, !active);
            Guis.click(player);
            openSettings(player);
        });
    }

    private GuiItem titleButton() {
        return Guis.button(Material.NAME_TAG, Palette.heading("Titre du menu"), Lore.create()
                .blank()
                .highlight("Actuel", service.title())
                .blank()
                .text("Les couleurs MiniMessage sont acceptées.")
                .blank()
                .action("Cliquer pour renommer")
                .build(), player ->
                ChatPrompts.open(player, "le titre du menu", input -> {
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
                .name(Mini.label(Palette.heading("Hauteur du menu")))
                .loreComponents(Mini.labels(Lore.create()
                        .blank()
                        .ratio("Lignes", service.rows(), 6)
                        .blank()
                        .click("Clic gauche", "ajouter une ligne")
                        .denyClick("Clic droit", "retirer une ligne")
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
                .highlight("Identifiant", crate.id())
                .count("Récompenses perdues", crate.rewards().size())
                .count("Caisses posées retirées", placements)
                .count("Clés virtuelles annulées", service.keyRepository().circulation(crate.id()))
                .blank()
                .deny("Cette action est irréversible");
        ConfirmMenu.create("Supprimer une caisse")
                .subject(crate.icon())
                .confirmLabel("Supprimer")
                .question("Supprimer cette caisse ?")
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
