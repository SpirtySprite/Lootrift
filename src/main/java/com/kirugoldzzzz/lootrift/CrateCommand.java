package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.command.NexusCommand;
import com.kirugoldzzzz.lootrift.common.gui.Guis;
import com.kirugoldzzzz.lootrift.common.item.ItemReturn;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CrateCommand extends NexusCommand {

    private static final String PERMISSION = "lootrift.admin.crates";

    private static final List<String> ACTIONS =
            List.of("admin", "give", "apercu", "ouvrir", "historique", "reload");
    private static final List<String> CRATE_ARGUMENT =
            List.of("apercu", "preview", "ouvrir", "open", "give");

    private final CrateService service;
    private final CrateOpener opener;
    private final CrateAdminMenu adminMenu;
    private final CrateEditor editor;
    private final CrateHistoryMenu historyMenu;

    public CrateCommand(CrateService service, CrateOpener opener, CrateAdminMenu adminMenu,
                        CrateHistoryMenu historyMenu, CrateEditor editor) {
        super(PERMISSION, true);
        this.service = service;
        this.opener = opener;
        this.adminMenu = adminMenu;
        this.historyMenu = historyMenu;
        this.editor = editor;
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
            case "reload" -> {
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
            List<String> options = new ArrayList<>(ACTIONS);
            options.addAll(service.crateIds());
            return match(options, args[0]);
        }
        if (args.length == 2 && CRATE_ARGUMENT.contains(args[0].toLowerCase(Locale.ROOT))) {
            return match(service.crateIds(), args[1]);
        }
        return List.of();
    }
}
