package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.api.LootriftApi;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LootriftService implements LootriftApi {

    private final CrateService service;
    private final CrateOpener opener;

    LootriftService(CrateService service, CrateOpener opener) {
        this.service = service;
        this.opener = opener;
    }

    @Override
    public List<String> crates() {
        return service.crateIds();
    }

    @Override
    public boolean exists(String crate) {
        return service.crate(crate).isPresent();
    }

    @Override
    public int keys(UUID player, String crate) {
        return service.virtualKeys(player, require(crate));
    }

    @Override
    public Map<String, Integer> keys(UUID player) {
        return Map.copyOf(service.virtualKeysOf(player));
    }

    @Override
    public int giveKeys(UUID player, String crate, int amount) {
        return service.giveVirtualKeys(player, require(crate), positive(amount));
    }

    @Override
    public int takeKeys(UUID player, String crate, int amount) {
        return service.takeVirtualKeys(player, require(crate), positive(amount));
    }

    @Override
    public void setKeys(UUID player, String crate, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        service.setVirtualKeys(player, require(crate), amount);
    }

    @Override
    public void givePhysicalKeys(Player player, String crate, int amount) {
        service.givePhysicalKeys(player, require(crate), positive(amount));
    }

    @Override
    public int opened(UUID player, String crate) {
        return service.keyRepository().opened(player, require(crate).id());
    }

    @Override
    public boolean isOpening(Player player) {
        return service.isOpening(player);
    }

    @Override
    public boolean open(Player player, String crate) {
        return opener.open(player, require(crate), null);
    }

    @Override
    public void preview(Player player, String crate) {
        opener.preview(player, require(crate), null);
    }

    private Crate require(String id) {
        return service.crate(id).orElseThrow(() -> new IllegalArgumentException("unknown crate: " + id));
    }

    private static int positive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        return amount;
    }
}
