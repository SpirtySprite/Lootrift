package com.kirugoldzzzz.lootrift.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CrateRewardEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String crate;
    private final String reward;
    private final String rarity;
    private final int amount;
    private final double money;
    private final ItemStack display;

    public CrateRewardEvent(Player player, String crate, String reward, String rarity, int amount, double money,
                            ItemStack display) {
        super(player, !Bukkit.isPrimaryThread());
        this.crate = crate;
        this.reward = reward;
        this.rarity = rarity;
        this.amount = amount;
        this.money = money;
        this.display = display;
    }

    public String crate() {
        return crate;
    }

    public String reward() {
        return reward;
    }

    public String rarity() {
        return rarity;
    }

    public int amount() {
        return amount;
    }

    public double money() {
        return money;
    }

    public ItemStack display() {
        return display.clone();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
