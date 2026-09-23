package com.kirugoldzzzz.lootrift;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record CrateReward(String id, ItemStack display, boolean giveItem, int weight,
                          int minAmount, int maxAmount, double money, List<String> commands,
                          CrateRarity rarity, String permission, Boolean announce, boolean unique) {

    public CrateReward {
        weight = Math.max(1, weight);
        minAmount = Math.max(1, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
        money = Math.max(0.0D, money);
        commands = commands == null ? List.of() : List.copyOf(commands);
        rarity = rarity == null ? CrateRarity.COMMUN : rarity;
    }

    public int rollAmount() {
        return minAmount == maxAmount
                ? minAmount
                : ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
    }

    public ItemStack itemFor(int amount) {
        ItemStack copy = display.clone();
        copy.setAmount(Math.max(1, Math.min(amount, copy.getMaxStackSize() * 64)));
        return copy;
    }

    public boolean hasMoney() {
        return money > 0.0D;
    }

    public boolean hasCommands() {
        return !commands.isEmpty();
    }

    public boolean solo() {
        return unique;
    }

    public boolean restricted() {
        return permission != null && !permission.isBlank();
    }

    public boolean announced() {
        return announce == null ? rarity.announced() : announce;
    }

    public boolean fixedAmount() {
        return minAmount == maxAmount;
    }

    public String amountLabel() {
        return fixedAmount() ? String.valueOf(minAmount) : minAmount + " - " + maxAmount;
    }
}
