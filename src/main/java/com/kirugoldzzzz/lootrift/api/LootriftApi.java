package com.kirugoldzzzz.lootrift.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LootriftApi {

    static Optional<LootriftApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(LootriftApi.class));
    }

    List<String> crates();

    boolean exists(String crate);

    int keys(UUID player, String crate);

    Map<String, Integer> keys(UUID player);

    int giveKeys(UUID player, String crate, int amount);

    int takeKeys(UUID player, String crate, int amount);

    void setKeys(UUID player, String crate, int amount);

    void givePhysicalKeys(Player player, String crate, int amount);

    int opened(UUID player, String crate);

    boolean isOpening(Player player);

    boolean open(Player player, String crate);

    void preview(Player player, String crate);
}
