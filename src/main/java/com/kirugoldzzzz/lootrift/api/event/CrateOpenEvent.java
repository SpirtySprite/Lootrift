package com.kirugoldzzzz.lootrift.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class CrateOpenEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String crate;
    private final int openings;
    private boolean cancelled;

    public CrateOpenEvent(Player player, String crate, int openings) {
        super(player, !Bukkit.isPrimaryThread());
        this.crate = crate;
        this.openings = openings;
    }

    public String crate() {
        return crate;
    }

    public int openings() {
        return openings;
    }

    public boolean bulk() {
        return openings > 1;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
