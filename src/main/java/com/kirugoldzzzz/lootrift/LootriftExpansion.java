package com.kirugoldzzzz.lootrift;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

final class LootriftExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final CrateService service;

    LootriftExpansion(Plugin plugin, CrateService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lootrift";
    }

    @Override
    public @NotNull String getAuthor() {
        return "KiruGoldzZz";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String request = params.toLowerCase(Locale.ROOT);
        if (request.equals("crates")) {
            return String.valueOf(service.crateCount());
        }
        if (player == null) {
            return "";
        }
        if (request.equals("keys_total")) {
            int total = 0;
            for (int amount : service.virtualKeysOf(player.getUniqueId()).values()) {
                total += Math.max(0, amount);
            }
            return String.valueOf(total);
        }
        if (request.startsWith("keys_")) {
            return crate(request.substring(5))
                    .map(crate -> String.valueOf(service.virtualKeys(player.getUniqueId(), crate)))
                    .orElse("0");
        }
        if (request.startsWith("opened_")) {
            return crate(request.substring(7))
                    .map(crate -> String.valueOf(service.keyRepository().opened(player.getUniqueId(), crate.id())))
                    .orElse("0");
        }
        if (request.startsWith("cooldown_") && player.getPlayer() != null) {
            return crate(request.substring(9))
                    .map(crate -> String.valueOf(service.cooldownRemaining(player.getPlayer(), crate) / 1000L))
                    .orElse("0");
        }
        return null;
    }

    private Optional<Crate> crate(String id) {
        return service.crate(id);
    }
}
