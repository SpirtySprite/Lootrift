package com.kirugoldzzzz.lootrift.importer;

import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CrazyCratesSource implements CrateSource {

    private static final Map<String, String> ANIMATIONS = Map.ofEntries(
            Map.entry("csgo", "csgo"),
            Map.entry("roulette", "roulette"),
            Map.entry("casino", "roulette"),
            Map.entry("wheel", "roue"),
            Map.entry("wonder", "mosaique"),
            Map.entry("cosmic", "tombola"),
            Map.entry("war", "eclair"),
            Map.entry("quickcrate", "instant"),
            Map.entry("firecracker", "instant"),
            Map.entry("quadcrate", "instant"));

    @Override
    public String id() {
        return "crazycrates";
    }

    @Override
    public String plugin() {
        return "CrazyCrates";
    }

    @Override
    public Imported.Result read(File pluginFolder) {
        Imported.Result result = new Imported.Result(plugin());
        File[] files = new File(pluginFolder, "crates").listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            result.warn(Tr.t("Dossier des caisses introuvable : ") + new File(pluginFolder, "crates"));
            return result;
        }
        Arrays.sort(files);
        Map<String, String> ids = new LinkedHashMap<>();
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("Crate");
            if (root == null) {
                result.warn(file.getName() + Tr.t(" : section Crate absente, fichier ignoré"));
                continue;
            }
            String type = root.getString("CrateType", "CSGO").toLowerCase(Locale.ROOT);
            if (type.equals("menu")) {
                continue;
            }
            Imported.Crate crate = crate(CrateSource.slug(name), root, type, result);
            ids.put(name, crate.id());
            result.add(crate);
        }
        readKeys(new File(pluginFolder, "data.yml"), ids, result);
        return result;
    }

    private static Imported.Crate crate(String id, ConfigurationSection root, String type, Imported.Result result) {
        String name = ImportText.mini(root.getString("Name", root.getString("CrateName", id)));
        Imported.Item icon = spec(root.getString("Item", "chest"), 1, name, root.getStringList("Lore"), List.of(),
                false);
        ConfigurationSection physical = root.getConfigurationSection("PhysicalKey");
        Imported.Item key = physical == null ? null : spec(physical.getString("Item", "tripwire_hook"), 1,
                ImportText.mini(physical.getString("Name")), physical.getStringList("Lore"), List.of(),
                !"none".equalsIgnoreCase(physical.getString("Glowing", "none")) || physical.getBoolean("Glowing"));
        List<Imported.Reward> rewards = new ArrayList<>();
        ConfigurationSection prizes = root.getConfigurationSection("Prizes");
        if (prizes != null) {
            for (String prizeId : prizes.getKeys(false)) {
                ConfigurationSection prize = prizes.getConfigurationSection(prizeId);
                if (prize != null) {
                    Imported.Reward reward = reward(id, prizeId, prize, result);
                    if (reward != null) {
                        rewards.add(reward);
                    }
                }
            }
        }
        if (rewards.isEmpty()) {
            result.warn(id + Tr.t(" : aucune récompense importée"));
        }
        return new Imported.Crate(id, name, ANIMATIONS.getOrDefault(type, "csgo"), icon, key, rewards);
    }

    static Imported.Reward reward(String crate, String prizeId, ConfigurationSection prize, Imported.Result result) {
        double weight = prize.contains("Weight") ? prize.getDouble("Weight")
                : prize.getDouble("Chance", 0.0D) / Math.max(1.0D, prize.getDouble("MaxRange", 100.0D)) * 100.0D;
        if (weight <= 0.0D) {
            result.warn(crate + "." + prizeId + Tr.t(" : poids nul, récompense ignorée"));
            return null;
        }
        List<Imported.Item> given = items(prize);
        if (given.size() > 1) {
            result.warn(crate + "." + prizeId + Tr.t(" : plusieurs objets, seul le premier est gardé"));
        }
        List<String> commands = new ArrayList<>();
        for (String command : prize.getStringList("Commands")) {
            commands.add(command.replace("%player%", "<player>").replace("%Player%", "<player>"));
        }
        boolean giveItem = !given.isEmpty();
        Imported.Item item = giveItem ? given.getFirst()
                : spec(prize.getString("DisplayItem", "paper"), prize.getInt("DisplayAmount", 1),
                ImportText.mini(prize.getString("DisplayName")), prize.getStringList("DisplayLore"),
                prize.getStringList("DisplayEnchantments"), false);
        if (!giveItem && commands.isEmpty()) {
            result.warn(crate + "." + prizeId + Tr.t(" : ni objet ni commande, récompense ignorée"));
            return null;
        }
        return new Imported.Reward(CrateSource.slug(prizeId), item, giveItem, weight, null, commands,
                prize.getBoolean("Firework", false));
    }

    private static List<Imported.Item> items(ConfigurationSection prize) {
        List<Imported.Item> items = new ArrayList<>();
        ConfigurationSection section = prize.getConfigurationSection("Items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                List<String> enchantments = new ArrayList<>();
                ConfigurationSection enchants = entry.getConfigurationSection("enchantments");
                if (enchants != null) {
                    for (String enchant : enchants.getKeys(false)) {
                        enchantments.add(enchant + ":" + enchants.getInt(enchant, 1));
                    }
                }
                items.add(spec(entry.getString("material", "stone"), entry.getInt("amount", 1),
                        ImportText.mini(entry.getString("name")), entry.getStringList("lore"), enchantments, false));
            }
            return items;
        }
        for (String line : prize.getStringList("Items")) {
            items.add(legacy(line));
        }
        return items;
    }

    static Imported.Item legacy(String line) {
        String material = "stone";
        int amount = 1;
        String name = null;
        List<String> lore = new ArrayList<>();
        List<String> enchantments = new ArrayList<>();
        boolean glow = false;
        for (String part : line.split(", ")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(colon + 1).trim();
            switch (key) {
                case "item" -> material = value;
                case "amount" -> amount = parse(value);
                case "name" -> name = ImportText.mini(value);
                case "lore" -> lore.addAll(Arrays.asList(value.split(",")));
                case "glowing" -> glow = Boolean.parseBoolean(value);
                case "unbreakable", "player", "hide-flags", "custom-model-data", "trim-material", "trim-pattern" -> {
                }
                default -> enchantments.add(key + ":" + parse(value));
            }
        }
        return spec(material, amount, name, lore, enchantments, glow);
    }

    static Imported.Item spec(String material, int amount, String name, List<String> lore, List<String> enchantments,
                              boolean glow) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("material", material.contains(":") ? material.substring(material.indexOf(':') + 1) : material);
        if (name != null && !name.isBlank()) {
            spec.put("name", name);
        }
        if (!lore.isEmpty()) {
            spec.put("lore", ImportText.mini(lore));
        }
        if (!enchantments.isEmpty()) {
            spec.put("enchantments", List.copyOf(enchantments));
        }
        if (glow) {
            spec.put("glow", true);
        }
        return Imported.Item.spec(spec, amount);
    }

    private static void readKeys(File data, Map<String, String> ids, Imported.Result result) {
        if (!data.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(data);
        for (String root : List.of("Players", "Offline-Players")) {
            ConfigurationSection players = yaml.getConfigurationSection(root);
            if (players == null) {
                continue;
            }
            for (String rawId : players.getKeys(false)) {
                UUID player;
                try {
                    player = UUID.fromString(rawId);
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                ConfigurationSection balances = players.getConfigurationSection(rawId);
                if (balances == null) {
                    continue;
                }
                ids.forEach((file, crate) -> result.keys(player, crate, balances.getInt(file, 0)));
            }
        }
    }

    private static int parse(String value) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException invalid) {
            return 1;
        }
    }
}
