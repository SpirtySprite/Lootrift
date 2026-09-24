package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.api.event.CrateRewardEvent;
import com.kirugoldzzzz.lootrift.common.item.Inventories;
import com.kirugoldzzzz.lootrift.common.item.ItemNames;
import com.kirugoldzzzz.lootrift.common.item.ItemReturn;
import com.kirugoldzzzz.lootrift.common.item.ItemSpec;
import com.kirugoldzzzz.lootrift.common.log.LogTopic;
import com.kirugoldzzzz.lootrift.common.log.StaffAlert;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import com.kirugoldzzzz.lootrift.common.util.Cooldowns;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrateService {

    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final String BYPASS_COOLDOWN = "lootrift.crates.bypass-cooldown";
    public static final int BULK_LIMIT = 20;
    private static final long DAILY_PERIOD = TimeUnit.DAYS.toMillis(1L);

    private final CrateKeyRepository keys;
    private final CratePlacementRepository placements;
    private final CrateHistoryRepository history;
    private final CrateUniqueRepository uniques;
    private final Wallet economy;
    private final CrateEffects effects = new CrateEffects();

    private volatile Map<String, Crate> crates = Map.of();
    private final Set<UUID> opening = ConcurrentHashMap.newKeySet();
    private final Cooldowns<String> cooldowns = new Cooldowns<>();

    private volatile String title = Tr.t("Caisses du serveur");
    private volatile int rows = 5;
    private volatile boolean broadcastEnabled = true;
    private volatile double hologramHeight = 1.4D;
    private volatile int hologramBackgroundAlpha;
    private volatile int retentionDays = DEFAULT_RETENTION_DAYS;
    private volatile int personalHologramViewers = 10;

    private java.nio.file.Path logFile;
    private ScheduledTask maintenance;

    public CrateService(CrateKeyRepository keys, CratePlacementRepository placements,
                        CrateHistoryRepository history, CrateUniqueRepository uniques,
                        Wallet economy) {
        this.keys = keys;
        this.placements = placements;
        this.history = history;
        this.uniques = uniques;
        this.economy = economy;
    }

    public void logFile(java.nio.file.Path target) {
        this.logFile = target;
    }

    public void configure(ConfigurationSection root) {
        if (root == null) {
            crates = Map.of();
            return;
        }
        title = root.getString("title", Tr.t("Caisses du serveur"));
        rows = Math.max(3, Math.min(6, root.getInt("rows", 5)));

        ConfigurationSection settings = root.getConfigurationSection("settings");
        broadcastEnabled = settings == null || settings.getBoolean("broadcast", true);
        hologramHeight = settings == null ? 1.4D : settings.getDouble("hologram-height", 1.4D);
        hologramBackgroundAlpha = settings == null ? 0
                : Math.max(0, Math.min(255, settings.getInt("hologram-background-alpha", 0)));
        retentionDays = settings == null
                ? DEFAULT_RETENTION_DAYS
                : Math.max(0, settings.getInt("history-retention-days", DEFAULT_RETENTION_DAYS));
        personalHologramViewers = settings == null ? 10
                : Math.max(0, Math.min(64, settings.getInt("personal-hologram-viewers", 10)));
        CrateLog.bind(logFile,
                settings == null || settings.getBoolean("log-to-file", true),
                settings != null && settings.getBoolean("log-verbose", false));
        effects.configure(
                settings == null || settings.getBoolean("sounds", true),
                settings == null || settings.getBoolean("particles", true),
                settings == null || settings.getBoolean("fireworks", true),
                settings == null || settings.getBoolean("titles", true));

        Map<String, Crate> loaded = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("crates");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection crateSection = section.getConfigurationSection(id);
                if (crateSection == null) {
                    continue;
                }
                Crate crate = readCrate(id.toLowerCase(Locale.ROOT), crateSection);
                loaded.put(crate.id(), crate);
            }
        }
        crates = Collections.unmodifiableMap(loaded);
    }

    private Crate readCrate(String id, ConfigurationSection section) {
        Material block = ItemSpec.material(section.getString("block"), Material.CHEST);
        ItemStack icon = ItemSpec.read(section.getConfigurationSection("icon"), block);
        ItemStack key = ItemSpec.read(section.getConfigurationSection("key"), Material.TRIPWIRE_HOOK);

        ConfigurationSection pity = section.getConfigurationSection("pity");
        ConfigurationSection hologram = section.getConfigurationSection("hologram");

        return new Crate(id,
                section.getString("name", id),
                icon,
                block,
                key,
                CrateAnimationType.byId(section.getString("animation"), CrateAnimationType.CSGO),
                readRewards(section.getConfigurationSection("rewards")),
                section.getInt("rolls", 1),
                section.getBoolean("broadcast", true),
                section.getString("permission"),
                pity == null ? 0 : pity.getInt("after", 0),
                CrateRarity.byId(pity == null ? null : pity.getString("floor"), CrateRarity.EPIQUE),
                hologram != null && hologram.getBoolean("enabled", false),
                hologram == null ? List.of() : hologram.getStringList("lines"),
                CrateBlockEffects.read(section.getConfigurationSection("effects")),
                section.getInt("cooldown-seconds", 0),
                section.getDouble("price", 0.0D),
                section.getBoolean("daily-key", false),
                CrateMilestone.read(section.getConfigurationSection("milestones")),
                CrateBulkAnimation.byId(section.getString("bulk-animation"),
                        CrateBulkAnimation.DOMINO),
                CrateModel.read(section.getConfigurationSection("model")));
    }

    private List<CrateReward> readRewards(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<CrateReward> rewards = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(id);
            if (rewardSection == null) {
                continue;
            }
            int min = Math.max(1, rewardSection.getInt("min-amount", 1));
            double[] money = moneyRange(rewardSection.get("money"));
            rewards.add(new CrateReward(id,
                    display(rewardSection, id),
                    rewardSection.getBoolean("give-item", true),
                    rewardSection.getInt("weight", 10),
                    min,
                    Math.max(min, rewardSection.getInt("max-amount", min)),
                    money[0],
                    rewardSection.getStringList("commands"),
                    CrateRarity.byId(rewardSection.getString("rarity"), CrateRarity.COMMUN),
                    rewardSection.getString("permission"),
                    rewardSection.contains("announce") ? rewardSection.getBoolean("announce") : null,
                    rewardSection.getBoolean("unique", false),
                    money[1],
                    rewardSection.getInt("xp", 0)));
        }
        return rewards;
    }

    static double[] moneyRange(Object raw) {
        if (raw == null) {
            return new double[]{0.0D, 0.0D};
        }
        if (raw instanceof Number number) {
            return new double[]{number.doubleValue(), number.doubleValue()};
        }
        String text = raw.toString().replace(" ", "");
        int dash = text.indexOf('-', 1);
        try {
            if (dash > 0) {
                double low = Double.parseDouble(text.substring(0, dash));
                double high = Double.parseDouble(text.substring(dash + 1));
                return new double[]{Math.min(low, high), Math.max(low, high)};
            }
            double value = Double.parseDouble(text);
            return new double[]{value, value};
        } catch (NumberFormatException invalid) {
            return new double[]{0.0D, 0.0D};
        }
    }

    private static ItemStack display(ConfigurationSection section, String id) {
        String custom = section.getString("custom-item");
        if (custom != null && !custom.isBlank()) {
            Optional<ItemStack> resolved = ExternalItems.resolve(custom);
            if (resolved.isPresent()) {
                return resolved.get();
            }
            CrateLog.warn(id + Tr.t(" : objet personnalisé introuvable ") + custom
                    + (ExternalItems.available(custom) ? "" : Tr.t(", le plugin n'est pas chargé")));
        }
        return ItemSpec.read(section, Material.STONE);
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public boolean broadcastEnabled() {
        return broadcastEnabled;
    }

    public int personalHologramViewers() {
        return personalHologramViewers;
    }

    public int hologramBackgroundAlpha() {
        return hologramBackgroundAlpha;
    }

    public double hologramHeight() {
        return hologramHeight;
    }

    public CrateEffects effects() {
        return effects;
    }

    public CrateKeyRepository keyRepository() {
        return keys;
    }

    public CratePlacementRepository placementRepository() {
        return placements;
    }

    public CrateUniqueRepository uniqueRepository() {
        return uniques;
    }

    public CrateHistoryRepository historyRepository() {
        return history;
    }

    public Wallet economy() {
        return economy;
    }

    public List<Crate> crates() {
        return List.copyOf(crates.values());
    }

    public List<String> crateIds() {
        return List.copyOf(crates.keySet());
    }

    public Optional<Crate> crate(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(crates.get(id.toLowerCase(Locale.ROOT)));
    }

    public int crateCount() {
        return crates.size();
    }

    public int rewardCount() {
        int total = 0;
        for (Crate crate : crates.values()) {
            total += crate.rewards().size();
        }
        return total;
    }

    public int virtualKeys(UUID owner, Crate crate) {
        return keys.keys(owner, crate.id());
    }

    public int physicalKeys(Player player, Crate crate) {
        return CrateKeys.countIn(player.getInventory(), crate);
    }

    public int totalKeys(Player player, Crate crate) {
        return physicalKeys(player, crate) + virtualKeys(player.getUniqueId(), crate);
    }

    public Map<String, Integer> virtualKeysOf(UUID owner) {
        return keys.keysOf(owner);
    }

    public int giveVirtualKeys(UUID owner, Crate crate, int amount) {
        int total = keys.addKeys(owner, crate.id(), Math.abs(amount));
        Player online = Bukkit.getPlayer(owner);
        if (online != null) {
            Messages.send(online, "crates.keys-received",
                    Mini.value("amount", String.valueOf(Math.abs(amount))),
                    Mini.styled("crate", crate.displayName()));
        }
        return total;
    }

    public int takeVirtualKeys(UUID owner, Crate crate, int amount) {
        return keys.addKeys(owner, crate.id(), -Math.abs(amount));
    }

    public void setVirtualKeys(UUID owner, Crate crate, int amount) {
        keys.setKeys(owner, crate.id(), amount);
    }

    public void givePhysicalKeys(Player player, Crate crate, int amount) {
        int remaining = Math.max(1, amount);
        List<ItemStack> stacks = new ArrayList<>();
        int maxStack = Math.max(1, crate.keyItem().getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(maxStack, remaining);
            stacks.add(CrateKeys.physicalKey(crate, size));
            remaining -= size;
        }
        ItemReturn.give(player, stacks, Tr.t("Clés de caisse"));
    }

    public boolean buyKey(Player player, Crate crate, int amount) {
        if (!economy.playerOperationsAllowed() || !crate.purchasable() || amount <= 0) {
            return false;
        }
        double cost = Numbers.round(crate.price() * amount);
        boolean purchased = economy.change(() -> {
            if ((long) keys.keys(player.getUniqueId(), crate.id()) + amount > Integer.MAX_VALUE
                    || !economy.withdraw(player.getUniqueId(), cost)) {
                return false;
            }
            try {
                keys.addKeysDurable(player.getUniqueId(), crate.id(), amount);
            } catch (RuntimeException failure) {
                economy.deposit(player.getUniqueId(), cost);
                throw failure;
            }
            return true;
        });
        if (!purchased) {
            return false;
        }
        CrateLog.player(CrateLog.KEY_BUY, player, crate, cost,
                amount + Tr.t(" clés pour ") + Numbers.money(cost));
        Messages.send(player, "crates.key-bought",
                Mini.value("amount", String.valueOf(amount)),
                Mini.styled("crate", crate.displayName()),
                Mini.value("price", Numbers.money(cost)));
        return true;
    }

    public long dailyRemaining(Player player, Crate crate) {
        if (!crate.dailyKey()) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - keys.lastDaily(player.getUniqueId(), crate.id());
        return Math.max(0L, DAILY_PERIOD - elapsed);
    }

    public boolean claimDaily(Player player, Crate crate) {
        if (!crate.dailyKey()) {
            return false;
        }
        boolean granted = keys.claimDaily(player.getUniqueId(), crate.id(),
                System.currentTimeMillis(), DAILY_PERIOD);
        if (granted) {
            Messages.send(player, "crates.daily-claimed",
                    Mini.styled("crate", crate.displayName()));
            CrateLog.player(CrateLog.KEY_DAILY, player, crate, 1.0D, Tr.t("clé du jour réclamée"));
        }
        return granted;
    }

    public Refusal check(Player player, Crate crate) {
        if (!crate.canOpen(player)) {
            return Refusal.NO_PERMISSION;
        }
        if (crate.isEmpty()) {
            return Refusal.EMPTY;
        }
        if (CrateLoot.eligible(crate, player, uniques).isEmpty()) {
            return Refusal.EXHAUSTED;
        }
        if (opening.contains(player.getUniqueId())) {
            return Refusal.BUSY;
        }
        if (cooldowns.isActive(throttleKey(player, crate))) {
            return Refusal.COOLDOWN;
        }
        if (totalKeys(player, crate) <= 0) {
            return Refusal.NO_KEY;
        }
        return Refusal.NONE;
    }

    public long cooldownRemaining(Player player, Crate crate) {
        return cooldowns.remaining(throttleKey(player, crate));
    }

    private void applyCooldown(Player player, Crate crate) {
        if (crate.throttled() && !player.hasPermission(BYPASS_COOLDOWN)) {
            cooldowns.apply(throttleKey(player, crate), crate.cooldownMillis());
        }
    }

    private static String throttleKey(Player player, Crate crate) {
        return player.getUniqueId() + ":" + crate.id();
    }

    public Optional<Session> begin(Player player, Crate crate) {
        if (check(player, crate) != Refusal.NONE) {
            return Optional.empty();
        }
        if (!opening.add(player.getUniqueId())) {
            return Optional.empty();
        }
        boolean physical = CrateKeys.consumeOne(player, crate);
        if (!physical && !keys.takeKey(player.getUniqueId(), crate.id())) {
            opening.remove(player.getUniqueId());
            return Optional.empty();
        }
        CrateLoot.Draw draw = CrateLoot.draw(crate, player,
                keys.streak(player.getUniqueId(), crate.id()), uniques);
        if (draw.isEmpty()) {
            refund(player, crate, physical);
            opening.remove(player.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(new Session(player, crate, draw, physical));
    }

    public Bulk openBulk(Player player, Crate crate, int requested) {
        if (check(player, crate) != Refusal.NONE) {
            return Bulk.EMPTY;
        }
        if (!opening.add(player.getUniqueId())) {
            return Bulk.EMPTY;
        }
        List<Grant> collected = new ArrayList<>();
        int opened = 0;
        try {
            int wanted = Math.max(1, Math.min(BULK_LIMIT, requested));
            for (int round = 0; round < wanted; round++) {
                boolean physical = CrateKeys.consumeOne(player, crate);
                if (!physical && !keys.takeKey(player.getUniqueId(), crate.id())) {
                    break;
                }
                CrateLoot.Draw draw = CrateLoot.draw(crate, player,
                        keys.streak(player.getUniqueId(), crate.id()), uniques);
                if (draw.isEmpty()) {
                    refund(player, crate, physical);
                    break;
                }
                for (CrateReward reward : draw.rewards()) {
                    Grant granted = new Grant(reward, reward.rollAmount());
                    collected.add(granted);
                    deliverGrant(player, crate, granted, false);
                }
                keys.recordOpen(player.getUniqueId(), crate.id(), draw.satisfied());
                awardMilestones(player, crate);
                opened++;
            }
            if (opened > 0) {
                applyCooldown(player, crate);
                CrateLog.player(CrateLog.BULK, player, crate, opened,
                        opened + " ouvertures -> " + CrateLog.describe(collected));
            }
        } finally {
            opening.remove(player.getUniqueId());
        }
        return opened == 0 ? Bulk.EMPTY : new Bulk(List.copyOf(collected), opened);
    }

    private void awardMilestones(Player player, Crate crate) {
        if (crate.milestones().isEmpty()) {
            return;
        }
        int total = keys.opened(player.getUniqueId(), crate.id());
        for (CrateMilestone milestone : crate.milestones()) {
            if (!milestone.reachedAt(total)) {
                continue;
            }
            if (milestone.hasMoney()) {
                economy.creditOwed(player.getUniqueId(), milestone.money());
            }
            if (milestone.hasCommands()) {
                runConsole(player, crate, milestone.commands());
            }
            if (milestone.hasMessage()) {
                player.sendMessage(Mini.label(milestone.message(),
                        Mini.value("player", player.getName()),
                        Mini.value("opens", String.valueOf(total)),
                        Mini.styled("crate", crate.displayName())));
            }
            Messages.send(player, "crates.milestone",
                    Mini.styled("crate", crate.displayName()),
                    Mini.value("opens", String.valueOf(total)));
            CrateLog.player(CrateLog.MILESTONE, player, crate, milestone.money(),
                    milestone.label() + " atteint a " + total + Tr.t(" ouvertures"));
        }
    }

    private void deliverGrant(Player player, Crate crate, Grant granted, boolean chat) {
        CrateReward reward = granted.reward();
        int amount = granted.amount();
        try {
            if (reward.solo()) {
                uniques.mark(player.getUniqueId(), crate.id(), reward.id());
            }
            if (reward.giveItem()) {
                ItemReturn.give(player, Inventories.split(reward.itemFor(1), amount),
                        Tr.t("Caisse ") + Mini.plain(Mini.label(crate.displayName())));
            }
            double paid = reward.hasMoney() ? reward.rollMoney() : 0.0D;
            if (paid > 0.0D) {
                economy.creditOwed(player.getUniqueId(), paid);
            }
            if (reward.hasXp()) {
                player.giveExp(reward.xp());
            }
            if (reward.hasCommands()) {
                dispatch(player, crate, reward);
            }
            history.append(CratePull.of(player, crate, reward, rewardLabel(reward), amount));
            if (Bukkit.getServer() != null) {
                Bukkit.getPluginManager().callEvent(new CrateRewardEvent(player, crate.id(), reward.id(),
                        reward.rarity().id(), amount, paid, reward.display()));
            }
            if (chat) {
                announceReward(player, crate, reward, amount, paid);
            } else if (broadcastEnabled && crate.broadcast() && reward.announced()) {
                broadcastReward(player, crate, reward, amount);
            }
        } catch (RuntimeException failure) {
            CrateLog.failure(Tr.t("Récompense ") + reward.id() + Tr.t(" de la caisse ") + crate.id()
                    + Tr.t(" non remise à ") + player.getName() + " (" + amount + "x)", failure);
            StaffAlert.critical(LogTopic.CRATES, Tr.t("Récompense de caisse non remise"))
                    .summary(player.getName() + " · " + crate.id() + " · " + rewardLabel(reward))
                    .player(Tr.t("Joueur"), player.getName())
                    .detail(Card.CATEGORY, Tr.t("Caisse"), crate.id())
                    .detail(Card.STAR, Tr.t("Récompense"), rewardLabel(reward))
                    .count(Card.AMOUNT, Tr.t("Quantité"), amount)
                    .detail(Card.CHANCE, Tr.t("Rareté"), reward.rarity().id())
                    .error(failure)
                    .reference(Tr.t("Récompense"), reward.id())
                    .send();
        }
    }

    private void dispatch(Player player, Crate crate, CrateReward reward) {
        runConsole(player, crate, reward.commands());
    }

    private void runConsole(Player player, Crate crate, List<String> template) {
        String name = player.getName();
        List<String> commands = new ArrayList<>(template.size());
        for (String command : template) {
            commands.add(command
                    .replace("<player>", name)
                    .replace("%player%", name)
                    .replace("<crate>", crate.id()));
        }
        Scheduling.global(() -> {
            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });
    }

    private void announceReward(Player player, Crate crate, CrateReward reward, int amount, double paid) {
        Messages.send(player, "crates.reward",
                Mini.styled("crate", crate.displayName()),
                Mini.styled("rarity", reward.rarity().colored(reward.rarity().displayName())),
                Mini.component("reward", rewardName(reward)),
                Mini.value("amount", String.valueOf(amount)));
        if (paid > 0.0D) {
            Messages.send(player, "crates.reward-money",
                    Mini.value("amount", Numbers.money(paid)));
        }
        if (reward.hasXp()) {
            Messages.send(player, "crates.reward-xp", Mini.value("amount", String.valueOf(reward.xp())));
        }
        if (broadcastEnabled && crate.broadcast() && reward.announced()) {
            broadcastReward(player, crate, reward, amount);
        }
    }

    private void broadcastReward(Player player, Crate crate, CrateReward reward, int amount) {
        Messages.broadcast("crates.announce",
                Mini.value("player", player.getName()),
                Mini.styled("crate", crate.displayName()),
                Mini.styled("rarity", reward.rarity().colored(reward.rarity().displayName())),
                Mini.component("reward", rewardName(reward)),
                Mini.value("amount", String.valueOf(amount)));
    }

    public record Bulk(List<Grant> grants, int opened) {

        private static final Bulk EMPTY = new Bulk(List.of(), 0);

        public boolean isEmpty() {
            return opened == 0;
        }

        public CrateRarity best() {
            CrateRarity best = CrateRarity.COMMUN;
            for (Grant granted : grants) {
                if (granted.reward().rarity().tier() > best.tier()) {
                    best = granted.reward().rarity();
                }
            }
            return best;
        }
    }

    private void refund(Player player, Crate crate, boolean physical) {
        if (physical) {
            givePhysicalKeys(player, crate, 1);
        } else {
            keys.addKeys(player.getUniqueId(), crate.id(), 1);
        }
        CrateLog.player(CrateLog.KEY_REFUND, player, crate, 1.0D,
                physical ? Tr.t("clé physique rendue") : Tr.t("clé virtuelle rendue"));
    }

    public boolean isOpening(Player player) {
        return opening.contains(player.getUniqueId());
    }

    public void release(UUID owner) {
        opening.remove(owner);
    }

    public void startMaintenance() {
        stopMaintenance();
        if (retentionDays <= 0) {
            return;
        }
        maintenance = Scheduling.asyncTimer(this::purgeHistory, 600L, 21_600L);
    }

    public void stopMaintenance() {
        if (maintenance != null) {
            maintenance.cancel();
            maintenance = null;
        }
    }

    private void purgeHistory() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        history.purgeOlderThan(cutoff);
    }

    public static Component rewardName(CrateReward reward) {
        return ItemNames.of(reward.display());
    }

    public static String rewardLabel(CrateReward reward) {
        return Mini.plain(rewardName(reward));
    }

    public record Grant(CrateReward reward, int amount) {

        public ItemStack item() {
            return reward.itemFor(amount);
        }
    }

    public enum Refusal {
        NONE,
        NO_PERMISSION,
        NO_KEY,
        EMPTY,
        BUSY,
        COOLDOWN,
        EXHAUSTED;

        public String messageKey() {
            return switch (this) {
                case NONE -> null;
                case NO_PERMISSION -> "general.no-permission";
                case NO_KEY -> "crates.no-key";
                case EMPTY -> "crates.empty";
                case BUSY -> "crates.busy";
                case COOLDOWN -> "crates.cooldown";
                case EXHAUSTED -> "crates.exhausted";
            };
        }
    }

    public final class Session {

        private final Player player;
        private final Crate crate;
        private final CrateLoot.Draw draw;
        private final List<Grant> grants;
        private final boolean physicalKey;
        private final AtomicBoolean delivered = new AtomicBoolean();

        private Session(Player player, Crate crate, CrateLoot.Draw draw, boolean physicalKey) {
            this.player = player;
            this.crate = crate;
            this.draw = draw;
            this.physicalKey = physicalKey;
            List<Grant> rolled = new ArrayList<>(draw.rewards().size());
            for (CrateReward reward : draw.rewards()) {
                rolled.add(new Grant(reward, reward.rollAmount()));
            }
            this.grants = List.copyOf(rolled);
        }

        public Crate crate() {
            return crate;
        }

        public CrateLoot.Draw draw() {
            return draw;
        }

        public List<CrateReward> rewards() {
            return draw.rewards();
        }

        public List<Grant> grants() {
            return grants;
        }

        public CrateReward winner() {
            return draw.rewards().get(0);
        }

        public int winnerAmount() {
            return grants.get(0).amount();
        }

        public boolean pity() {
            return draw.pity();
        }

        public boolean isDelivered() {
            return delivered.get();
        }

        public void cancel() {
            if (!delivered.compareAndSet(false, true)) {
                return;
            }
            refund(player, crate, physicalKey);
            opening.remove(player.getUniqueId());
        }

        public void deliver() {
            if (!delivered.compareAndSet(false, true)) {
                return;
            }
            try {
                for (Grant granted : grants) {
                    deliverGrant(player, crate, granted, true);
                }
                keys.recordOpen(player.getUniqueId(), crate.id(), draw.satisfied());
                awardMilestones(player, crate);
                applyCooldown(player, crate);
                CrateLog.player(CrateLog.OPEN, player, crate, 0.0D,
                        (draw.pity() ? Tr.t("pitié | ") : "") + CrateLog.describe(grants));
            } finally {
                opening.remove(player.getUniqueId());
            }
        }
    }
}
