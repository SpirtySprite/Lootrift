package com.kirugoldzzzz.lootrift.common.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class ItemNames {

    private static final Map<Material, String> SLUGS = new EnumMap<>(Material.class);

    static {
        for (Material material : Material.values()) {
            SLUGS.put(material, material.name().toLowerCase(Locale.ROOT));
        }
    }

    private ItemNames() {
    }

    public static String tag(Material material) {
        return "<lang:" + material.translationKey() + ">";
    }

    public static Component of(Material material) {
        return Component.translatable(material.translationKey());
    }

    public static Component of(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return meta.displayName();
        }
        return of(item.getType());
    }

    public static String slug(Material material) {
        String slug = SLUGS.get(material);
        return slug == null ? material.name().toLowerCase(Locale.ROOT) : slug;
    }
}
