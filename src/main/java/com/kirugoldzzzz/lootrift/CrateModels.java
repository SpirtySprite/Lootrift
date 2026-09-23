package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.integration.ModelEngineBridge;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrateModels {

    private static final double SCAN_WIDTH = 2.0D;
    private static final double SCAN_HEIGHT = 4.0D;
    private static final double LERP = 0.2D;
    private static final double SPEED = 1.0D;
    private static final long SEQUENCE_TIMEOUT_MILLIS = 60_000L;
    private static final long RECLICK_GUARD_MILLIS = 250L;
    private static final long TICK_MILLIS = 50L;
    private static final long PERSONAL_CLEANUP_TICKS = 25L;
    private static final char PERSONAL_MARK = '|';

    private final CrateService service;

    private final Map<String, Map<UUID, Long>> openers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();

    public CrateModels(CrateService service) {
        this.service = service;
    }

    public boolean available() {
        return ModelEngineBridge.available();
    }

    public boolean contains(Entity entity) {
        return entity instanceof ArmorStand stand && ownerOf(stand) != null;
    }

    public String unavailableReason() {
        return ModelEngineBridge.failureReason();
    }

    public int configured() {
        int total = 0;
        for (Crate crate : service.crates()) {
            if (crate.model().enabled()) {
                total++;
            }
        }
        return total;
    }

    public int placed() {
        int total = 0;
        for (CratePlacement placement : service.placementRepository().view()) {
            if (service.crate(placement.crate()).map(crate -> crate.model().enabled()).orElse(false)) {
                total++;
            }
        }
        return total;
    }

    public int opening() {
        long now = System.currentTimeMillis();
        int total = 0;
        for (Map<UUID, Long> active : openers.values()) {
            for (long expiry : active.values()) {
                if (expiry > now) {
                    total++;
                }
            }
        }
        return total;
    }

    public void refreshAll() {
        for (CratePlacement placement : service.placementRepository().view()) {
            service.crate(placement.crate()).ifPresentOrElse(
                    crate -> {
                        if (crate.model().enabled()) {
                            spawn(placement, crate);
                        } else {
                            remove(placement);
                        }
                    },
                    () -> remove(placement));
        }
    }

    public void chunkLoaded(Chunk chunk, List<Entity> entities) {
        Set<String> kept = new HashSet<>();
        for (Entity entity : entities) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }
            String owner = ownerOf(stand);
            if (owner == null) {
                continue;
            }
            boolean wanted = owner.indexOf(PERSONAL_MARK) < 0 && !kept.contains(owner)
                    && service.placementRepository().byId(owner)
                    .flatMap(placement -> service.crate(placement.crate()))
                    .map(crate -> crate.model().enabled())
                    .orElse(false);
            if (wanted) {
                kept.add(owner);
            } else {
                ModelEngineBridge.detach(stand);
                stand.remove();
            }
        }
        if (!ModelEngineBridge.available()) {
            return;
        }
        for (CratePlacement placement : service.placementRepository().view()) {
            if (kept.contains(placement.id()) || !placement.world().equals(chunk.getWorld().getName())
                    || placement.x() >> 4 != chunk.getX() || placement.z() >> 4 != chunk.getZ()) {
                continue;
            }
            service.crate(placement.crate())
                    .filter(crate -> crate.model().enabled())
                    .ifPresent(crate -> spawn(placement, crate));
        }
    }

    public void rotate(CratePlacement placement, Crate crate) {
        spawn(placement, crate);
    }

    public void spawn(CratePlacement placement, Crate crate) {
        CrateModel model = crate.model();
        if (!model.enabled() || !ModelEngineBridge.available()) {
            return;
        }
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        Location anchor = spot.clone().add(0.0D, model.offset(), 0.0D);
        String id = placement.id();

        withChunk(spot, () -> {
            despawnAll(spot, id);
            ArmorStand stand = anchor.getWorld().spawn(anchor, ArmorStand.class,
                    spawned -> prepare(spawned, id, true));
            if (!ModelEngineBridge.attach(stand, model.blueprint(), model.scale())) {
                stand.remove();
                return;
            }
            ModelEngineBridge.face(stand, anchor.getYaw());
            animate(stand, model.idle());
        });
    }

    public void remove(CratePlacement placement) {
        openers.remove(placement.id());
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        String id = placement.id();
        withChunk(spot, () -> despawnAll(spot, id));
    }

    public void removeAll() {
        openers.clear();
        pending.clear();
        for (CratePlacement placement : service.placementRepository().view()) {
            remove(placement);
        }
    }

    public boolean openWith(CratePlacement placement, Crate crate, Player player, Runnable openGui) {
        CrateModel model = crate.model();
        if (!model.enabled() || !ModelEngineBridge.available()
                || placement.base().isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long guarded = pending.get(player.getUniqueId());
        if (guarded != null && guarded > now) {
            return true;
        }
        pending.put(player.getUniqueId(),
                now + model.openDelayTicks() * TICK_MILLIS + RECLICK_GUARD_MILLIS);

        Map<UUID, Long> active = openers.computeIfAbsent(placement.id(),
                id -> new ConcurrentHashMap<>());
        active.values().removeIf(expiry -> expiry <= now);
        boolean first = active.isEmpty();
        active.put(player.getUniqueId(), now + SEQUENCE_TIMEOUT_MILLIS);

        if (model.personal()) {
            openPersonal(placement, model, player);
        } else if (first) {
            play(placement, model.opening(), model.closing());
        }

        if (model.openDelayTicks() <= 0L) {
            openGui.run();
        } else {
            Scheduling.entityLater(player, openGui, model.openDelayTicks());
        }
        return true;
    }

    public void finish(CratePlacement placement, Crate crate, Player player) {
        Map<UUID, Long> active = openers.get(placement.id());
        if (active == null || !active.containsKey(player.getUniqueId())) {
            return;
        }
        Scheduling.entityLater(player, () -> {
            player.closeInventory();
            release(placement.id(), player.getUniqueId());
        }, Math.max(1L, crate.model().closeDelayTicks()));
    }

    public void abort(CratePlacement placement, Player player) {
        pending.remove(player.getUniqueId());
        release(placement.id(), player.getUniqueId());
    }

    public void forget(UUID playerId) {
        pending.remove(playerId);
        for (String placementId : Set.copyOf(openers.keySet())) {
            release(placementId, playerId);
        }
    }

    private void release(String placementId, UUID playerId) {
        Map<UUID, Long> active = openers.get(placementId);
        if (active == null || active.remove(playerId) == null) {
            return;
        }
        long now = System.currentTimeMillis();
        active.values().removeIf(expiry -> expiry <= now);
        boolean last = active.isEmpty();
        if (last) {
            openers.remove(placementId);
        }
        placementOf(placementId).ifPresent(placement ->
                service.crate(placement.crate()).ifPresent(crate -> {
                    CrateModel model = crate.model();
                    if (model.personal()) {
                        closePersonal(placement, model, playerId);
                    } else if (last) {
                        closeShared(placement, model);
                    }
                }));
    }

    private void closeShared(CratePlacement placement, CrateModel model) {
        if (model.closing() == null) {
            play(placement, model.idle(), model.opening());
            return;
        }
        play(placement, model.closing(), model.opening());
        playLater(placement, model.idle(), CrateModel.IDLE_RETURN_TICKS,
                model.opening(), model.closing());
    }

    private void openPersonal(CratePlacement placement, CrateModel model, Player player) {
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        Location anchor = spot.clone().add(0.0D, model.offset(), 0.0D);
        String shared = placement.id();
        String tag = personalTag(shared, player.getUniqueId());

        withChunk(spot, () -> {
            despawnTagged(spot, tag);
            ArmorStand mine = anchor.getWorld().spawn(anchor, ArmorStand.class,
                    spawned -> prepare(spawned, tag, false));
            if (!ModelEngineBridge.attach(mine, model.blueprint(), model.scale())) {
                mine.remove();
                return;
            }
            ModelEngineBridge.face(mine, anchor.getYaw());
            ArmorStand common = find(spot, shared);
            Scheduling.reveal(player, mine, true);
            if (common != null) {
                Scheduling.reveal(player, common, false);
            }
            animate(mine, model.opening(), model.closing());
        });
    }

    private void closePersonal(CratePlacement placement, CrateModel model, UUID playerId) {
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        String shared = placement.id();
        String tag = personalTag(shared, playerId);

        withChunk(spot, () -> {
            ArmorStand mine = find(spot, tag);
            if (mine == null) {
                reveal(spot, shared, playerId);
                return;
            }
            animate(mine, model.closing() == null ? model.idle() : model.closing(), model.opening());
            Scheduling.entityLater(mine, () -> {
                ModelEngineBridge.detach(mine);
                mine.remove();
                reveal(spot, shared, playerId);
            }, PERSONAL_CLEANUP_TICKS);
        });
    }

    private static void reveal(Location spot, String placementId, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        ArmorStand common = find(spot, placementId);
        if (common == null) {
            return;
        }
        Scheduling.reveal(player, common, true);
    }

    private static String personalTag(String placementId, UUID playerId) {
        return placementId + PERSONAL_MARK + playerId;
    }

    private Optional<CratePlacement> placementOf(String placementId) {
        for (CratePlacement placement : service.placementRepository().view()) {
            if (placement.id().equals(placementId)) {
                return Optional.of(placement);
            }
        }
        return Optional.empty();
    }

    private void play(CratePlacement placement, String animation, String... stopFirst) {
        if (animation == null || !ModelEngineBridge.available()) {
            return;
        }
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        String id = placement.id();
        withChunk(spot, () -> animate(find(spot, id), animation, stopFirst));
    }

    private void playLater(CratePlacement placement, String animation, long delayTicks,
                           String... stopFirst) {
        if (animation == null || !ModelEngineBridge.available()) {
            return;
        }
        Location spot = placement.base().orElse(null);
        if (spot == null) {
            return;
        }
        String id = placement.id();
        withChunk(spot, () -> {
            ArmorStand stand = find(spot, id);
            if (stand != null) {
                Scheduling.entityLater(stand, () -> animate(stand, animation, stopFirst), delayTicks);
            }
        });
    }

    private static void animate(ArmorStand base, String animation, String... stopFirst) {
        if (base == null) {
            return;
        }
        for (String stop : stopFirst) {
            ModelEngineBridge.stop(base, stop);
        }
        if (animation != null) {
            ModelEngineBridge.play(base, animation, LERP, LERP, SPEED, true);
        }
    }

    private static void prepare(ArmorStand stand, String tag, boolean shared) {
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(shared);
        stand.setCollidable(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setAI(false);
        if (!shared) {
            stand.setVisibleByDefault(false);
        }
        stand.getPersistentDataContainer().set(CrateKeys.MODEL_TAG,
                PersistentDataType.STRING, tag);
    }

    private static ArmorStand find(Location spot, String tag) {
        for (ArmorStand stand : spot.getNearbyEntitiesByType(ArmorStand.class,
                SCAN_WIDTH, SCAN_HEIGHT, SCAN_WIDTH)) {
            if (tag.equals(ownerOf(stand))) {
                return stand;
            }
        }
        return null;
    }

    private static void despawnTagged(Location spot, String tag) {
        for (ArmorStand stand : spot.getNearbyEntitiesByType(ArmorStand.class,
                SCAN_WIDTH, SCAN_HEIGHT, SCAN_WIDTH)) {
            if (tag.equals(ownerOf(stand))) {
                ModelEngineBridge.detach(stand);
                stand.remove();
            }
        }
    }

    private static void despawnAll(Location spot, String placementId) {
        String prefix = placementId + PERSONAL_MARK;
        for (ArmorStand stand : spot.getNearbyEntitiesByType(ArmorStand.class,
                SCAN_WIDTH, SCAN_HEIGHT, SCAN_WIDTH)) {
            String owner = ownerOf(stand);
            if (owner == null || (!owner.equals(placementId) && !owner.startsWith(prefix))) {
                continue;
            }
            ModelEngineBridge.detach(stand);
            stand.remove();
        }
    }

    private static String ownerOf(ArmorStand stand) {
        return stand.getPersistentDataContainer()
                .get(CrateKeys.MODEL_TAG, PersistentDataType.STRING);
    }

    private static void withChunk(Location location, Runnable task) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Scheduling.region(location, task);
    }
}
