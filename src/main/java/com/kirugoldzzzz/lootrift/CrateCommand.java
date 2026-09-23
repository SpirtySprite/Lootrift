package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.command.NexusCommand;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.item.ItemReturn;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import com.kirugoldzzzz.lootrift.importer.CrateSource;
import com.kirugoldzzzz.lootrift.importer.Imported;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CrateCommand extends NexusCommand {

    private static final String PERMISSION = "lootrift.admin.crates";

    private static final List<String> ACTIONS_FR =
            List.of("admin", "give", "apercu", "ouvrir", "historique", "reload", "importer");
    private static final List<String> ACTIONS_EN =
            List.of("admin", "give", "preview", "open", "history", "reload", "import");
    private static final List<String> CRATE_ARGUMENT =
            List.of("apercu", "preview", "ouvrir", "open", "give");

    private final CrateService service;
    private final CrateOpener opener;
    private final CrateAdminMenu adminMenu;
    private final CrateEditor editor;
    private final CrateHistoryMenu historyMenu;
    private final Runnable reloadSettings;
    private final CrateImporter importer;

    public CrateCommand(CrateService service, CrateOpener opener, CrateAdminMenu adminMenu,
                        CrateHistoryMenu historyMenu, CrateEditor editor, Runnable reloadSettings,
                        CrateImporter importer) {
        super(PERMISSION, true);
        this.service = service;
        this.opener = opener;
        this.adminMenu = adminMenu;
        this.historyMenu = historyMenu;
        this.editor = editor;
        this.reloadSettings = reloadSettings;
        this.importer = importer;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (args.length == 0) {
            adminMenu.open(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "admin", "edit" -> adminMenu.open(player);
            case "historique", "history" -> historyMenu.open(player, null, () -> adminMenu.open(player));
            case "apercu", "preview" -> resolve(player, args)
                    .ifPresent(crate -> opener.preview(player, crate, null));
            case "ouvrir", "open" -> resolve(player, args).ifPresent(crate -> {
                int count = args.length > 2 ? Numbers.parseCount(args[2]).orElse(-1) : 1;
                if (count < 1) {
                    Messages.send(player, "general.invalid-amount", Mini.value("input", args[2]));
                } else if (count > 1) {
                    opener.openBulk(player, crate, count, null);
                } else {
                    opener.open(player, crate, null);
                }
            });
            case "give" -> give(player, args);
            case "import", "importer" -> importFrom(player, args);
            case "reload" -> {
                reloadSettings.run();
                editor.reload();
                Guis.success(player);
                Messages.send(player, "crates.reloaded");
            }
            default -> Messages.send(sender, "crates.admin-usage");
        }
    }

    private void give(Player player, String[] args) {
        Optional<Crate> crate = resolve(player, args);
        if (crate.isEmpty()) {
            return;
        }
        int requested = args.length > 2 ? Numbers.parseCount(args[2]).orElse(-1) : 1;
        if (requested < 1) {
            Messages.send(player, "general.invalid-amount", Mini.value("input", args[2]));
            return;
        }
        int amount = Math.min(64, requested);
        ItemReturn.give(player, List.of(CrateKeys.blockItem(crate.get(), amount)));
        Guis.success(player);
        Messages.send(player, "crates.block-given",
                Mini.styled("crate", crate.get().displayName()));
    }

    private void importFrom(Player player, String[] args) {
        Optional<CrateSource> source = args.length < 2 ? Optional.empty() : CrateSource.byId(args[1]);
        if (source.isEmpty()) {
            Messages.send(player, "crates.import-usage", Mini.value("sources", sources()));
            return;
        }
        File folder = CrateImporter.folder(source.get());
        if (!folder.isDirectory()) {
            Messages.send(player, "crates.import-missing", Mini.value("plugin", source.get().plugin()),
                    Mini.value("folder", folder.getPath()));
            return;
        }
        Messages.send(player, "crates.import-started", Mini.value("plugin", source.get().plugin()));
        Scheduling.async(() -> {
            Imported.Result result = source.get().read(folder);
            Scheduling.global(() -> {
                CrateImporter.Summary summary = importer.write(result);
                Messages.send(player, "crates.import-done",
                        Mini.value("plugin", source.get().plugin()),
                        Mini.value("crates", String.valueOf(summary.crates())),
                        Mini.value("rewards", String.valueOf(summary.rewards())),
                        Mini.value("balances", String.valueOf(summary.balances())));
                if (!summary.warnings().isEmpty()) {
                    Messages.send(player, "crates.import-warnings",
                            Mini.value("amount", String.valueOf(summary.warnings().size())));
                }
            });
        });
    }

    private static String sources() {
        return String.join(", ", CrateSource.ALL.stream().map(CrateSource::id).toList());
    }

    private Optional<Crate> resolve(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "crates.admin-usage");
            return Optional.empty();
        }
        Optional<Crate> crate = service.crate(args[1]);
        if (crate.isEmpty()) {
            Guis.deny(player);
            Messages.send(player, "crates.unknown", Mini.value("crate", args[1]));
        }
        return crate;
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>("fr".equals(Tr.language()) ? ACTIONS_FR : ACTIONS_EN);
            options.addAll(service.crateIds());
            return match(options, args[0]);
        }
        if (args.length == 2 && List.of("import", "importer").contains(args[0].toLowerCase(Locale.ROOT))) {
            return match(CrateSource.ALL.stream().map(CrateSource::id).toList(), args[1]);
        }
        if (args.length == 2 && CRATE_ARGUMENT.contains(args[0].toLowerCase(Locale.ROOT))) {
            return match(service.crateIds(), args[1]);
        }
        return List.of();
    }
}
