package com.kirugoldzzzz.lootrift.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Imported {

    private Imported() {
    }

    public record Item(Map<String, Object> spec, String argument, int amount) {

        public static Item spec(Map<String, Object> spec, int amount) {
            return new Item(Map.copyOf(spec), null, Math.max(1, amount));
        }

        public static Item argument(String argument, int amount) {
            return new Item(Map.of(), argument, Math.max(1, amount));
        }
    }

    public record Reward(String id, Item item, boolean giveItem, double weight, String rarity, List<String> commands,
                         boolean announce) {
    }

    public record Crate(String id, String name, String animation, Item icon, Item key, List<Reward> rewards) {
    }

    public static final class Result {

        private final String source;
        private final List<Crate> crates = new ArrayList<>();
        private final Map<UUID, Map<String, Integer>> keys = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();

        public Result(String source) {
            this.source = source;
        }

        public String source() {
            return source;
        }

        public List<Crate> crates() {
            return crates;
        }

        public Map<UUID, Map<String, Integer>> keys() {
            return keys;
        }

        public List<String> warnings() {
            return warnings;
        }

        public void add(Crate crate) {
            crates.add(crate);
        }

        public void keys(UUID player, String crate, int amount) {
            if (amount > 0) {
                keys.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).merge(crate, amount, Integer::sum);
            }
        }

        public void warn(String warning) {
            warnings.add(warning);
        }
    }
}
