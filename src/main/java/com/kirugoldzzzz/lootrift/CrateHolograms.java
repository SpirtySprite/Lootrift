package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrateHolograms {

    private static final double AMBIENT_RANGE = 24.0D;
    private static final double PERSONAL_RANGE = 12.0D;
    private static final double CLEANUP_RADIUS = 2.0D;
    private static final double PERSONAL_DROP = 0.30D;
    private static final long ANIMATION_PERIOD_TICKS = 2L;
    private static final long PERSONAL_PERIOD_TICKS = 20L;
    private static final double PHASE_STEP = Math.PI / 16.0D;
    private static final float VIEW_RANGE = 0.6F;

    private final CrateService service;

    private final Map<String, Map<UUID, UUID>> personal = new ConcurrentHashMap<>();
    private final Set<String> lively = ConcurrentHashMap.newKeySet();

    private ScheduledTask animation;
    private ScheduledTask personalTask;
    private double phase;

    public CrateHolograms(CrateService service) {
        this.service = service;
    }

    public void refreshAll() {
        for (CratePlacement placement : service.placementRepository().view()) {
            service.crate(placement.crate()).ifPresentOrElse(
                    crate -> {
                        if (crate.hologram() && !crate.hologramLines().isEmpty()) {
                            spawn(placement, crate);
                        } else {
                            remove(placement);
                        }
                    },
                    () -> remove(placement));
        }
    }

    public void chunkLoaded(Chunk chunk, List<Entity> entities) {
        Map<String, TextDisplay> kept = new HashMap<>();
        for (Entity entity : entities) {
            if (!(entity instanceof TextDisplay display) || !display.isVisibleByDefault()) {
                continue;
            }
            String placementId = display.getPersistentDataContainer()
                    .get(CrateKeys.HOLOGRAM_TAG, PersistentDataType.STRING);
            if (placementId == null) {
                continue;
            }
            Crate crate = service.placementRepository().byId(placementId)
                    .flatMap(placement -> service.crate(placement.crate()))
                    .orElse(null);
            if (crate == null || !crate.hologram() || crate.hologramLines().isEmpty()
                    || kept.containsKey(placementId)) {
                display.remove();
                continue;
            }
            kept.put(placementId, display);
            Component text = render(crate.hologramLines());
            if (!text.equals(display.text())) {
                display.text(text);
            }
        }
        for (CratePlacement placement : service.placementRepository().view()) {
            if (kept.containsKey(placement.id()) || !placement.world().equals(chunk.getWorld().getName())
                    || placement.x() >> 4 != chunk.getX() || placement.z() >> 4 != chunk.getZ()) {
                continue;
            }
            service.crate(placement.crate())
                    .filter(crate -> crate.hologram() && !crate.hologramLines().isEmpty())
                    .ifPresent(crate -> spawn(placement, crate));
        }
    }

    public void spawn(CratePlacement placement, Crate crate) {
        Location center = placement.center().orElse(null);
        if (center == null || crate.hologramLines().isEmpty()) {
            return;
        }
        Location anchor = center.clone().add(0.0D, service.hologramHeight(), 0.0D);
        Component text = render(crate.hologramLines());
        String id = placement.id();

        withChunk(anchor, () -> {
            despawnNear(anchor, id);
            TextDisplay display = anchor.getWorld().spawn(anchor, TextDisplay.class,
                    spawned -> decorate(spawned, text, id, service.hologramBackgroundAlpha()));
            rememberHologram(placement, display.getUniqueId());
        });
    }

    public void remove(CratePlacement placement) {
        clearPersonal(placement.id());
        if (!placement.hasHologram()) {
            return;
        }
        Location center = placement.center().orElse(null);
        if (center == null) {
            return;
        }
        Location anchor = center.clone().add(0.0D, service.hologramHeight(), 0.0D);
        String id = placement.id();
        UUID entity = placement.hologram();

        withChunk(anchor, () -> {
            despawnNear(anchor, id);
            Entity known = anchor.getWorld().getEntity(entity);
            if (known != null) {
                known.remove();
            }
            rememberHologram(placement, null);
        });
    }

    public void removeAll() {
        for (String placementId : List.copyOf(personal.keySet())) {
            clearPersonal(placementId);
        }
        for (CratePlacement placement : service.placementRepository().view()) {
            remove(placement);
        }
    }

    public int active() {
        int count = 0;
        for (CratePlacement placement : service.placementRepository().view()) {
            if (placement.hasHologram()) {
                count++;
            }
        }
        return count;
    }

    public int personalDisplays() {
        int count = 0;
        for (Map<UUID, UUID> owned : personal.values()) {
            count += owned.size();
        }
        return count;
    }

    public void startAmbient() {
        stopAmbient();
        animation = Scheduling.globalTimer(this::animate,
                ANIMATION_PERIOD_TICKS, ANIMATION_PERIOD_TICKS);
        personalTask = Scheduling.globalTimer(this::refreshPersonal,
                PERSONAL_PERIOD_TICKS, PERSONAL_PERIOD_TICKS);
    }

    public int livelyPlacements() {
        return lively.size();
    }

    public void stopAmbient() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (personalTask != null) {
            personalTask.cancel();
            personalTask = null;
        }
    }

    public void forget(UUID viewer) {
        for (Map.Entry<String, Map<UUID, UUID>> entry : personal.entrySet()) {
            UUID display = entry.getValue().remove(viewer);
            if (display == null) {
                continue;
            }
            service.placementRepository().byId(entry.getKey())
                    .flatMap(CratePlacement::center)
                    .ifPresent(center -> Scheduling.region(center, () -> despawn(center, display)));
        }
    }

    private void animate() {
        if (!service.effects().particlesEnabled()) {
            return;
        }
        phase += PHASE_STEP;
        double current = phase;
        for (CratePlacement placement : service.placementRepository().view()) {
            if (!lively.contains(placement.id())) {
                continue;
            }
            Crate crate = service.crate(placement.crate()).orElse(null);
            if (crate == null || crate.blockEffects().silent()) {
                continue;
            }
            Location center = loadedCentre(placement);
            if (center == null) {
                continue;
            }
            CrateBlockEffects effects = crate.blockEffects();
            Scheduling.region(center, () -> effects.emit(center, current));
        }
    }

    private void surveyLiveliness(CratePlacement placement, Location center) {
        boolean watched = !center.getWorld().getNearbyPlayers(center, AMBIENT_RANGE).isEmpty();
        if (watched) {
            lively.add(placement.id());
        } else {
            lively.remove(placement.id());
        }
    }

    private void refreshPersonal() {
        int allowed = service.personalHologramViewers();
        for (CratePlacement placement : service.placementRepository().view()) {
            Crate crate = service.crate(placement.crate()).orElse(null);
            Location center = crate == null ? null : loadedCentre(placement);
            if (center == null) {
                lively.remove(placement.id());
                clearPersonal(placement.id());
                continue;
            }
            boolean personalWanted = allowed > 0
                    && crate.hologram() && !crate.hologramLines().isEmpty();
            if (!personalWanted) {
                clearPersonal(placement.id());
            }
            Scheduling.region(center, () -> {
                surveyLiveliness(placement, center);
                if (personalWanted) {
                    updateAround(placement, crate, center, allowed);
                }
            });
        }
    }

    private void updateAround(CratePlacement placement, Crate crate, Location center, int allowed) {
        Location anchor = center.clone().add(0.0D, service.hologramHeight() - PERSONAL_DROP, 0.0D);
        Map<UUID, UUID> owned = personal.computeIfAbsent(placement.id(),
                ignored -> new ConcurrentHashMap<>());

        Collection<Player> nearby = center.getWorld().getNearbyPlayers(center, PERSONAL_RANGE);
        List<Player> served = new ArrayList<>(Math.min(allowed, nearby.size()));
        Set<UUID> keep = new HashSet<>();
        for (Player player : nearby) {
            if (served.size() >= allowed) {
                break;
            }
            served.add(player);
            keep.add(player.getUniqueId());
        }

        for (Map.Entry<UUID, UUID> entry : List.copyOf(owned.entrySet())) {
            if (!keep.contains(entry.getKey())) {
                owned.remove(entry.getKey());
                despawn(anchor, entry.getValue());
            }
        }

        for (Player player : served) {
            Scheduling.entity(player, () -> {
                Component text = statusLine(player, crate);
                Scheduling.region(anchor, () -> apply(player, anchor, placement.id(), owned, text));
            });
        }
    }

    private void apply(Player player, Location anchor, String placementId,
                       Map<UUID, UUID> owned, Component text) {
        UUID existing = owned.get(player.getUniqueId());
        Entity known = existing == null ? null : anchor.getWorld().getEntity(existing);
        if (known instanceof TextDisplay display && display.isValid()) {
            if (!text.equals(display.text())) {
                display.text(text);
            }
            return;
        }
        TextDisplay display = anchor.getWorld().spawn(anchor, TextDisplay.class, spawned -> {
            decorate(spawned, text, placementId, service.hologramBackgroundAlpha());
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        });
        owned.put(player.getUniqueId(), display.getUniqueId());
        Scheduling.reveal(player, display, true);
    }

    private void clearPersonal(String placementId) {
        Map<UUID, UUID> owned = personal.remove(placementId);
        if (owned == null || owned.isEmpty()) {
            return;
        }
        service.placementRepository().byId(placementId)
                .flatMap(CratePlacement::center)
                .ifPresent(center -> Scheduling.region(center, () -> {
                    for (UUID display : owned.values()) {
                        despawn(center, display);
                    }
                }));
    }

    private static void despawn(Location near, UUID display) {
        Entity entity = near.getWorld().getEntity(display);
        if (entity != null) {
            entity.remove();
        }
    }

    private Location loadedCentre(CratePlacement placement) {
        World world = Bukkit.getWorld(placement.world());
        if (world == null || !world.isChunkLoaded(placement.x() >> 4, placement.z() >> 4)) {
            return null;
        }
        return new Location(world, placement.x() + 0.5D,
                placement.y() + 0.5D, placement.z() + 0.5D);
    }

    private Component statusLine(Player player, Crate crate) {
        long waiting = service.cooldownRemaining(player, crate);
        if (waiting > 0L) {
            return Mini.uncached(Card.waitingLine("Disponible dans " + Palette.WARNING + Numbers.duration(waiting)));
        }
        int keys = service.totalKeys(player, crate);
        return Mini.uncached(keys > 0
                ? Card.noteLine(Palette.SUCCESS, Palette.CHECK, "Vos clés : " + Palette.SUCCESS + "<b>" + keys + "</b>")
                : Card.denyLine("Aucune clé"));
    }

    private static void decorate(TextDisplay display, Component text, String placementId,
                                 int backgroundAlpha) {
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(backgroundAlpha, 0, 0, 0));
        display.setViewRange(VIEW_RANGE);
        display.setPersistent(true);
        display.getPersistentDataContainer()
                .set(CrateKeys.HOLOGRAM_TAG, PersistentDataType.STRING, placementId);
    }

    private void rememberHologram(CratePlacement placement, UUID entity) {
        if (service.placementRepository().byId(placement.id()).isEmpty()) {
            return;
        }
        service.placementRepository().save(placement.withHologram(entity));
    }

    private static void withChunk(Location location, Runnable task) {
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Scheduling.region(location, task);
    }

    private static void despawnNear(Location anchor, String placementId) {
        for (TextDisplay display : anchor.getNearbyEntitiesByType(TextDisplay.class,
                CLEANUP_RADIUS, CLEANUP_RADIUS + 1.0D, CLEANUP_RADIUS)) {
            String owner = display.getPersistentDataContainer()
                    .get(CrateKeys.HOLOGRAM_TAG, PersistentDataType.STRING);
            if (placementId.equals(owner) && display.isVisibleByDefault()) {
                display.remove();
            }
        }
    }

    private static Component render(List<String> lines) {
        Component text = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(Mini.label(lines.get(index)));
        }
        return text;
    }
}
