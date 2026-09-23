package com.kirugoldzzzz.lootrift;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Objects;

public final class CrateKeys {

    public static final NamespacedKey KEY_TAG = key("crate_key");

    public static final NamespacedKey BLOCK_TAG = key("crate_block");

    public static final NamespacedKey HOLOGRAM_TAG = key("crate_hologram");

    public static final NamespacedKey MODEL_TAG = key("crate_model");

    private CrateKeys() {
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("lootrift:" + value),
                "clé de données invalide: " + value);
    }

    public static ItemStack physicalKey(Crate crate, int amount) {
        return tagged(crate.keyItem(amount), KEY_TAG, crate.id());
    }

    public static ItemStack blockItem(Crate crate, int amount) {
        ItemStack item = new ItemStack(crate.block(), Math.max(1, amount));
        return tagged(item, BLOCK_TAG, crate.id());
    }

    private static ItemStack tagged(ItemStack item, NamespacedKey tag, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING,
                value.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        return item;
    }

    public static String crateOfKey(ItemStack item) {
        return read(item, KEY_TAG);
    }

    public static String crateOfBlock(ItemStack item) {
        return read(item, BLOCK_TAG);
    }

    private static String read(ItemStack item, NamespacedKey tag) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    public static boolean isKeyOf(ItemStack item, Crate crate) {
        String owner = crateOfKey(item);
        return owner != null && owner.equalsIgnoreCase(crate.id());
    }

    public static int countIn(PlayerInventory inventory, Crate crate) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isKeyOf(item, crate)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static boolean consumeOne(Player player, Crate crate) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isKeyOf(item, crate)) {
                continue;
            }
            if (item.getAmount() <= 1) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
            }
            inventory.setStorageContents(contents);
            return true;
        }
        return false;
    }
}
