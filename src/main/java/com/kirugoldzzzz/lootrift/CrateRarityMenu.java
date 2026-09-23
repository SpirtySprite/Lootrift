package com.kirugoldzzzz.lootrift;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class CrateRarityMenu {

    private static final int FIRST_COLUMN = 2;

    public void open(Player player, String title, CrateRarity current,
                     Consumer<CrateRarity> onPick, Runnable back) {
        long start = System.nanoTime();

        Gui gui = Gui.builder()
                .rows(3)
                .title(Mini.parse(Palette.title(title)))
                .create();
        Guis.fill(gui);

        int column = FIRST_COLUMN;
        for (CrateRarity rarity : CrateRarity.values()) {
            gui.setItem(2, column, option(rarity, current, onPick));
            column++;
        }

        gui.setItem(3, 1, Guis.backButton(back));
        gui.setItem(3, 9, Guis.closeButton());
        gui.open(player);
        Guis.opened(start);
    }

    private GuiItem option(CrateRarity rarity, CrateRarity current, Consumer<CrateRarity> onPick) {
        boolean selected = rarity == current;
        return ItemBuilder.of(rarity.pane())
                .name(Mini.label(rarity.heading(rarity.displayName())))
                .loreComponents(Mini.labels(Lore.create()
                        .blank()
                        .entry(Tr.t("Annoncé par défaut"), rarity.announced() ? Tr.t("oui") : Tr.t("non"))
                        .entry(Tr.t("Feu d'artifice"), rarity.firework() ? Tr.t("oui") : Tr.t("non"))
                        .blank()
                        .action(selected ? Tr.t("Palier actuel") : Tr.t("Cliquer pour choisir"))
                        .build()))
                .glow(selected)
                .asGuiItem(event -> {
                    Player player = (Player) event.getWhoClicked();
                    Guis.click(player);
                    onPick.accept(rarity);
                });
    }
}
