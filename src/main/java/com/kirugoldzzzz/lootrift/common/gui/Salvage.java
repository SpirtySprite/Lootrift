package com.kirugoldzzzz.lootrift.common.gui;

import com.kirugoldzzzz.lootrift.common.log.LogTopic;
import com.kirugoldzzzz.lootrift.common.log.PluginLog;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Salvage {

    private static final Map<UUID, Set<Runnable>> HANDLERS = new ConcurrentHashMap<>();

    private Salvage() {
    }

    public static Runnable register(Player player, Runnable onShutdown) {
        HANDLERS.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(onShutdown);
        return onShutdown;
    }

    public static void unregister(Player player, Runnable onShutdown) {
        HANDLERS.computeIfPresent(player.getUniqueId(), (ignored, handlers) -> {
            handlers.remove(onShutdown);
            return handlers.isEmpty() ? null : handlers;
        });
    }

    public static void runFor(UUID player) {
        Set<Runnable> handlers = HANDLERS.remove(player);
        if (handlers != null) {
            run(player, List.copyOf(handlers));
        }
    }

    public static void runAll() {
        for (UUID player : List.copyOf(HANDLERS.keySet())) {
            runFor(player);
        }
    }

    private static void run(UUID player, List<Runnable> handlers) {
        for (Runnable handler : handlers) {
            try {
                handler.run();
            } catch (RuntimeException failure) {
                PluginLog.error(LogTopic.MENUS, Tr.t("Restitution d'un menu impossible pour ") + player, failure);
            }
        }
    }
}
