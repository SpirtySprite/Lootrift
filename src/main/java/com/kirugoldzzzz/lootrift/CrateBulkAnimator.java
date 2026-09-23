package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.foliagui.animation.GuiAnimation;
import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.foliagui.scheduler.TaskHandle;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

public final class CrateBulkAnimator {

    private static final int ROWS = 6;
    private static final int COLUMNS = 9;
    private static final int REWARD_ROWS = 5;
    private static final int CAPACITY = REWARD_ROWS * COLUMNS;
    private static final int HOLD_FRAMES = 40;

    private final CrateService service;

    private CrateRewardMenu rewardMenu;

    public CrateBulkAnimator(CrateService service) {
        this.service = service;
    }

    public void bind(CrateRewardMenu menu) {
        this.rewardMenu = menu;
    }

    public void play(Player player, Crate crate, CrateService.Bulk bulk, Runnable onFinish) {
        play(player, crate, bulk, onFinish, null);
    }

    public void play(Player player, Crate crate, CrateService.Bulk bulk, Runnable onFinish,
                     Runnable afterReveal) {
        CrateBulkAnimation pattern = crate.bulkAnimation();
        List<CrateService.Grant> shown = merge(bulk);

        if (pattern.instant() || shown.isEmpty()) {
            summarise(player, crate, bulk, onFinish, afterReveal);
            return;
        }

        List<Integer> occupied = layoutSlots(shown.size());
        Map<Integer, CrateService.Grant> bySlot = new LinkedHashMap<>();
        for (int index = 0; index < shown.size(); index++) {
            bySlot.put(occupied.get(index), shown.get(index));
        }
        List<Integer> reveal = pattern.order(occupied);

        Gui gui = Gui.builder()
                .rows(ROWS)
                .title(Mini.parse(Palette.title(Tr.t("Ouverture groupée"))))
                .create();
        Guis.fillAnimated(gui);
        for (int slot : occupied) {
            gui.setItem(slot, locked(CrateIcons.mystery()));
        }
        gui.setItem(ROWS, 5, header(crate, bulk, shown.size()));
        AtomicBoolean ended = new AtomicBoolean();
        Runnable endSequence = () -> {
            if (afterReveal != null && ended.compareAndSet(false, true)) {
                afterReveal.run();
            }
        };
        gui.setCloseAction(event -> endSequence.run());
        gui.open(player);
        service.effects().start(player);

        int period = Math.max(1, pattern.ticksPerReveal());
        int total = reveal.size();
        TaskHandle[] handle = new TaskHandle[1];
        int[] step = {0};

        handle[0] = GuiAnimation.play(gui, player, period, animated -> {
            int current = step[0]++;
            if (current < total) {
                int slot = reveal.get(current);
                CrateService.Grant granted = bySlot.get(slot);
                gui.updateItem(slot, CrateIcons.winner(granted.reward(), granted.amount()));
                service.effects().step(player, (current + 1) / (float) total);
                if (granted.reward().rarity().atLeast(CrateRarity.EPIQUE)) {
                    service.effects().reveal(player, granted.reward());
                }
                return;
            }
            if (current >= total + HOLD_FRAMES / period) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                summarise(player, crate, bulk, onFinish, endSequence);
            }
        });
    }

    private static List<CrateService.Grant> merge(CrateService.Bulk bulk) {
        Map<CrateReward, Integer> totals = new LinkedHashMap<>();
        for (CrateService.Grant granted : bulk.grants()) {
            totals.merge(granted.reward(), granted.amount(), Integer::sum);
        }
        List<CrateService.Grant> merged = new ArrayList<>(totals.size());
        for (Map.Entry<CrateReward, Integer> entry : totals.entrySet()) {
            merged.add(new CrateService.Grant(entry.getKey(), entry.getValue()));
            if (merged.size() == CAPACITY) {
                break;
            }
        }
        return merged;
    }

    private static List<Integer> layoutSlots(int count) {
        List<Integer> slots = new ArrayList<>(count);
        int remaining = Math.min(count, CAPACITY);
        int rows = Math.max(1, (remaining + COLUMNS - 1) / COLUMNS);
        int placed = 0;
        for (int row = 0; row < rows && placed < remaining; row++) {
            int onThisRow = Math.min(COLUMNS, remaining - placed);
            int first = (COLUMNS - onThisRow) / 2;
            for (int index = 0; index < onThisRow; index++) {
                slots.add(row * COLUMNS + first + index);
                placed++;
            }
        }
        return slots;
    }

    private GuiItem header(Crate crate, CrateService.Bulk bulk, int distinct) {
        CrateRarity best = bulk.best();
        return Guis.display(Material.CHEST, Palette.heading(crate.displayName()), Lore.create()
                .blank()
                .count(Tr.t("Caisses ouvertes"), bulk.opened())
                .count(Tr.t("Récompenses"), bulk.grants().size())
                .count(Tr.t("Objets distincts"), distinct)
                .entry(Tr.t("Meilleur tirage"), best.colored(best.displayName()))
                .blank()
                .text(Tr.t("Les gains sont déjà dans votre inventaire."))
                .build());
    }

    private void summarise(Player player, Crate crate, CrateService.Bulk bulk, Runnable onFinish,
                           Runnable afterReveal) {
        if (afterReveal != null) {
            afterReveal.run();
        }
        if (rewardMenu == null) {
            player.closeInventory();
            return;
        }
        rewardMenu.openBulk(player, crate, bulk, onFinish);
    }

    private static GuiItem locked(ItemStack item) {
        return ItemBuilder.of(item).asGuiItem(event -> event.setCancelled(true));
    }
}
