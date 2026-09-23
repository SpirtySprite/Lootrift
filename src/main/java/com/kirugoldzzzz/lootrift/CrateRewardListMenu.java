package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.ConfirmMenu;
import com.kirugoldzzzz.lootrift.common.gui.DeferredPage;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.item.Shulkers;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CrateRewardListMenu {

    private static final Comparator<CrateReward> ORDER = Comparator
            .comparingInt((CrateReward reward) -> reward.rarity().tier()).reversed()
            .thenComparingInt(CrateReward::weight)
            .thenComparing(CrateReward::id);

    private final CrateService service;
    private final CrateEditor editor;
    private final CrateRewardEditorMenu rewardEditor;

    public CrateRewardListMenu(CrateService service, CrateEditor editor,
                               CrateRewardEditorMenu rewardEditor) {
        this.service = service;
        this.editor = editor;
        this.rewardEditor = rewardEditor;
    }

    public void open(Player player, Crate crate, Runnable back) {
        long start = System.nanoTime();
        PaginatedGui gui = PaginatedGui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title("Récompenses")))
                .create();

        Guis.paginationBar(gui, back);
        gui.setItem(gui.getRows(), 2, addButton(crate, back));
        gui.setItem(gui.getRows(), 8, importButton(crate, back));
        gui.setItem(gui.getRows(), 4, summary(crate));
        gui.setItem(gui.getRows(), 6, weightsButton(crate));

        List<CrateReward> rewards = new ArrayList<>(crate.rewards());
        rewards.sort(ORDER);

        if (rewards.isEmpty()) {
            gui.setItem(3, 5, Guis.display(Material.COBWEB,
                    Palette.DANGER + "<b>Aucune récompense</b>", Lore.create()
                            .blank()
                            .text("Cette caisse ne peut pas encore")
                            .text("être ouverte.")
                            .blank()
                            .hint("Prenez un objet en main puis")
                            .hint("utilisez le bouton d'ajout.")
                            .build()));
        }
        DeferredPage<CrateReward> page = Guis.deferred(gui, rewards, 45,
                reward -> entry(crate, reward, back));

        Guis.controls(gui, page);
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem entry(Crate crate, CrateReward reward, Runnable back) {
        return ItemBuilder.of(CrateIcons.editorReward(crate, reward))
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click.isShiftClick() && click.isRightClick()) {
                        confirmDelete(player, crate, reward, back);
                        return;
                    }
                    Guis.click(player);
                    rewardEditor.open(player, crate, reward,
                            () -> reopen(player, crate.id(), back));
                });
    }

    private GuiItem addButton(Crate crate, Runnable back) {
        return Guis.button(Material.NETHER_STAR, Palette.heading("Ajouter une récompense"),
                Lore.create()
                        .blank()
                        .text("Ajoute l'objet tenu en main")
                        .text("comme nouvelle récompense.")
                        .blank()
                        .text("Poids 10 et rareté commune par")
                        .text("défaut, modifiables ensuite.")
                        .blank()
                        .action("Cliquer pour ajouter")
                        .build(),
                player -> {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        Guis.deny(player);
                        Messages.send(player, "crates.editor-hold-item");
                        return;
                    }
                    String id = editor.addReward(crate.id(), held.clone());
                    Guis.success(player);
                    Messages.send(player, "crates.editor-reward-added", Mini.value("reward", id));
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem importButton(Crate crate, Runnable back) {
        return Guis.button(Material.SHULKER_BOX, Palette.heading("Importer un conteneur"),
                Lore.create()
                        .blank()
                        .text("Ajoute d'un coup tout le contenu")
                        .text("de la boîte de shulker tenue en main.")
                        .blank()
                        .text("Chaque objet devient une récompense")
                        .text("de poids 10 et de rareté commune.")
                        .blank()
                        .action("Cliquer pour importer")
                        .build(),
                player -> {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    List<ItemStack> contents = Shulkers.contentsOf(held);
                    if (contents.isEmpty()) {
                        Guis.deny(player);
                        Messages.send(player, "crates.editor-empty-container");
                        return;
                    }
                    int added = editor.importRewards(crate.id(), contents);
                    Guis.success(player);
                    Messages.send(player, "crates.editor-imported",
                            Mini.value("amount", String.valueOf(added)));
                    reopen(player, crate.id(), back);
                });
    }

    private GuiItem summary(Crate crate) {
        Lore lore = Lore.create()
                .blank()
                .highlight("Caisse", crate.id())
                .count("Récompenses", crate.rewards().size())
                .count("Poids total", crate.totalWeight())
                .blank();
        for (CrateRarity rarity : CrateRarity.values()) {
            int count = crate.countOf(rarity);
            if (count > 0) {
                lore.entry(rarity.colored(rarity.displayName()), count);
            }
        }
        if (crate.isEmpty()) {
            lore.blank().deny("Une caisse vide ne peut pas s'ouvrir");
        }
        return Guis.display(Material.KNOWLEDGE_BOOK, Palette.heading(crate.displayName()),
                lore.build());
    }

    private GuiItem weightsButton(Crate crate) {
        Lore lore = Lore.create()
                .blank()
                .text("La chance d'une récompense vaut")
                .text("son poids divisé par le total.")
                .blank()
                .count("Poids total", crate.totalWeight())
                .blank();
        List<CrateReward> rewards = new ArrayList<>(crate.rewards());
        rewards.sort(ORDER);
        int shown = Math.min(6, rewards.size());
        for (int index = 0; index < shown; index++) {
            CrateReward reward = rewards.get(index);
            lore.entry(reward.rarity().colored(reward.id()),
                    CrateIcons.chance(crate.chanceOf(reward)));
        }
        if (rewards.size() > shown) {
            lore.text("et " + (rewards.size() - shown) + " autres");
        }
        return Guis.display(Material.COMPARATOR, Palette.heading("Probabilités"), lore.build());
    }

    private void confirmDelete(Player player, Crate crate, CrateReward reward, Runnable back) {
        Guis.click(player);
        ConfirmMenu.create("Supprimer une récompense")
                .subject(reward.display())
                .confirmLabel("Supprimer")
                .question("Supprimer cette récompense ?")
                .details(Lore.create()
                        .highlight("Identifiant", reward.id())
                        .entry("Rareté", reward.rarity().colored(reward.rarity().displayName()))
                        .highlight("Chance actuelle", CrateIcons.chance(crate.chanceOf(reward)))
                        .blank()
                        .deny("Cette action est irréversible")
                        .build())
                .onConfirm(viewer -> {
                    editor.deleteReward(crate.id(), reward.id());
                    Guis.success(viewer);
                    Messages.send(viewer, "crates.editor-reward-deleted",
                            Mini.value("reward", reward.id()));
                    reopen(viewer, crate.id(), back);
                })
                .onCancel(viewer -> reopen(viewer, crate.id(), back))
                .open(player);
    }

    private void reopen(Player player, String crateId, Runnable back) {
        service.crate(crateId).ifPresentOrElse(
                crate -> open(player, crate, back),
                () -> {
                    if (back != null) {
                        back.run();
                    } else {
                        player.closeInventory();
                    }
                });
    }
}
