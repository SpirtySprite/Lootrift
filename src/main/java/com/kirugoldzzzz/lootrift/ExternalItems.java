package com.kirugoldzzzz.lootrift;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ExternalItems {

    public static final List<String> PROVIDERS = List.of("itemsadder", "nexo", "oraxen", "mmoitems");

    private ExternalItems() {
    }

    public static boolean isReference(String raw) {
        if (raw == null) {
            return false;
        }
        int colon = raw.indexOf(':');
        return colon > 0 && PROVIDERS.contains(raw.substring(0, colon).toLowerCase(Locale.ROOT));
    }

    public static Optional<ItemStack> resolve(String raw) {
        if (!isReference(raw)) {
            return Optional.empty();
        }
        int colon = raw.indexOf(':');
        String provider = raw.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = raw.substring(colon + 1).strip();
        try {
            ItemStack item = switch (provider) {
                case "itemsadder" -> itemsAdder(id);
                case "nexo" -> builder("com.nexomc.nexo.api.NexoItems", "itemFromId", id);
                case "oraxen" -> builder("io.th0rgal.oraxen.api.OraxenItems", "getItemById", id);
                case "mmoitems" -> mmoItems(id);
                default -> null;
            };
            return Optional.ofNullable(item).filter(stack -> !stack.getType().isAir()).map(ItemStack::clone);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    public static boolean available(String raw) {
        if (!isReference(raw)) {
            return false;
        }
        String provider = raw.substring(0, raw.indexOf(':')).toLowerCase(Locale.ROOT);
        String plugin = switch (provider) {
            case "itemsadder" -> "ItemsAdder";
            case "nexo" -> "Nexo";
            case "oraxen" -> "Oraxen";
            default -> "MMOItems";
        };
        return Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private static ItemStack itemsAdder(String id) throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack");
        Object stack = type.getMethod("getInstance", String.class).invoke(null, id);
        return stack == null ? null : (ItemStack) type.getMethod("getItemStack").invoke(stack);
    }

    private static ItemStack builder(String owner, String lookup, String id) throws ReflectiveOperationException {
        Object builder = Class.forName(owner).getMethod(lookup, String.class).invoke(null, id);
        if (builder == null) {
            return null;
        }
        Method build = builder.getClass().getMethod("build");
        return (ItemStack) build.invoke(builder);
    }

    private static ItemStack mmoItems(String id) throws ReflectiveOperationException {
        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        Class<?> main = Class.forName("net.Indyuri.mmoitems.MMOItems");
        Object plugin = main.getField("plugin").get(null);
        Object types = main.getMethod("getTypes").invoke(plugin);
        Object type = types.getClass().getMethod("get", String.class).invoke(types, parts[0].toUpperCase(Locale.ROOT));
        if (type == null) {
            return null;
        }
        Class<?> typeClass = Class.forName("net.Indyuri.mmoitems.api.Type");
        return (ItemStack) main.getMethod("getItem", typeClass, String.class)
                .invoke(plugin, type, parts[1].toUpperCase(Locale.ROOT));
    }
}
