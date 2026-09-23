package com.kirugoldzzzz.lootrift.importer;

import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExcellentCratesSource implements CrateSource {

    private static final Pattern BALANCE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z0-9_]+");

    @Override
    public String id() {
        return "excellentcrates";
    }

    @Override
    public String plugin() {
        return "ExcellentCrates";
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
        Map<String, String> keyOwners = new LinkedHashMap<>();
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String source = file.getName().substring(0, file.getName().length() - 4);
            Imported.Crate crate = crate(CrateSource.slug(source), yaml, result);
            result.add(crate);
            for (String key : keyIds(yaml)) {
                keyOwners.putIfAbsent(key.toLowerCase(Locale.ROOT), crate.id());
            }
            keyOwners.putIfAbsent(source.toLowerCase(Locale.ROOT), crate.id());
        }
        readKeys(pluginFolder, keyOwners, result);
        return result;
    }

    static Imported.Crate crate(String id, ConfigurationSection yaml, Imported.Result result) {
        String name = ImportText.mini(yaml.getString("Name", id));
        Imported.Item icon = adapted(yaml, "ItemProvider", id + ".icon", result);
        List<Imported.Reward> rewards = new ArrayList<>();
        ConfigurationSection list = yaml.getConfigurationSection("Rewards.List");
        if (list != null) {
            for (String rewardId : list.getKeys(false)) {
                ConfigurationSection section = list.getConfigurationSection(rewardId);
                if (section != null) {
                    Imported.Reward reward = reward(id, rewardId, section, result);
                    if (reward != null) {
                        rewards.add(reward);
                    }
                }
            }
        }
        if (rewards.isEmpty()) {
            result.warn(id + Tr.t(" : aucune récompense importée"));
        }
        return new Imported.Crate(id, name, "csgo", icon, null, rewards);
    }

    static Imported.Reward reward(String crate, String rewardId, ConfigurationSection section, Imported.Result result) {
        String where = crate + "." + rewardId;
        double weight = section.getDouble("Weight", -1.0D);
        if (weight <= 0.0D) {
            result.warn(where + Tr.t(" : poids nul, récompense ignorée"));
            return null;
        }
        boolean command = "COMMAND".equalsIgnoreCase(section.getString("Type", "ITEM"));
        List<Imported.Item> given = new ArrayList<>();
        ConfigurationSection items = section.getConfigurationSection("ItemsData");
        if (items != null) {
            for (String index : items.getKeys(false)) {
                Imported.Item item = adapted(items, index, where, result);
                if (item != null) {
                    given.add(item);
                }
            }
        }
        if (!section.getStringList("Items").isEmpty()) {
            result.warn(where + Tr.t(" : ancien format d'objets, ouvrez la caisse une fois dans ExcellentCrates pour le convertir"));
        }
        if (given.size() > 1) {
            result.warn(where + Tr.t(" : plusieurs objets, seul le premier est gardé"));
        }
        Imported.Item preview = adapted(section, "PreviewData", where, result);
        List<String> commands = new ArrayList<>();
        for (String line : section.getStringList("Commands")) {
            commands.add(line.replace("%player_name%", "<player>").replace("%player%", "<player>"));
        }
        boolean giveItem = !command && !given.isEmpty();
        Imported.Item item = giveItem ? given.getFirst() : preview;
        if (item == null) {
            item = CrazyCratesSource.spec("paper", 1, ImportText.mini(section.getString("Name", rewardId)),
                    List.of(), List.of(), false);
        }
        if (!giveItem && commands.isEmpty()) {
            result.warn(where + Tr.t(" : ni objet ni commande, récompense ignorée"));
            return null;
        }
        return new Imported.Reward(CrateSource.slug(rewardId), item, giveItem, weight,
                rarity(section.getString("Rarity")), commands, section.getBoolean("Broadcast", false));
    }

    static Imported.Item adapted(ConfigurationSection parent, String path, String where, Imported.Result result) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        String provider = section.getString("Provider", "vanilla");
        if (!"vanilla".equalsIgnoreCase(provider)) {
            result.warn(where + Tr.t(" : objet du plugin ") + provider + Tr.t(" non pris en charge"));
            return null;
        }
        String value = section.getString("Data.Value");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Snbt.Item item = Snbt.item(value);
            return Imported.Item.argument(item.argument(), item.count());
        } catch (IllegalArgumentException invalid) {
            result.warn(where + Tr.t(" : objet illisible, ") + invalid.getMessage());
            return null;
        }
    }

    static String rarity(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "common" -> "commun";
            case "uncommon" -> "peu-commun";
            case "rare" -> "rare";
            case "epic" -> "epique";
            case "legendary" -> "legendaire";
            case "mythic", "mythical" -> "mythique";
            default -> null;
        };
    }

    static List<String> keyIds(ConfigurationSection yaml) {
        List<String> keys = new ArrayList<>(yaml.getStringList("Key.Ids"));
        ConfigurationSection costs = yaml.getConfigurationSection("CostOptions");
        if (costs != null) {
            for (String cost : costs.getKeys(false)) {
                ConfigurationSection entries = costs.getConfigurationSection(cost + ".Entries");
                if (entries == null) {
                    continue;
                }
                for (String entry : entries.getKeys(false)) {
                    String key = entries.getString(entry + ".Key");
                    if (key != null) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private static void readKeys(File pluginFolder, Map<String, String> keyOwners, Imported.Result result) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(pluginFolder, "config.yml"));
        String type = config.getString("Database.Type", "SQLITE");
        if (!"SQLITE".equalsIgnoreCase(type)) {
            result.warn(Tr.t("Clés non importées : la base ") + type + Tr.t(" n'est pas lue, seule SQLite l'est"));
            return;
        }
        File database = new File(pluginFolder, config.getString("Database.SQLite.FileName", "data.db"));
        if (!database.isFile()) {
            return;
        }
        String prefix = config.getString("Database.Table_Prefix", "excellentcrates");
        if (!TABLE.matcher(prefix).matches()) {
            result.warn(Tr.t("Clés non importées : préfixe de table invalide ") + prefix);
            return;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT \"uuid\", \"keys\" FROM \"" + prefix + "_users\"")) {
            Set<String> orphans = new TreeSet<>();
            while (rows.next()) {
                UUID player;
                try {
                    player = UUID.fromString(rows.getString(1));
                } catch (IllegalArgumentException | NullPointerException invalid) {
                    continue;
                }
                balances(rows.getString(2)).forEach((key, amount) -> {
                    String crate = keyOwners.get(key.toLowerCase(Locale.ROOT));
                    if (crate == null) {
                        orphans.add(key);
                    } else {
                        result.keys(player, crate, amount);
                    }
                });
            }
            for (String orphan : orphans) {
                result.warn(Tr.t("Clé sans caisse ignorée : ") + orphan);
            }
        } catch (SQLException failure) {
            result.warn(Tr.t("Clés non importées : ") + failure.getMessage());
        }
    }

    static Map<String, Integer> balances(String json) {
        Map<String, Integer> balances = new LinkedHashMap<>();
        if (json == null) {
            return balances;
        }
        Matcher matcher = BALANCE.matcher(json);
        while (matcher.find()) {
            try {
                balances.merge(matcher.group(1), Integer.parseInt(matcher.group(2)), Integer::sum);
            } catch (NumberFormatException overflow) {
                balances.merge(matcher.group(1), Integer.MAX_VALUE, (a, b) -> Integer.MAX_VALUE);
            }
        }
        return balances;
    }
}
