package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.kirugoldzzzz.lootrift.common.log.LogTopic;
import com.kirugoldzzzz.lootrift.common.log.StaffAlert;
import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.config.ConfigFile;
import com.kirugoldzzzz.lootrift.common.config.Sections;
import com.kirugoldzzzz.lootrift.common.item.ItemNames;
import com.kirugoldzzzz.lootrift.common.item.ItemSpec;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrateEditor {

    private final ConfigFile file;
    private final CrateService service;

    private CrateHolograms holograms;
    private CrateModels models;

    public CrateEditor(ConfigFile file, CrateService service) {
        this.file = file;
        this.service = service;
    }

    public void bind(CrateHolograms holograms) {
        this.holograms = holograms;
    }

    public void bind(CrateModels models) {
        this.models = models;
    }

    public String createCrate(ItemStack icon) {
        String id = uniqueId("crates", ItemNames.slug(icon.getType()));
        ConfigurationSection section = crates().createSection(id);
        section.set("name", ItemNames.tag(icon.getType()));
        section.set("block", icon.getType().isBlock() ? icon.getType().name() : Material.CHEST.name());
        section.set("animation", CrateAnimationType.CSGO.id());
        section.set("rolls", 1);
        section.set("broadcast", true);
        ItemSpec.write(section.createSection("icon"), icon);

        ConfigurationSection key = section.createSection("key");
        key.set("material", Material.TRIPWIRE_HOOK.name());
        key.set("name", Tr.t("<#A78BFA><b>Clé</b>"));
        key.set("glow", true);

        ConfigurationSection pity = section.createSection("pity");
        pity.set("after", 0);
        pity.set("floor", CrateRarity.EPIQUE.id());

        ConfigurationSection hologram = section.createSection("hologram");
        hologram.set("enabled", false);
        hologram.set("lines", new ArrayList<>(List.of("<#A78BFA><b>Caisse</b>",
                Tr.t("<#6E7681>Clic droit avec une clé"))));

        section.createSection("rewards");
        apply();
        CrateLog.system(CrateLog.CRATE_CREATED, service.crate(id).orElse(null),
                Tr.t("depuis ") + icon.getType().name());
        return id;
    }

    public String duplicateCrate(String crateId) {
        ConfigurationSection source = crate(crateId);
        String copy = uniqueId("crates", crateId);
        ConfigurationSection target = crates().createSection(copy);
        Sections.copy(source, target);
        target.set("name", source.getString("name", crateId) + " (copie)");
        apply();
        CrateLog.system(CrateLog.CRATE_COPIED, service.crate(copy).orElse(null),
                Tr.t("copie de ") + crateId);
        return copy;
    }

    public void setCooldown(String crateId, int seconds) {
        crate(crateId).set("cooldown-seconds", Math.max(0, seconds));
        apply(crateId, Tr.t("délai entre ouvertures"));
    }

    public void deleteCrate(String crateId) {
        Crate removed = service.crate(crateId).orElse(null);
        for (CratePlacement placement : service.placementRepository().of(crateId)) {
            if (holograms != null) {
                holograms.remove(placement);
            }
            if (models != null) {
                models.remove(placement);
            }
        }
        service.placementRepository().removeAll(crateId);
        service.keyRepository().forgetCrate(crateId);
        service.uniqueRepository().forgetCrate(crateId);
        crates().set(crateId.toLowerCase(Locale.ROOT), null);
        apply();
        CrateLog.system(CrateLog.CRATE_DELETED, removed,
                removed == null ? crateId : removed.rewards().size() + Tr.t(" récompenses perdues"));
    }

    public void renameCrate(String crateId, String name) {
        crate(crateId).set("name", name);
        apply(crateId, "nom");
    }

    public void setIcon(String crateId, ItemStack icon) {
        ConfigurationSection section = crate(crateId);
        section.set("icon", null);
        ItemSpec.write(section.createSection("icon"), icon);
        apply(crateId, Tr.t("icône"));
    }

    public void setBlock(String crateId, Material block) {
        crate(crateId).set("block", block.name());
        apply(crateId, Tr.t("bloc"));
    }

    public void setKeyItem(String crateId, ItemStack key) {
        ConfigurationSection section = crate(crateId);
        section.set("key", null);
        ItemSpec.write(section.createSection("key"), key);
        apply(crateId, Tr.t("objet clé"));
    }

    public void setAnimation(String crateId, CrateAnimationType animation) {
        crate(crateId).set("animation", animation.id());
        apply(crateId, Tr.t("animation d'ouverture"));
    }

    public void setRolls(String crateId, int rolls) {
        crate(crateId).set("rolls", Math.max(1, Math.min(9, rolls)));
        apply(crateId, Tr.t("tirages par ouverture"));
    }

    public void setBroadcast(String crateId, boolean broadcast) {
        crate(crateId).set("broadcast", broadcast);
        apply(crateId, Tr.t("annonces"));
    }

    public void setPermission(String crateId, String permission) {
        crate(crateId).set("permission", blankToNull(permission));
        apply(crateId, "permission");
    }

    public void setPity(String crateId, int after, CrateRarity floor) {
        ConfigurationSection pity = child(crate(crateId), "pity");
        pity.set("after", Math.max(0, after));
        pity.set("floor", floor.id());
        apply(crateId, Tr.t("pitié"));
    }

    public void setHologram(String crateId, boolean enabled) {
        child(crate(crateId), "hologram").set("enabled", enabled);
        apply(crateId, Tr.t("hologramme"));
    }

    public void setHologramLines(String crateId, List<String> lines) {
        child(crate(crateId), "hologram").set("lines", new ArrayList<>(lines));
        apply(crateId, Tr.t("lignes d'hologramme"));
    }

    public void setBulkAnimation(String crateId, CrateBulkAnimation animation) {
        crate(crateId).set("bulk-animation", animation.id());
        apply(crateId, Tr.t("animation groupée"));
    }

    public void setBlockEffects(String crateId, CrateBlockEffects effects) {
        effects.write(child(crate(crateId), "effects"));
        apply(crateId, Tr.t("effets de bloc"));
    }

    public String addReward(String crateId, ItemStack item) {
        ConfigurationSection rewards = rewards(crateId);
        String id = uniqueId("crates." + crateId + ".rewards", ItemNames.slug(item.getType()));
        ConfigurationSection section = rewards.createSection(id);
        ItemSpec.write(section, item);
        section.set("weight", 10);
        section.set("rarity", CrateRarity.COMMUN.id());
        section.set("min-amount", Math.max(1, item.getAmount()));
        section.set("max-amount", Math.max(1, item.getAmount()));
        apply();
        CrateLog.system(CrateLog.REWARD_ADDED, service.crate(crateId).orElse(null),
                id + " (" + item.getType().name() + ")");
        return id;
    }

    public void setPrice(String crateId, double price) {
        crate(crateId).set("price", Numbers.round(Math.max(0.0D, price)));
        apply(crateId, Tr.t("prix de la clé"));
    }

    public void setDailyKey(String crateId, boolean daily) {
        crate(crateId).set("daily-key", daily);
        apply(crateId, Tr.t("clé quotidienne"));
    }

    public int importRewards(String crateId, List<ItemStack> items) {
        ConfigurationSection rewards = rewards(crateId);
        int added = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String id = uniqueId("crates." + crateId + ".rewards", ItemNames.slug(item.getType()));
            ConfigurationSection section = rewards.createSection(id);
            ItemSpec.write(section, item);
            section.set("weight", 10);
            section.set("rarity", CrateRarity.COMMUN.id());
            section.set("min-amount", Math.max(1, item.getAmount()));
            section.set("max-amount", Math.max(1, item.getAmount()));
            added++;
        }
        if (added > 0) {
            apply();
            CrateLog.system(CrateLog.REWARD_IMPORTED, service.crate(crateId).orElse(null),
                    added + Tr.t(" récompenses depuis un conteneur"));
        }
        return added;
    }

    public void setRewardChance(String crateId, String rewardId, double percent) {
        Crate crate = service.crate(crateId).orElse(null);
        CrateReward reward = crate == null ? null : crate.reward(rewardId);
        if (reward == null) {
            return;
        }
        double target = Math.max(0.01D, Math.min(99.0D, percent)) / 100.0D;
        int others = crate.totalWeight() - reward.weight();
        int weight = (int) Math.round(target * others / (1.0D - target));
        setRewardWeight(crateId, rewardId, Math.max(1, weight));
    }

    public void deleteReward(String crateId, String rewardId) {
        rewards(crateId).set(rewardId, null);
        apply();
        CrateLog.system(CrateLog.REWARD_DELETED, service.crate(crateId).orElse(null), rewardId);
    }

    public void setRewardItem(String crateId, String rewardId, ItemStack item) {
        ConfigurationSection section = reward(crateId, rewardId);
        section.set("item", null);
        ItemSpec.write(section, item);
        apply(crateId, Tr.t("objet de ") + rewardId);
    }

    public void setRewardWeight(String crateId, String rewardId, int weight) {
        reward(crateId, rewardId).set("weight", Math.max(1, weight));
        apply(crateId, Tr.t("poids de ") + rewardId);
    }

    public void setRewardRarity(String crateId, String rewardId, CrateRarity rarity) {
        reward(crateId, rewardId).set("rarity", rarity.id());
        apply(crateId, Tr.t("rareté de ") + rewardId);
    }

    public void setRewardAmounts(String crateId, String rewardId, int min, int max) {
        ConfigurationSection section = reward(crateId, rewardId);
        int low = Math.max(1, min);
        section.set("min-amount", low);
        section.set("max-amount", Math.max(low, max));
        apply(crateId, Tr.t("quantités de ") + rewardId);
    }

    public void setRewardMoney(String crateId, String rewardId, double money) {
        reward(crateId, rewardId).set("money", Numbers.round(Math.max(0.0D, money)));
        apply(crateId, Tr.t("argent de ") + rewardId);
    }

    public void setRewardPermission(String crateId, String rewardId, String permission) {
        reward(crateId, rewardId).set("permission", blankToNull(permission));
        apply(crateId, Tr.t("permission de ") + rewardId);
    }

    public void setRewardAnnounce(String crateId, String rewardId, Boolean announce) {
        reward(crateId, rewardId).set("announce", announce);
        apply(crateId, Tr.t("annonce de ") + rewardId);
    }

    public void setRewardUnique(String crateId, String rewardId, boolean unique) {
        reward(crateId, rewardId).set("unique", unique);
        apply(crateId, Tr.t("unicité de ") + rewardId);
    }

    public void setRewardGiveItem(String crateId, String rewardId, boolean giveItem) {
        reward(crateId, rewardId).set("give-item", giveItem);
        apply(crateId, Tr.t("remise objet de ") + rewardId);
    }

    public void addRewardCommand(String crateId, String rewardId, String command) {
        ConfigurationSection section = reward(crateId, rewardId);
        List<String> commands = new ArrayList<>(section.getStringList("commands"));
        commands.add(command);
        section.set("commands", commands);
        apply(crateId, Tr.t("commande ajoutée à ") + rewardId);
    }

    public void removeRewardCommand(String crateId, String rewardId, int index) {
        ConfigurationSection section = reward(crateId, rewardId);
        List<String> commands = new ArrayList<>(section.getStringList("commands"));
        if (index < 0 || index >= commands.size()) {
            return;
        }
        commands.remove(index);
        section.set("commands", commands.isEmpty() ? null : commands);
        apply(crateId, Tr.t("commande retirée de ") + rewardId);
    }

    public void setTitle(String title) {
        file.get().set("title", title);
        apply(null, Tr.t("titre du menu"));
    }

    public void setRows(int rows) {
        file.get().set("rows", Math.max(3, Math.min(6, rows)));
        apply(null, Tr.t("hauteur du menu"));
    }

    public void setSetting(String key, boolean value) {
        settings().set(key, value);
        apply(null, Tr.t("réglage ") + key + " = " + value);
    }

    public void setSetting(String key, int value) {
        settings().set(key, value);
        apply(null, Tr.t("réglage ") + key + " = " + value);
    }

    public void setSetting(String key, double value) {
        settings().set(key, value);
        apply(null, Tr.t("réglage ") + key + " = " + value);
    }

    public boolean setting(String key, boolean fallback) {
        return settings().getBoolean(key, fallback);
    }

    public void reload() {
        file.load();
        service.configure(file.get());
        if (holograms != null) {
            holograms.refreshAll();
        }
    }

    private void apply() {
        apply(null, null);
    }

    private void apply(String crateId, String change) {
        boolean saved = file.save();
        service.configure(file.get());
        if (holograms != null) {
            holograms.refreshAll();
        }
        if (!saved) {
            String lost = change == null ? Tr.t("inconnu") : change;
            CrateLog.warn(Tr.t("crates.yml n'a pas pu être enregistré, changement perdu au redémarrage : ") + lost);
            StaffAlert.warning(LogTopic.CRATES, Tr.t("Configuration des caisses non enregistrée"))
                    .summary(Tr.t("crates.yml n'a pas pu être écrit, le changement sera perdu au redémarrage"))
                    .detail(Card.CATEGORY, Tr.t("Caisse"), crateId == null ? Tr.t("Toutes") : crateId)
                    .detail(Card.SEARCH, Tr.t("Changement"), lost)
                    .send();
            return;
        }
        if (change != null) {
            CrateLog.system(CrateLog.CONFIG,
                    crateId == null ? null : service.crate(crateId).orElse(null), change);
        }
    }

    private ConfigurationSection settings() {
        return child(file.get(), "settings");
    }

    private ConfigurationSection crates() {
        return child(file.get(), "crates");
    }

    private ConfigurationSection crate(String crateId) {
        return child(crates(), crateId.toLowerCase(Locale.ROOT));
    }

    private ConfigurationSection rewards(String crateId) {
        return child(crate(crateId), "rewards");
    }

    private ConfigurationSection reward(String crateId, String rewardId) {
        return child(rewards(crateId), rewardId);
    }

    private static ConfigurationSection child(ConfigurationSection parent, String path) {
        ConfigurationSection existing = parent.getConfigurationSection(path);
        return existing == null ? parent.createSection(path) : existing;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String uniqueId(String path, String base) {
        ConfigurationSection section = file.get().getConfigurationSection(path);
        String slug = base.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_]", "");
        if (slug.isEmpty()) {
            slug = "entree";
        }
        if (section == null || !section.contains(slug)) {
            return slug;
        }
        int suffix = 2;
        while (section.contains(slug + "_" + suffix)) {
            suffix++;
        }
        return slug + "_" + suffix;
    }
}
