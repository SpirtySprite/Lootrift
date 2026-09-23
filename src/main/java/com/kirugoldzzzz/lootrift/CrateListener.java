package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.item.ItemReturn;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Optional;

public final class CrateListener implements Listener {

    private static final String ADMIN_PERMISSION = "lootrift.admin.crates";

    private final CrateService service;
    private final CrateOpener opener;
    private final CrateHolograms holograms;
    private final CrateModels models;

    public CrateListener(CrateService service, CrateOpener opener, CrateHolograms holograms,
                         CrateModels models) {
        this.service = service;
        this.opener = opener;
        this.holograms = holograms;
        this.models = models;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onKeyPlace(BlockPlaceEvent event) {
        if (CrateKeys.crateOfKey(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String crateId = CrateKeys.crateOfBlock(event.getItemInHand());
        if (crateId == null) {
            return;
        }
        Optional<Crate> found = service.crate(crateId);
        if (found.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            service.effects().deny(player);
            Messages.send(player, "general.no-permission");
            return;
        }
        Crate crate = found.get();
        Block placed = event.getBlockPlaced();
        CratePlacement placement = CratePlacement.of(crate, placed,
                player.getLocation().getYaw() + 180.0F);
        service.placementRepository().save(placement);
        service.effects().placement(placed.getLocation().add(0.5D, 0.5D, 0.5D), true);
        Messages.send(player, "crates.placed", Mini.styled("crate", crate.displayName()));
        CrateLog.player(CrateLog.PLACED, player, crate, 0.0D, placement.describe());
        if (crate.hologram() && !crate.hologramLines().isEmpty()) {
            holograms.spawn(placement, crate);
        }
        models.spawn(placement, crate);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<CratePlacement> found = service.placementRepository().at(block);
        if (found.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            service.effects().deny(player);
            Messages.send(player, "general.no-permission");
            return;
        }
        CratePlacement placement = found.get();
        Optional<Crate> crate = service.crate(placement.crate());
        holograms.remove(placement);
        models.remove(placement);
        service.placementRepository().remove(placement.id());
        service.effects().placement(block.getLocation().add(0.5D, 0.5D, 0.5D), false);
        Messages.send(player, "crates.removed", Mini.styled("crate",
                crate.map(Crate::displayName).orElse(placement.crate())));
        CrateLog.player(CrateLog.REMOVED, player, crate.orElse(null), 0.0D, placement.describe());
        crate.ifPresent(value -> {
            event.setDropItems(false);
            ItemReturn.give(player, List.of(CrateKeys.blockItem(value, 1)));
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (action == Action.LEFT_CLICK_BLOCK && block != null
                && service.placementRepository().contains(block)) {
            if (player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
            if (hand == EquipmentSlot.HAND) {
                preview(player, block);
            }
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && block != null) {
            Optional<CratePlacement> placement = service.placementRepository().at(block);
            if (placement.isPresent()) {
                event.setCancelled(true);
                if (hand == EquipmentSlot.HAND) {
                    useBlock(player, placement.get());
                }
                return;
            }
        }
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        String crateId = CrateKeys.crateOfKey(event.getItem());
        if (crateId == null) {
            return;
        }
        Optional<Crate> crate = service.crate(crateId);
        if (crate.isEmpty()) {
            service.effects().deny(player);
            Messages.send(player, "crates.unknown", Mini.value("crate", crateId));
            return;
        }
        event.setCancelled(true);
        trigger(player, crate.get());
    }

    private void useBlock(Player player, CratePlacement placement) {
        Optional<Crate> found = service.crate(placement.crate());
        if (found.isEmpty()) {
            service.effects().deny(player);
            Messages.send(player, "crates.not-a-crate");
            return;
        }
        Crate crate = found.get();
        if (service.check(player, crate) != CrateService.Refusal.NONE) {
            trigger(player, crate, null);
            return;
        }
        Runnable afterReveal = () -> models.finish(placement, crate, player);
        Runnable openGui = () -> {
            if (!trigger(player, crate, afterReveal)) {
                models.abort(placement, player);
            }
        };
        if (!models.openWith(placement, crate, player, openGui)) {
            trigger(player, crate, null);
        }
    }

    private void trigger(Player player, Crate crate) {
        trigger(player, crate, null);
    }

    private boolean trigger(Player player, Crate crate, Runnable afterReveal) {
        if (player.isSneaking()) {
            return opener.openBulk(player, crate, CrateService.BULK_LIMIT, null, afterReveal);
        }
        return opener.open(player, crate, null, afterReveal);
    }

    private void preview(Player player, Block block) {
        service.placementRepository().at(block)
                .flatMap(placement -> service.crate(placement.crate()))
                .ifPresentOrElse(
                        crate -> opener.preview(player, crate, null),
                        () -> {
                            service.effects().deny(player);
                            Messages.send(player, "crates.not-a-crate");
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        holograms.chunkLoaded(event.getChunk(), event.getEntities());
        models.chunkLoaded(event.getChunk(), event.getEntities());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.release(event.getPlayer().getUniqueId());
        holograms.forget(event.getPlayer().getUniqueId());
        models.forget(event.getPlayer().getUniqueId());
    }
}
