package com.kirugoldzzzz.lootrift;

import com.foliagui.FoliaGUI;
import com.kirugoldzzzz.lootrift.api.LootriftApi;
import com.kirugoldzzzz.lootrift.common.command.NexusCommand;
import com.kirugoldzzzz.lootrift.common.config.ConfigFile;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.platform.Telemetry;
import com.kirugoldzzzz.lootrift.common.platform.UpdateChecker;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.storage.Database;
import com.kirugoldzzzz.lootrift.common.storage.StorageManager;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Lootrift extends JavaPlugin {

    private static final long SAVE_INTERVAL_SECONDS = 30L;
    private static final int BSTATS_ID = 0;
    private static final String REPOSITORY = "SpirtySprite/Lootrift";

    private final StorageManager storage = new StorageManager();
    private Database database;
    private CrateService service;
    private CrateHolograms holograms;
    private CrateModels models;
    private final Telemetry telemetry = new Telemetry();

    @Override
    public void onEnable() {
        Scheduling.bind(this);
        ConfigFile settings = loadSettings();
        FoliaGUI.init(this);
        Guis.installTheme();
        Tr.seedLocalized(this, "crates.yml");
        ConfigFile crates = new ConfigFile(this, "crates.yml", "crates").load();

        database = new Database(this, "lootrift.db");
        try {
            database.open();
        } catch (Exception failure) {
            getLogger().severe(Tr.t("Base de données inaccessible, désactivation : ") + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        storage.attach(database);
        CrateKeyRepository keys = storage.register(new CrateKeyRepository(database));
        CratePlacementRepository placements = storage.register(new CratePlacementRepository(database));
        CrateUniqueRepository uniques = storage.register(new CrateUniqueRepository(database));
        CrateHistoryRepository history = storage.register(new CrateHistoryRepository(database));

        Wallet wallet = new Wallet();
        service = new CrateService(keys, placements, history, uniques, wallet);
        service.logFile(new File(getDataFolder(), "logs/caisses.log").toPath());
        holograms = new CrateHolograms(service);
        models = new CrateModels(service);
        CrateEditor editor = new CrateEditor(crates, service);
        editor.bind(holograms);
        editor.bind(models);
        service.configure(crates.get());

        CrateAnimator animator = new CrateAnimator(service);
        CrateOpener opener = new CrateOpener(service, animator);
        CrateRewardMenu rewardMenu = new CrateRewardMenu(service);
        rewardMenu.bind(opener);
        animator.bind(rewardMenu);
        opener.bind(rewardMenu);
        CrateBulkAnimator bulkAnimator = new CrateBulkAnimator(service);
        bulkAnimator.bind(rewardMenu);
        opener.bind(bulkAnimator);
        opener.bind(new CratePreviewMenu(service, opener));
        CrateHistoryMenu historyMenu = new CrateHistoryMenu(service);
        CrateRarityMenu rarityMenu = new CrateRarityMenu();
        CrateRewardListMenu rewardListMenu = new CrateRewardListMenu(service, editor,
                new CrateRewardEditorMenu(service, editor, rarityMenu));
        CrateEditorMenu editorMenu = new CrateEditorMenu(service, editor, rewardListMenu,
                new CrateAnimationMenu(editor), rarityMenu, new CrateEffectsMenu(service, editor),
                new CrateStatsMenu(service));
        CrateAdminMenu adminMenu = new CrateAdminMenu(service, editor, editorMenu,
                new CratePlacementMenu(service, holograms, models), new CrateKeyAdminMenu(service, wallet), holograms);

        bind("crate", new CrateCommand(service, opener, adminMenu, historyMenu, editor, this::loadSettings,
                new CrateImporter(service, editor)));
        bind("cle", new CrateKeyCommand(service, wallet));
        getServer().getPluginManager().registerEvents(new CrateListener(service, opener, holograms, models), this);

        storage.start(SAVE_INTERVAL_SECONDS);
        service.startMaintenance();
        holograms.startAmbient();
        Scheduling.global(() -> {
            holograms.refreshAll();
            models.refreshAll();
        });
        if (!wallet.available()) {
            getLogger().info(Tr.t("Vault est absent : l'achat de clés et les récompenses en argent sont inactifs."));
        }
        getServer().getServicesManager().register(LootriftApi.class, new LootriftService(service, opener), this,
                ServicePriority.Normal);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new LootriftExpansion(this, service).register();
        }
        if (settings.get().getBoolean("update-checker", true)) {
            new UpdateChecker(this, REPOSITORY, "lootrift.admin.crates").start();
        }
        telemetry.start(this, BSTATS_ID, Map.of(
                "crates", () -> String.valueOf(service.crateCount()),
                "economy", () -> wallet.available() ? "Vault" : "none"));
    }

    private ConfigFile loadSettings() {
        ConfigFile settings = new ConfigFile(this, "config.yml").load();
        Tr.configure(this, settings.get().getString("language", "en"));
        new ConfigFile(this, "lang/messages_fr.yml").load();
        Messages.load(new ConfigFile(this, Tr.messagesFile(this)).load().get());
        return settings;
    }

    @Override
    public void onDisable() {
        telemetry.stop();
        getServer().getServicesManager().unregisterAll(this);
        CrateLog.flushNow();
        if (service != null) {
            service.stopMaintenance();
        }
        if (holograms != null) {
            holograms.stopAmbient();
        }
        if (models != null) {
            models.removeAll();
        }
        storage.shutdown();
        if (database != null) {
            database.close();
        }
        FoliaGUI.shutdown();
    }

    private void bind(String name, NexusCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning(Tr.t("La commande ") + name + Tr.t(" est absente du plugin.yml"));
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
