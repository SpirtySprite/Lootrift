package com.kirugoldzzzz.lootrift;

import org.bukkit.entity.Player;

import java.util.UUID;

public record CratePull(long at, UUID owner, String ownerName, String crate, String reward,
                        String rewardName, CrateRarity rarity, int amount, double money) {

    public static CratePull of(Player player, Crate crate, CrateReward reward,
                               String rewardName, int amount) {
        return new CratePull(System.currentTimeMillis(), player.getUniqueId(), player.getName(),
                crate.id(), reward.id(), rewardName, reward.rarity(), amount, reward.money());
    }

    public boolean hasMoney() {
        return money > 0.0D;
    }
}
