package com.kirugoldzzzz.lootrift;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateLoot {

    private CrateLoot() {
    }

    public static List<CrateReward> eligible(Crate crate, Player player) {
        List<CrateReward> pool = eligible(crate, player, null);
        return pool.isEmpty() ? crate.rewards() : pool;
    }

    public static List<CrateReward> eligible(Crate crate, Player player,
                                             CrateUniqueRepository owned) {
        List<CrateReward> pool = new ArrayList<>(crate.rewards().size());
        for (CrateReward reward : crate.rewards()) {
            if (reward.restricted() && !player.hasPermission(reward.permission())) {
                continue;
            }
            if (reward.solo() && owned != null
                    && owned.has(player.getUniqueId(), crate.id(), reward.id())) {
                continue;
            }
            pool.add(reward);
        }
        return pool;
    }

    public static Draw draw(Crate crate, Player player, int streak) {
        return draw(crate, player, streak, null);
    }

    public static Draw draw(Crate crate, Player player, int streak, CrateUniqueRepository owned) {
        return drawFrom(crate, eligible(crate, player, owned), streak);
    }

    static Draw drawFrom(Crate crate, List<CrateReward> eligible, int streak) {
        List<CrateReward> pool = new ArrayList<>(eligible);
        if (pool.isEmpty()) {
            return new Draw(List.of(), false, false);
        }

        boolean pityDue = crate.pityEnabled() && streak >= crate.pityAfter();
        List<CrateReward> drawn = new ArrayList<>(crate.rolls());
        boolean pityApplied = false;

        for (int roll = 0; roll < crate.rolls() && !pool.isEmpty(); roll++) {
            List<CrateReward> source = pool;
            if (pityDue && roll == 0) {
                List<CrateReward> guaranteed = atLeast(pool, crate.pityFloor());
                if (!guaranteed.isEmpty()) {
                    source = guaranteed;
                    pityApplied = true;
                }
            }
            CrateReward picked = pick(source);
            drawn.add(picked);
            if (picked.solo()) {
                pool.remove(picked);
            }
        }

        return new Draw(List.copyOf(drawn), pityApplied, satisfies(drawn, crate.pityFloor()));
    }

    public record Simulation(int opens, Map<String, Integer> counts, int pityApplied) {
    }

    public static Simulation simulate(Crate crate, int opens) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        crate.rewards().forEach(reward -> counts.put(reward.id(), 0));
        int streak = 0;
        int pity = 0;
        for (int open = 0; open < opens; open++) {
            Draw draw = drawFrom(crate, crate.rewards(), streak);
            for (CrateReward reward : draw.rewards()) {
                counts.merge(reward.id(), 1, Integer::sum);
            }
            if (draw.pity()) {
                pity++;
            }
            streak = draw.satisfied() ? 0 : streak + 1;
        }
        return new Simulation(opens, counts, pity);
    }

    public static CrateReward pick(List<CrateReward> pool) {
        if (pool.size() == 1) {
            return pool.get(0);
        }
        int total = 0;
        for (CrateReward reward : pool) {
            total += reward.weight();
        }
        int target = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (CrateReward reward : pool) {
            target -= reward.weight();
            if (target < 0) {
                return reward;
            }
        }
        return pool.get(pool.size() - 1);
    }

    public static List<CrateReward> reel(List<CrateReward> pool, int length, int winnerIndex,
                                         CrateReward winner) {
        List<CrateReward> reel = new ArrayList<>(Math.max(1, length));
        for (int index = 0; index < length; index++) {
            reel.add(index == winnerIndex ? winner : pick(pool));
        }
        if (winnerIndex >= 0 && winnerIndex < reel.size()) {
            reel.set(winnerIndex, winner);
        }
        return reel;
    }

    private static List<CrateReward> atLeast(List<CrateReward> pool, CrateRarity floor) {
        List<CrateReward> matching = new ArrayList<>();
        for (CrateReward reward : pool) {
            if (reward.rarity().atLeast(floor)) {
                matching.add(reward);
            }
        }
        return matching;
    }

    private static boolean satisfies(List<CrateReward> drawn, CrateRarity floor) {
        for (CrateReward reward : drawn) {
            if (reward.rarity().atLeast(floor)) {
                return true;
            }
        }
        return false;
    }

    public record Draw(List<CrateReward> rewards, boolean pity, boolean satisfied) {

        public boolean isEmpty() {
            return rewards.isEmpty();
        }

        public CrateReward best() {
            CrateReward best = null;
            for (CrateReward reward : rewards) {
                if (best == null || reward.rarity().tier() > best.rarity().tier()) {
                    best = reward;
                }
            }
            return best;
        }
    }
}
