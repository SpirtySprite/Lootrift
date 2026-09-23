package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record CrateMilestone(int opens, double money, List<String> commands, String message,
                             boolean repeating) {

    public CrateMilestone {
        opens = Math.max(1, opens);
        money = Math.max(0.0D, money);
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public static List<CrateMilestone> read(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<CrateMilestone> milestones = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            int opens = entry.getInt("opens", parseOpens(key));
            if (opens <= 0) {
                continue;
            }
            milestones.add(new CrateMilestone(opens,
                    entry.getDouble("money", 0.0D),
                    entry.getStringList("commands"),
                    entry.getString("message"),
                    entry.getBoolean("repeating", false)));
        }
        milestones.sort(Comparator.comparingInt(CrateMilestone::opens));
        return List.copyOf(milestones);
    }

    private static int parseOpens(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    public boolean reachedAt(int totalOpens) {
        if (repeating) {
            return totalOpens > 0 && totalOpens % opens == 0;
        }
        return totalOpens == opens;
    }

    public boolean hasMoney() {
        return money > 0.0D;
    }

    public boolean hasCommands() {
        return !commands.isEmpty();
    }

    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    public String label() {
        return repeating ? Tr.t("toutes les ") + opens + Tr.t(" ouvertures") : opens + Tr.t(" ouvertures");
    }
}
