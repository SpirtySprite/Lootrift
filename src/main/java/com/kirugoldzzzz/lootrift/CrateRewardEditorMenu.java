package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

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

import java.util.OptionalDouble;

public final class CrateRewardEditorMenu {

    private static final int COMMAND_PREVIEW = 40;

    private final CrateService service;
    private final CrateEditor editor;
    private final CrateRarityMenu rarityMenu;

    public CrateRewardEditorMenu(CrateService service, CrateEditor editor, CrateRarityMenu rarityMenu) {
        this.service = service;
        this.editor = editor;
        this.rarityMenu = rarityMenu;
    }

    public void open(Player player, Crate crate, CrateReward reward, Runnable back) {
        long start = System.nanoTime();
        Gui gui = Gui.builder()
                .rows(6)
                .title(Mini.parse(Palette.title(Tr.t("Récompense"))))
                .create();
        Guis.fill(gui);

        gui.setItem(1, 5, ItemBuilder.of(CrateIcons.editorReward(crate, reward))
                .asGuiItem(event -> event.setCancelled(true)));

        gui.setItem(2, 2, itemButton(crate, reward, back));
        gui.setItem(2, 4, weightButton(crate, reward, back));
        gui.setItem(2, 6, rarityButton(crate, reward, back));
        gui.setItem(2, 8, amountButton(crate, reward, back));

        gui.setItem(3, 2, moneyButton(crate, reward, back));
        gui.setItem(3, 4, commandsButton(crate, reward, back));
        gui.setItem(3, 6, giveItemButton(crate, reward, back));
        gui.setItem(3, 8, announceButton(crate, reward, back));

        gui.setItem(4, 2, uniqueButton(crate, reward, back));
        gui.setItem(4, 4, permissionButton(crate, reward, back));
        gui.setItem(4, 6, chanceButton(crate, reward, back));

        gui.setItem(6, 1, Guis.backButton(back));
        gui.setItem(6, 5, deleteButton(crate, reward, back));
        gui.setItem(6, 9, Guis.closeButton());

        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem itemButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.NAME_TAG, Palette.heading(Tr.t("Objet")), Lore.create()
                .blank()
                .text(Tr.t("Remplace l'objet affiché dans"))
                .text(Tr.t("l'animation et remis au gagnant."))
                .blank()
                .action(Tr.t("Remplacer par l'objet en main"))
                .build(), player -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                Guis.deny(player);
                Messages.send(player, "crates.editor-hold-item");
                return;
            }
            editor.setRewardItem(crate.id(), reward.id(), held.clone());
            Guis.success(player);
            reopen(player, crate.id(), reward.id(), back);
        });
    }

    private GuiItem uniqueButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.END_CRYSTAL, Palette.heading(Tr.t("Récompense unique")),
                Lore.create()
                        .blank()
                        .state(Tr.t("Unique"), reward.solo(), Tr.t("oui"), Tr.t("non"))
                        .blank()
                        .text(Tr.t("Une fois gagnée, elle sort du tirage"))
                        .text(Tr.t("pour ce joueur et ne peut plus"))
                        .text(Tr.t("tomber une seconde fois."))
                        .blank()
                        .text(Tr.t("Idéal pour une collection à compléter."))
                        .blank()
                        .action(Tr.t("Cliquer pour ") + (reward.solo() ? "retirer" : "activer"))
                        .build(), player -> {
            editor.setRewardUnique(crate.id(), reward.id(), !reward.solo());
            Guis.click(player);
            reopen(player, crate.id(), reward.id(), back);
        });
    }

    private GuiItem chanceButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.COMPARATOR, Palette.heading(Tr.t("Chance ciblée")), Lore.create()
                .blank()
                .highlight(Tr.t("Chance actuelle"), CrateIcons.chance(crate.chanceOf(reward)))
                .entry(Tr.t("Poids actuel"), reward.weight())
                .blank()
                .text(Tr.t("Saisissez un pourcentage et le poids"))
                .text(Tr.t("est recalculé pour l'atteindre."))
                .blank()
                .warn(Tr.t("Les autres chances bougent en conséquence."))
                .blank()
                .action(Tr.t("Cliquer pour définir"))
                .build(), player -> ChatPrompts.open(player, Tr.t("la chance en pourcent"), input -> {
            java.util.OptionalDouble parsed = Numbers.parsePositive(input);
            if (parsed.isEmpty() || parsed.getAsDouble() >= 100.0D) {
                Guis.deny(player);
                reopen(player, crate.id(), reward.id(), back);
                return;
            }
            editor.setRewardChance(crate.id(), reward.id(), parsed.getAsDouble());
            Guis.success(player);
            reopen(player, crate.id(), reward.id(), back);
        }));
    }

    private GuiItem weightButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.item(Material.GOLD_NUGGET,
                Palette.heading(Tr.t("Poids")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Poids"), reward.weight())
                        .highlight(Tr.t("Chance"), CrateIcons.chance(crate.chanceOf(reward)))
                        .blank()
                        .text(Tr.t("La chance dépend du poids total"))
                        .text(Tr.t("des récompenses de la caisse."))
                        .blank()
                        .click(Tr.t("Clic gauche"), "+1")
                        .click(Tr.t("Shift + clic gauche"), "+10")
                        .denyClick(Tr.t("Clic droit"), "-1")
                        .denyClick(Tr.t("Shift + clic droit"), "-10")
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    int step = click.isShiftClick() ? 10 : 1;
                    int updated = Math.max(1, reward.weight() + (click.isRightClick() ? -step : step));
                    editor.setRewardWeight(crate.id(), reward.id(), updated);
                    Guis.click(player);
                    reopen(player, crate.id(), reward.id(), back);
                });
    }

    private GuiItem rarityButton(Crate crate, CrateReward reward, Runnable back) {
        CrateRarity rarity = reward.rarity();
        return Guis.button(rarity.pane(), Palette.heading(Tr.t("Rareté")), Lore.create()
                .blank()
                .entry(Tr.t("Actuelle"), rarity.colored(rarity.displayName()))
                .state(Tr.t("Annonce par défaut"), rarity.announced(), Tr.t("oui"), Tr.t("non"))
                .blank()
                .text(Tr.t("La rareté pilote la couleur, le son"))
                .text(Tr.t("et les effets du tirage."))
                .blank()
                .action(Tr.t("Cliquer pour changer"))
                .build(), player -> rarityMenu.open(player, Tr.t("Rareté"), rarity, picked -> {
            editor.setRewardRarity(crate.id(), reward.id(), picked);
            Guis.success(player);
            reopen(player, crate.id(), reward.id(), back);
        }, () -> open(player, crate, reward, back)));
    }

    private GuiItem amountButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.PAPER, Palette.heading(Tr.t("Quantité")), Lore.create()
                .blank()
                .entry(Tr.t("Quantité"), reward.amountLabel())
                .blank()
                .text(Tr.t("Saisissez « min-max » pour un"))
                .text(Tr.t("intervalle, ou un seul nombre."))
                .blank()
                .action(Tr.t("Cliquer pour définir"))
                .build(), player -> ChatPrompts.open(player, Tr.t("la quantité"), input -> {
                    int[] range = parseRange(input);
                    if (range == null) {
                        Guis.deny(player);
                        reopen(player, crate.id(), reward.id(), back);
                        return;
                    }
                    editor.setRewardAmounts(crate.id(), reward.id(), range[0], range[1]);
                    reopen(player, crate.id(), reward.id(), back);
                }));
    }

    private GuiItem moneyButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.SUNFLOWER, Palette.heading(Tr.t("Argent")), Lore.create()
                .blank()
                .money(Tr.t("Bonus"), reward.money())
                .blank()
                .text(Tr.t("Somme créditée en plus de l'objet."))
                .text(Tr.t("Saisissez 0 pour aucun bonus."))
                .blank()
                .action(Tr.t("Cliquer pour définir"))
                .build(), player -> ChatPrompts.open(player, Tr.t("le montant"), input -> {
                    OptionalDouble parsed = Numbers.parseAmount(input);
                    if (parsed.isEmpty()) {
                        Guis.deny(player);
                        reopen(player, crate.id(), reward.id(), back);
                        return;
                    }
                    editor.setRewardMoney(crate.id(), reward.id(), Math.max(0.0D, parsed.getAsDouble()));
                    reopen(player, crate.id(), reward.id(), back);
                }));
    }

    private GuiItem commandsButton(Crate crate, CrateReward reward, Runnable back) {
        Lore lore = Lore.create().blank();
        if (reward.commands().isEmpty()) {
            lore.text(Tr.t("Aucune commande configurée."));
        } else {
            int index = 1;
            for (String command : reward.commands()) {
                lore.text(index++ + ". " + preview(command));
            }
        }
        lore.blank()
                .text(Tr.t("Exécutées par la console, où"))
                .text(Tr.t("\\<player> devient le nom du gagnant."))
                .blank()
                .click(Tr.t("Clic gauche"), Tr.t("ajouter une commande"))
                .denyClick(Tr.t("Shift + clic droit"), Tr.t("retirer la dernière"));
        return Guis.item(Material.COMMAND_BLOCK,
                Palette.heading(Tr.t("Commandes")),
                lore.build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click.isShiftClick() && click.isRightClick()) {
                        if (reward.commands().isEmpty()) {
                            Guis.deny(player);
                            return;
                        }
                        editor.removeRewardCommand(crate.id(), reward.id(), reward.commands().size() - 1);
                        Guis.click(player);
                        reopen(player, crate.id(), reward.id(), back);
                        return;
                    }
                    Guis.click(player);
                    ChatPrompts.open(player, Tr.t("la commande"), typed -> {
                        if (typed.isBlank()) {
                            reopen(player, crate.id(), reward.id(), back);
                            return;
                        }
                        editor.addRewardCommand(crate.id(), reward.id(), typed);
                        Guis.success(player);
                        reopen(player, crate.id(), reward.id(), back);
                    });
                });
    }

    private GuiItem giveItemButton(Crate crate, CrateReward reward, Runnable back) {
        Lore lore = Lore.create()
                .blank()
                .state(Tr.t("Objet remis"), reward.giveItem(), Tr.t("oui"), Tr.t("non"))
                .blank()
                .text(Tr.t("Désactivé, l'objet ne sert que"))
                .text(Tr.t("d'affichage pendant l'animation."));
        if (!reward.giveItem() && !reward.hasMoney() && !reward.hasCommands()) {
            lore.blank().deny(Tr.t("Cette récompense ne donne rien"));
        } else {
            lore.blank().warn(Tr.t("Sans remise, prévoyez de l'argent"))
                    .text(Tr.t("ou au moins une commande."));
        }
        return Guis.button(Material.HOPPER, Palette.heading(Tr.t("Remise de l'objet")), lore
                .blank()
                .action(Tr.t("Cliquer pour changer"))
                .build(), player -> {
            editor.setRewardGiveItem(crate.id(), reward.id(), !reward.giveItem());
            reopen(player, crate.id(), reward.id(), back);
        });
    }

    private GuiItem announceButton(Crate crate, CrateReward reward, Runnable back) {
        Boolean announce = reward.announce();
        String setting = announce == null
                ? Tr.t("défaut (") + reward.rarity().displayName() + ")"
                : (announce ? Tr.t("toujours") : Tr.t("jamais"));
        return Guis.button(Material.BELL, Palette.heading(Tr.t("Annonce")), Lore.create()
                .blank()
                .state(Tr.t("Annoncé"), reward.announced(), Tr.t("oui"), Tr.t("non"))
                .entry(Tr.t("Réglage"), setting)
                .blank()
                .text(Tr.t("Un gain annoncé est diffusé"))
                .text(Tr.t("à tout le serveur."))
                .blank()
                .action(Tr.t("Cliquer pour changer le réglage"))
                .build(), player -> {
            Boolean next = announce == null ? Boolean.TRUE : (announce ? Boolean.FALSE : null);
            editor.setRewardAnnounce(crate.id(), reward.id(), next);
            reopen(player, crate.id(), reward.id(), back);
        });
    }

    private GuiItem permissionButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.item(Material.SHIELD,
                Palette.heading(Tr.t("Permission")),
                Lore.create()
                        .blank()
                        .entry(Tr.t("Permission"), reward.permission() == null ? Tr.t("aucune") : reward.permission())
                        .blank()
                        .text(Tr.t("Sans la permission, la récompense"))
                        .text(Tr.t("n'entre pas dans le tirage."))
                        .blank()
                        .click(Tr.t("Clic gauche"), Tr.t("définir une permission"))
                        .denyClick(Tr.t("Shift + clic droit"), Tr.t("retirer la permission"))
                        .build(),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    ClickType click = event.getClick();
                    if (click.isShiftClick() && click.isRightClick()) {
                        editor.setRewardPermission(crate.id(), reward.id(), null);
                        Guis.click(player);
                        reopen(player, crate.id(), reward.id(), back);
                        return;
                    }
                    Guis.click(player);
                    ChatPrompts.open(player, Tr.t("la permission"), typed -> {
                        editor.setRewardPermission(crate.id(), reward.id(), typed.isBlank() ? null : typed);
                        Guis.success(player);
                        reopen(player, crate.id(), reward.id(), back);
                    });
                });
    }

    private GuiItem deleteButton(Crate crate, CrateReward reward, Runnable back) {
        return Guis.button(Material.BARRIER, Palette.DANGER + "<b>Supprimer</b>", Lore.create()
                .blank()
                .text(Tr.t("Retire définitivement cette"))
                .text(Tr.t("récompense de la caisse."))
                .blank()
                .deny(Tr.t("Cliquer pour supprimer"))
                .build(), player -> ConfirmMenu.create(Tr.t("Supprimer une récompense"))
                .subject(reward.display())
                .confirmLabel(Tr.t("Supprimer"))
                .question(Tr.t("Supprimer cette récompense ?"))
                .details(Lore.create()
                        .highlight(Tr.t("Identifiant"), reward.id())
                        .entry(Tr.t("Rareté"), reward.rarity().colored(reward.rarity().displayName()))
                        .entry(Tr.t("Poids"), reward.weight())
                        .highlight(Tr.t("Chance"), CrateIcons.chance(crate.chanceOf(reward)))
                        .blank()
                        .deny(Tr.t("Cette action est irréversible"))
                        .build())
                .onConfirm(viewer -> {
                    editor.deleteReward(crate.id(), reward.id());
                    Messages.send(viewer, "crates.editor-reward-deleted",
                            Mini.value("reward", reward.id()));
                    Guis.success(viewer);
                    back.run();
                })
                .onCancel(viewer -> open(viewer, crate, reward, back))
                .open(player));
    }

    private void reopen(Player player, String crateId, String rewardId, Runnable back) {
        service.crate(crateId).ifPresentOrElse(fresh -> {
            CrateReward updated = fresh.reward(rewardId);
            if (updated == null) {
                back.run();
            } else {
                open(player, fresh, updated, back);
            }
        }, back);
    }

    private static int[] parseRange(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String cleaned = input.trim();
        int dash = cleaned.indexOf('-');
        if (dash < 0) {
            int single = Numbers.parseInt(cleaned, -1);
            return single < 1 ? null : new int[]{single, single};
        }
        int min = Numbers.parseInt(cleaned.substring(0, dash), -1);
        int max = Numbers.parseInt(cleaned.substring(dash + 1), -1);
        return min < 1 || max < min ? null : new int[]{min, max};
    }

    private static String preview(String command) {
        String shortened = command.length() <= COMMAND_PREVIEW
                ? command
                : command.substring(0, COMMAND_PREVIEW - 1) + "…";
        return shortened.replace("<", "\\<");
    }
}
