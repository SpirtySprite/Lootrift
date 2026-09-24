package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class KeySources implements Listener {

    public enum Trigger {
        KILL,
        MINE
    }

    public record Drop(Trigger trigger, String target, double chance, String crate, int amount) {

        public boolean matches(Trigger event, String type) {
            return trigger == event && (target.equals("*") || target.equalsIgnoreCase(type));
        }
    }

    public record Playtime(boolean enabled, int everyMinutes, String crate, int amount) {

        static final Playtime OFF = new Playtime(false, 60, null, 1);
    }

    private final CrateService service;
    private final Map<UUID, Integer> minutes = new ConcurrentHashMap<>();

    private volatile Playtime playtime = Playtime.OFF;
    private volatile List<Drop> drops = List.of();
    private ScheduledTask timer;

    public KeySources(CrateService service) {
        this.service = service;
    }

    public void configure(ConfigurationSection root) {
        playtime = readPlaytime(root == null ? null : root.getConfigurationSection("playtime"));
        drops = readDrops(root == null ? List.of() : root.getMapList("drops"));
        restart();
    }

    static Playtime readPlaytime(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return Playtime.OFF;
        }
        String crate = section.getString("crate");
        if (crate == null || crate.isBlank()) {
            return Playtime.OFF;
        }
        return new Playtime(true, Math.max(1, section.getInt("every-minutes", 60)), crate.toLowerCase(Locale.ROOT),
                Math.max(1, section.getInt("amount", 1)));
    }

    static List<Drop> readDrops(List<Map<?, ?>> raw) {
        List<Drop> parsed = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Object crate = entry.get("crate");
            if (crate == null) {
                continue;
            }
            Trigger trigger = "mine".equalsIgnoreCase(String.valueOf(entry.get("trigger"))) ? Trigger.MINE : Trigger.KILL;
            Object target = entry.get("target");
            double chance = number(entry.get("chance"), 0.0D);
            if (chance <= 0.0D) {
                continue;
            }
            parsed.add(new Drop(trigger, target == null ? "*" : String.valueOf(target).toUpperCase(Locale.ROOT),
                    Math.min(1.0D, chance), String.valueOf(crate).toLowerCase(Locale.ROOT),
                    (int) Math.max(1.0D, number(entry.get("amount"), 1.0D))));
        }
        return List.copyOf(parsed);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void restart() {
        stop();
        if (playtime.enabled()) {
            timer = Scheduling.asyncTimer(this::tickMinute, 60L, 60L);
        }
    }

    private void tickMinute() {
        Playtime current = playtime;
        if (!current.enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            int reached = minutes.merge(player.getUniqueId(), 1, Integer::sum);
            if (reached >= current.everyMinutes()) {
                minutes.put(player.getUniqueId(), 0);
                award(player, current.crate(), current.amount(), "crates.key-playtime");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            roll(killer, Trigger.KILL, event.getEntityType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            roll(event.getPlayer(), Trigger.MINE, event.getBlock().getType().name());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        minutes.remove(event.getPlayer().getUniqueId());
    }

    private void roll(Player player, Trigger trigger, String type) {
        for (Drop drop : drops) {
            if (drop.matches(trigger, type) && ThreadLocalRandom.current().nextDouble() < drop.chance()) {
                award(player, drop.crate(), drop.amount(), "crates.key-found");
            }
        }
    }

    private void award(Player player, String crateId, int amount, String message) {
        service.crate(crateId).ifPresentOrElse(crate -> {
            service.keyRepository().addKeys(player.getUniqueId(), crate.id(), amount);
            Messages.send(player, message, Mini.styled("crate", crate.displayName()),
                    Mini.value("amount", String.valueOf(amount)));
        }, () -> CrateLog.warn(Tr.t("Source de clés : caisse inconnue ") + crateId));
    }

    private static double number(Object raw, double fallback) {
        if (raw instanceof Number value) {
            return value.doubleValue();
        }
        try {
            return raw == null ? fallback : Double.parseDouble(String.valueOf(raw).replace("%", "")) /
                    (String.valueOf(raw).contains("%") ? 100.0D : 1.0D);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
