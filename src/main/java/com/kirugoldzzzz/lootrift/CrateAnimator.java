package com.kirugoldzzzz.lootrift;

import com.foliagui.animation.GuiAnimation;
import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.BaseGui;
import com.foliagui.gui.Gui;
import com.foliagui.item.GuiItem;
import com.foliagui.scheduler.TaskHandle;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.gui.Salvage;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrateAnimator {

    private static final int HOLD_FRAMES = 45;

    private final CrateService service;

    private CrateRewardMenu rewardMenu;

    public CrateAnimator(CrateService service) {
        this.service = service;
    }

    public void bind(CrateRewardMenu menu) {
        this.rewardMenu = menu;
    }

    public void play(Player player, CrateService.Session session, Runnable onFinish) {
        play(player, session, onFinish, null);
    }

    public void play(Player player, CrateService.Session session, Runnable onFinish,
                     Runnable afterReveal) {
        Crate crate = session.crate();
        CrateEffects effects = service.effects();

        if (crate.animation().instant()) {
            session.deliver();
            effects.reveal(player, session.winner());
            summarise(player, session, onFinish, afterReveal);
            return;
        }

        Spin spin = create(crate, session.winner(), CrateLoot.eligible(crate, player));
        Gui gui = Gui.builder()
                .rows(spin.rows())
                .title(Mini.parse(Palette.title(Mini.plain(Mini.label(crate.displayName())))))
                .create();
        gui.setForceOpen(false);
        Guis.fillAnimated(gui);
        spin.layout(gui);

        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean ended = new AtomicBoolean();
        Runnable salvage = () -> {
            if (Scheduling.active()) {
                session.deliver();
            } else {
                session.cancel();
            }
        };
        Runnable settle = () -> {
            if (finished.compareAndSet(false, true)) {
                session.deliver();
                Salvage.unregister(player, salvage);
            }
        };
        Runnable endSequence = () -> {
            if (afterReveal != null && ended.compareAndSet(false, true)) {
                afterReveal.run();
            }
        };
        Salvage.register(player, salvage);
        gui.setCloseAction(event -> {
            settle.run();
            endSequence.run();
        });

        gui.open(player);
        effects.start(player);

        TaskHandle[] handle = new TaskHandle[1];
        int[] frame = {0};
        int[] nextStep = {0};
        handle[0] = GuiAnimation.play(gui, player, 1L, animated -> {
            int current = frame[0]++;
            int total = spin.length();

            if (current < total) {
                advance(player, gui, spin, nextStep, current, effects);
                return;
            }
            if (current == total) {
                advance(player, gui, spin, nextStep, current, effects);
                settle.run();
                spin.reveal(gui, session);
                effects.reveal(player, session.winner());
                effects.title(player, crate, session.winner(),
                        CrateService.rewardName(session.winner()));
                return;
            }
            if (current >= total + HOLD_FRAMES) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                summarise(player, session, onFinish, endSequence);
            }
        });
    }

    private void advance(Player player, BaseGui gui, Spin spin, int[] nextStep, int frame,
                         CrateEffects effects) {
        int steps = spin.steps();
        boolean moved = false;
        while (nextStep[0] < steps && frame >= spin.stampAt(nextStep[0])) {
            spin.step(gui, nextStep[0]);
            nextStep[0]++;
            moved = true;
        }
        if (moved) {
            effects.step(player, nextStep[0] / (float) steps);
        }
    }

    private void summarise(Player player, CrateService.Session session, Runnable onFinish,
                           Runnable afterReveal) {
        if (afterReveal != null) {
            afterReveal.run();
        }
        if (rewardMenu == null) {
            player.closeInventory();
            if (onFinish != null) {
                Scheduling.entityLater(player, onFinish, 2L);
            }
            return;
        }
        rewardMenu.open(player, session, onFinish);
    }

    private static Spin create(Crate crate, CrateReward winner, List<CrateReward> pool) {
        return switch (crate.animation()) {
            case ROULETTE -> new RouletteSpin(winner, pool);
            case CASCADE -> new CascadeSpin(winner, pool);
            case ROUE -> new WheelSpin(winner, pool);
            case PULSE -> new PulseSpin(winner, pool);
            case TOMBOLA -> new TombolaSpin(winner, pool);
            case ECLAIR -> new FlashSpin(winner, pool);
            case HORLOGE -> new ClockSpin(winner, pool);
            case VAGUE -> new WaveSpin(winner, pool);
            case ZOOM -> new ZoomSpin(winner, pool);
            case MOSAIQUE -> new MosaicSpin(winner, pool);
            default -> new CsgoSpin(winner, pool);
        };
    }

    private static GuiItem locked(ItemStack item) {
        return ItemBuilder.of(item).asGuiItem(event -> event.setCancelled(true));
    }

    private static ItemStack glowing(ItemStack item) {
        return ItemBuilder.of(item.clone()).glow(true).build();
    }

    private static int[] schedule(int steps, int maxDelay, double power) {
        int[] stamps = new int[steps];
        int frame = 0;
        for (int index = 0; index < steps; index++) {
            double progress = steps <= 1 ? 1.0D : index / (steps - 1.0D);
            frame += 1 + (int) Math.round(Math.pow(progress, power) * maxDelay);
            stamps[index] = frame;
        }
        return stamps;
    }

    private abstract static class Spin {

        protected final CrateReward winner;
        protected final List<CrateReward> pool;
        private final Map<CrateReward, ItemStack> cards = new HashMap<>();
        private final int[] stamps;

        protected Spin(CrateReward winner, List<CrateReward> pool, int steps, int maxDelay,
                       double power) {
            this.winner = winner;
            this.pool = pool;
            this.stamps = schedule(steps, maxDelay, power);
        }

        abstract int rows();

        abstract void layout(BaseGui gui);

        abstract void step(BaseGui gui, int step);

        abstract void reveal(BaseGui gui, CrateService.Session session);

        final int steps() {
            return stamps.length;
        }

        final int stampAt(int step) {
            return stamps[step];
        }

        final int length() {
            return stamps[stamps.length - 1];
        }

        protected final List<CrateReward> reel(int length, int winnerIndex) {
            return CrateLoot.reel(pool, length, winnerIndex, winner);
        }

        protected final ItemStack card(CrateReward reward) {
            return cards.computeIfAbsent(reward, CrateIcons::card);
        }

        protected final void reserve(BaseGui gui, int slot) {
            gui.setItem(slot, locked(CrateIcons.mystery()));
        }
    }

    private static final class CsgoSpin extends Spin {

        private static final int STEPS = 38;
        private static final int WINDOW = 9;
        private static final int CENTER = 4;

        private final List<CrateReward> reel;

        private CsgoSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 8, 2.4D);
            this.reel = reel(STEPS + WINDOW, STEPS - 1 + CENTER);
        }

        @Override
        int rows() {
            return 3;
        }

        @Override
        void layout(BaseGui gui) {
            gui.setItem(1, 5, locked(CrateIcons.pointer(true)));
            gui.setItem(3, 5, locked(CrateIcons.pointer(false)));
            for (int column = 1; column <= WINDOW; column++) {
                reserve(gui, Guis.slot(2, column));
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            for (int offset = 0; offset < WINDOW; offset++) {
                gui.updateItem(Guis.slot(2, offset + 1), card(reel.get(step + offset)));
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            CrateRarity rarity = session.winner().rarity();
            gui.updateItem(Guis.slot(1, 5), CrateIcons.pane(rarity));
            gui.updateItem(Guis.slot(3, 5), CrateIcons.pane(rarity));
            gui.updateItem(Guis.slot(2, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class RouletteSpin extends Spin {

        private static final int STEPS = 52;
        private static final int ROWS = 5;

        private final List<Integer> ring = new ArrayList<>();
        private final List<CrateReward> content = new ArrayList<>();

        private int highlighted = -1;

        private RouletteSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 5, 2.6D);
            buildRing();
            for (int index = 0; index < ring.size(); index++) {
                content.add(CrateLoot.pick(pool));
            }
            content.set((STEPS - 1) % ring.size(), winner);
        }

        private void buildRing() {
            for (int column = 1; column <= 9; column++) {
                ring.add(Guis.slot(1, column));
            }
            for (int row = 2; row < ROWS; row++) {
                ring.add(Guis.slot(row, 9));
            }
            for (int column = 9; column >= 1; column--) {
                ring.add(Guis.slot(ROWS, column));
            }
            for (int row = ROWS - 1; row >= 2; row--) {
                ring.add(Guis.slot(row, 1));
            }
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int index = 0; index < ring.size(); index++) {
                gui.setItem(ring.get(index), locked(card(content.get(index))));
            }
            reserve(gui, Guis.slot(3, 5));
        }

        @Override
        void step(BaseGui gui, int step) {
            int index = step % ring.size();
            if (highlighted >= 0) {
                gui.updateItem(ring.get(highlighted), card(content.get(highlighted)));
            }
            gui.updateItem(ring.get(index), glowing(card(content.get(index))));
            highlighted = index;
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            gui.updateItem(Guis.slot(3, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int row = 2; row < ROWS; row++) {
                for (int column = 2; column <= 8; column++) {
                    int slot = Guis.slot(row, column);
                    if (slot != Guis.slot(3, 5)) {
                        gui.updateItem(slot, pane);
                    }
                }
            }
        }
    }

    private static final class CascadeSpin extends Spin {

        private static final int STEPS = 58;
        private static final int ROWS = 6;
        private static final int VISIBLE = 5;
        private static final int COLUMNS = 9;
        private static final int STAGGER = 5;

        private final List<List<CrateReward>> columns = new ArrayList<>();
        private final int[] freeze = new int[COLUMNS];

        private CascadeSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 6, 2.2D);
            for (int column = 0; column < COLUMNS; column++) {
                List<CrateReward> strip = new ArrayList<>(STEPS + VISIBLE);
                for (int index = 0; index < STEPS + VISIBLE; index++) {
                    strip.add(CrateLoot.pick(pool));
                }
                columns.add(strip);
                freeze[column] = STEPS - 1 - Math.abs(column - 4) * STAGGER;
            }
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int row = 1; row <= VISIBLE; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    reserve(gui, Guis.slot(row, column));
                }
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            for (int column = 0; column < COLUMNS; column++) {
                if (step > freeze[column]) {
                    continue;
                }
                List<CrateReward> strip = columns.get(column);
                for (int row = 0; row < VISIBLE; row++) {
                    gui.updateItem(Guis.slot(row + 1, column + 1),
                            card(strip.get(step + row)));
                }
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int row = 1; row <= VISIBLE; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    if (row != 3 || column != 5) {
                        gui.updateItem(Guis.slot(row, column), pane);
                    }
                }
            }
            gui.updateItem(Guis.slot(3, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class WheelSpin extends Spin {

        private static final int STEPS = 46;
        private static final int ROWS = 5;

        private static final int POINTER = 1;

        private final int[] ring;
        private final List<CrateReward> content = new ArrayList<>();

        private WheelSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 6, 2.5D);
            this.ring = new int[]{
                    Guis.slot(2, 4), Guis.slot(2, 5), Guis.slot(2, 6),
                    Guis.slot(3, 6), Guis.slot(4, 6), Guis.slot(4, 5),
                    Guis.slot(4, 4), Guis.slot(3, 4)};
            for (int index = 0; index < ring.length; index++) {
                content.add(CrateLoot.pick(pool));
            }
            content.set(Math.floorMod(POINTER + STEPS - 1, ring.length), winner);
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            gui.setItem(1, 5, locked(CrateIcons.pointer(true)));
            for (int slot : ring) {
                reserve(gui, slot);
            }
            reserve(gui, Guis.slot(3, 5));
        }

        @Override
        void step(BaseGui gui, int step) {
            for (int index = 0; index < ring.length; index++) {
                CrateReward reward = content.get(Math.floorMod(index + step, ring.length));
                ItemStack card = card(reward);
                gui.updateItem(ring[index], index == POINTER ? glowing(card) : card);
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            gui.updateItem(Guis.slot(1, 5), CrateIcons.pane(session.winner().rarity()));
            gui.updateItem(Guis.slot(3, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int slot : ring) {
                gui.updateItem(slot, pane);
            }
        }
    }

    private static final class PulseSpin extends Spin {

        private static final int STEPS = 34;

        private final List<CrateReward> reel;
        private final int[] frame = {
                Guis.slot(1, 4), Guis.slot(1, 5), Guis.slot(1, 6),
                Guis.slot(2, 4), Guis.slot(2, 6),
                Guis.slot(3, 4), Guis.slot(3, 5), Guis.slot(3, 6)};

        private PulseSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 9, 2.6D);
            this.reel = reel(STEPS, STEPS - 1);
        }

        @Override
        int rows() {
            return 3;
        }

        @Override
        void layout(BaseGui gui) {
            for (int slot : frame) {
                gui.setItem(slot, locked(CrateIcons.pane(CrateRarity.COMMUN)));
            }
            reserve(gui, Guis.slot(2, 5));
        }

        @Override
        void step(BaseGui gui, int step) {
            CrateReward reward = reel.get(step);
            gui.updateItem(Guis.slot(2, 5), card(reward));
            ItemStack pane = CrateIcons.pane(reward.rarity());
            for (int index = 0; index < frame.length; index++) {
                if ((index + step) % 2 == 0) {
                    gui.updateItem(frame[index], pane);
                }
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int slot : frame) {
                gui.updateItem(slot, pane);
            }
            gui.updateItem(Guis.slot(2, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class TombolaSpin extends Spin {

        private static final int STEPS = 40;
        private static final int WINDOW = 5;
        private static final int CENTER = 2;
        private static final int COLUMN = 5;

        private final List<CrateReward> reel;

        private TombolaSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 8, 2.4D);
            this.reel = reel(STEPS + WINDOW, STEPS - 1 + CENTER);
        }

        @Override
        int rows() {
            return WINDOW;
        }

        @Override
        void layout(BaseGui gui) {
            gui.setItem(CENTER + 1, COLUMN - 1, locked(CrateIcons.pointer(false)));
            gui.setItem(CENTER + 1, COLUMN + 1, locked(CrateIcons.pointer(false)));
            for (int row = 1; row <= WINDOW; row++) {
                reserve(gui, Guis.slot(row, COLUMN));
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            for (int offset = 0; offset < WINDOW; offset++) {
                gui.updateItem(Guis.slot(offset + 1, COLUMN), card(reel.get(step + offset)));
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            CrateRarity rarity = session.winner().rarity();
            gui.updateItem(Guis.slot(CENTER + 1, COLUMN - 1), CrateIcons.pane(rarity));
            gui.updateItem(Guis.slot(CENTER + 1, COLUMN + 1), CrateIcons.pane(rarity));
            gui.updateItem(Guis.slot(CENTER + 1, COLUMN),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class FlashSpin extends Spin {

        private static final int STEPS = 34;
        private static final int COLUMNS = 9;
        private static final int CENTER = 4;

        private final List<CrateReward> reel;

        private FlashSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 9, 2.6D);
            this.reel = reel(STEPS + COLUMNS, STEPS - 1);
        }

        @Override
        int rows() {
            return 3;
        }

        @Override
        void layout(BaseGui gui) {
            for (int column = 1; column <= COLUMNS; column++) {
                reserve(gui, Guis.slot(2, column));
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            int length = reel.size();
            for (int index = 0; index < COLUMNS; index++) {
                int offset = index == CENTER ? 0 : (index + 1) * 5;
                CrateReward reward = reel.get(Math.floorMod(step + offset, length));
                gui.updateItem(Guis.slot(2, index + 1), card(reward));
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int column = 1; column <= COLUMNS; column++) {
                if (column != CENTER + 1) {
                    gui.updateItem(Guis.slot(2, column), pane);
                }
            }
            gui.updateItem(Guis.slot(2, CENTER + 1),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class ClockSpin extends Spin {

        private static final int STEPS = 50;
        private static final int ROWS = 5;

        private final int[] dial = {
                Guis.slot(1, 5), Guis.slot(1, 7), Guis.slot(2, 8), Guis.slot(3, 9),
                Guis.slot(4, 8), Guis.slot(5, 7), Guis.slot(5, 5), Guis.slot(5, 3),
                Guis.slot(4, 2), Guis.slot(3, 1), Guis.slot(2, 2), Guis.slot(1, 3)};
        private final List<CrateReward> content = new ArrayList<>();

        private int lit = -1;

        private ClockSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 6, 2.6D);
            for (int index = 0; index < dial.length; index++) {
                content.add(CrateLoot.pick(pool));
            }
            content.set(Math.floorMod(STEPS - 1, dial.length), winner);
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int index = 0; index < dial.length; index++) {
                gui.setItem(dial[index], locked(card(content.get(index))));
            }
            reserve(gui, Guis.slot(3, 5));
        }

        @Override
        void step(BaseGui gui, int step) {
            int index = Math.floorMod(step, dial.length);
            if (lit >= 0) {
                gui.updateItem(dial[lit], card(content.get(lit)));
            }
            gui.updateItem(dial[index], glowing(card(content.get(index))));
            lit = index;
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int slot : dial) {
                gui.updateItem(slot, pane);
            }
            gui.updateItem(Guis.slot(3, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class WaveSpin extends Spin {

        private static final int STEPS = 48;
        private static final int ROWS = 5;
        private static final int COLUMNS = 9;
        private static final int TARGET_ROW = 2;

        private final List<CrateReward> reel;

        private WaveSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 7, 2.4D);
            this.reel = reel(STEPS + COLUMNS, STEPS - 1 + 4);
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    reserve(gui, Guis.slot(row, column));
                }
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            int active = Math.floorMod(step, ROWS);
            ItemStack dim = CrateIcons.pane(CrateRarity.COMMUN);
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int slot = Guis.slot(row + 1, column + 1);
                    if (row == active) {
                        gui.updateItem(slot, card(reel.get(step + column)));
                    } else {
                        gui.updateItem(slot, dim);
                    }
                }
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    if (row != TARGET_ROW + 1 || column != 5) {
                        gui.updateItem(Guis.slot(row, column), pane);
                    }
                }
            }
            gui.updateItem(Guis.slot(TARGET_ROW + 1, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class ZoomSpin extends Spin {

        private static final int STEPS = 44;
        private static final int ROWS = 5;
        private static final int COLUMNS = 9;
        private static final int MAX_RING = 4;

        private final List<CrateReward> reel;

        private ZoomSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 7, 2.5D);
            this.reel = reel(STEPS + COLUMNS, STEPS - 1);
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    reserve(gui, Guis.slot(row, column));
                }
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            int length = reel.size();
            double progress = steps() <= 1 ? 1.0D : step / (steps() - 1.0D);
            int radius = (int) Math.round(MAX_RING * (1.0D - progress));
            ItemStack dim = CrateIcons.pane(CrateRarity.COMMUN);
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    int ring = Math.max(Math.abs(row - 3), Math.abs(column - 5));
                    int slot = Guis.slot(row, column);
                    if (ring > radius) {
                        gui.updateItem(slot, dim);
                    } else if (ring == 0) {
                        gui.updateItem(slot, card(reel.get(step)));
                    } else {
                        int offset = ring * 7 + column * 3;
                        gui.updateItem(slot, card(reel.get(Math.floorMod(step + offset, length))));
                    }
                }
            }
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    if (row != 3 || column != 5) {
                        gui.updateItem(Guis.slot(row, column), pane);
                    }
                }
            }
            gui.updateItem(Guis.slot(3, 5),
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }

    private static final class MosaicSpin extends Spin {

        private static final int STEPS = 52;
        private static final int ROWS = 6;
        private static final int COLUMNS = 9;
        private static final int STRIDE = 23;

        private final List<CrateReward> reel;
        private final int[] freezeOrder;
        private final int centerSlot = Guis.slot(3, 5);

        private MosaicSpin(CrateReward winner, List<CrateReward> pool) {
            super(winner, pool, STEPS, 6, 2.3D);
            this.reel = reel(STEPS + COLUMNS, STEPS - 1);

            List<Integer> others = new ArrayList<>();
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    int slot = Guis.slot(row, column);
                    if (slot != centerSlot) {
                        others.add(slot);
                    }
                }
            }
            this.freezeOrder = new int[others.size()];
            for (int index = 0; index < freezeOrder.length; index++) {
                freezeOrder[index] = others.get((index * STRIDE) % others.size());
            }
        }

        @Override
        int rows() {
            return ROWS;
        }

        @Override
        void layout(BaseGui gui) {
            for (int row = 1; row <= ROWS; row++) {
                for (int column = 1; column <= COLUMNS; column++) {
                    reserve(gui, Guis.slot(row, column));
                }
            }
        }

        @Override
        void step(BaseGui gui, int step) {
            int length = reel.size();
            int frozen = (int) Math.floor((step + 1.0D) / steps() * freezeOrder.length);
            ItemStack dim = CrateIcons.pane(CrateRarity.COMMUN);
            for (int index = 0; index < freezeOrder.length; index++) {
                int slot = freezeOrder[index];
                if (index < frozen) {
                    gui.updateItem(slot, dim);
                } else {
                    gui.updateItem(slot, card(reel.get(Math.floorMod(step + index * 3, length))));
                }
            }
            gui.updateItem(centerSlot, card(reel.get(step)));
        }

        @Override
        void reveal(BaseGui gui, CrateService.Session session) {
            ItemStack pane = CrateIcons.pane(session.winner().rarity());
            for (int slot : freezeOrder) {
                gui.updateItem(slot, pane);
            }
            gui.updateItem(centerSlot,
                    glowing(CrateIcons.winner(session.winner(), session.winnerAmount())));
        }
    }
}
