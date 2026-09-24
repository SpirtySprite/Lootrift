package com.kirugoldzzzz.lootrift;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record Crate(String id, String displayName, ItemStack icon, Material block, ItemStack keyItem,
                    CrateAnimationType animation, List<CrateReward> rewards, int rolls,
                    boolean broadcast, String permission, int pityAfter, CrateRarity pityFloor,
                    boolean hologram, List<String> hologramLines, CrateBlockEffects blockEffects,
                    int cooldownSeconds, double price, boolean dailyKey,
                    List<CrateMilestone> milestones, CrateBulkAnimation bulkAnimation,
                    CrateModel model, CrateSeason season) {

    public Crate(String id, String displayName, ItemStack icon, Material block, ItemStack keyItem,
                 CrateAnimationType animation, List<CrateReward> rewards, int rolls,
                 boolean broadcast, String permission, int pityAfter, CrateRarity pityFloor,
                 boolean hologram, List<String> hologramLines, CrateBlockEffects blockEffects,
                 int cooldownSeconds, double price, boolean dailyKey,
                 List<CrateMilestone> milestones, CrateBulkAnimation bulkAnimation, CrateModel model) {
        this(id, displayName, icon, block, keyItem, animation, rewards, rolls, broadcast, permission, pityAfter,
                pityFloor, hologram, hologramLines, blockEffects, cooldownSeconds, price, dailyKey, milestones,
                bulkAnimation, model, CrateSeason.ALWAYS);
    }

    public Crate {
        blockEffects = blockEffects == null ? CrateBlockEffects.defaults() : blockEffects;
        cooldownSeconds = Math.max(0, cooldownSeconds);
        price = Math.max(0.0D, price);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        bulkAnimation = bulkAnimation == null ? CrateBulkAnimation.DOMINO : bulkAnimation;
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        rolls = Math.max(1, Math.min(9, rolls));
        pityAfter = Math.max(0, pityAfter);
        pityFloor = pityFloor == null ? CrateRarity.RARE : pityFloor;
        hologramLines = hologramLines == null ? List.of() : List.copyOf(hologramLines);
        animation = animation == null ? CrateAnimationType.CSGO : animation;
        model = model == null ? CrateModel.none() : model;
        season = season == null ? CrateSeason.ALWAYS : season;
    }

    public boolean isEmpty() {
        return rewards.isEmpty();
    }

    public boolean usable() {
        return !rewards.isEmpty();
    }

    public boolean canOpen(Player player) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public boolean pityEnabled() {
        return pityAfter > 0;
    }

    public boolean throttled() {
        return cooldownSeconds > 0;
    }

    public boolean purchasable() {
        return price > 0.0D;
    }

    public long cooldownMillis() {
        return cooldownSeconds * 1000L;
    }

    public int totalWeight() {
        int total = 0;
        for (CrateReward reward : rewards) {
            total += reward.weight();
        }
        return Math.max(1, total);
    }

    public double chanceOf(CrateReward reward) {
        return reward.weight() * 100.0D / totalWeight();
    }

    public CrateReward reward(String rewardId) {
        for (CrateReward reward : rewards) {
            if (reward.id().equalsIgnoreCase(rewardId)) {
                return reward;
            }
        }
        return null;
    }

    public int countOf(CrateRarity rarity) {
        int count = 0;
        for (CrateReward reward : rewards) {
            if (reward.rarity() == rarity) {
                count++;
            }
        }
        return count;
    }

    public ItemStack keyItem(int amount) {
        ItemStack copy = keyItem.clone();
        copy.setAmount(Math.max(1, Math.min(amount, copy.getMaxStackSize())));
        return copy;
    }
}
