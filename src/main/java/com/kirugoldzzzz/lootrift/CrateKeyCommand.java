package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.command.CommandBase;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class CrateKeyCommand extends CommandBase {

    private static final List<String> ACTIONS =
            List.of("give", "take", "set", "physical", "physique", "all", "check");

    private final CrateService service;
    private final Wallet economy;

    public CrateKeyCommand(CrateService service, Wallet economy) {
        super("lootrift.admin.crates", false);
        this.service = service;
        this.economy = economy;
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("all")) {
            giveAll(sender, args);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("check")) {
            inspect(sender, args[1]);
            return;
        }
        if (args.length < 3) {
            Messages.send(sender, "crates.key-usage");
            return;
        }
        String action = action(args[0]);
        if (action.isEmpty()) {
            Messages.send(sender, "crates.key-usage");
            return;
        }
        Optional<Crate> found = service.crate(args[2]);
        if (found.isEmpty()) {
            Messages.send(sender, "crates.unknown", Mini.value("crate", args[2]));
            return;
        }
        Optional<UUID> target = economy.resolve(args[1]);
        if (target.isEmpty()) {
            Messages.send(sender, "general.unknown-player", Mini.value("player", args[1]));
            return;
        }
        String input = args.length > 3 ? args[3] : "1";
        int amount = Numbers.parseCount(input).orElse(-1);
        if (action.equals("set") ? amount < 0 : amount <= 0) {
            Messages.send(sender, "general.invalid-amount", Mini.value("input", input));
            return;
        }

        Crate crate = found.get();
        UUID uuid = target.get();
        String name = economy.nameOf(uuid);
        Player online = Bukkit.getPlayer(uuid);

        int applied = amount;
        switch (action) {
            case "give" -> service.giveVirtualKeys(uuid, crate, amount);
            case "take" -> {
                int before = service.virtualKeys(uuid, crate);
                applied = before - service.takeVirtualKeys(uuid, crate, amount);
            }
            case "set" -> service.setVirtualKeys(uuid, crate, amount);
            case "physique", "physical" -> {
                if (online == null) {
                    service.giveVirtualKeys(uuid, crate, amount);
                    sender.sendMessage(Mini.parse(Palette.WARNING + name + Tr.t(" est hors ligne ")
                            + Palette.MUTED + Tr.t("les clés ont été créditées en virtuel.")));
                } else {
                    Scheduling.entity(online, () -> service.givePhysicalKeys(online, crate, amount));
                }
            }
        }

        String key = action.equals("take") ? "crates.keys-taken" : "crates.keys-given";
        Messages.send(sender, key,
                Mini.value("amount", String.valueOf(applied)),
                Mini.value("player", name),
                Mini.styled("crate", crate.displayName()));
        if (online != null && !online.equals(sender) && !action.equals("give")) {
            Messages.send(online, key + "-target",
                    Mini.value("amount", String.valueOf(applied)),
                    Mini.value("player", name),
                    Mini.styled("crate", crate.displayName()));
        }
        CrateLog.console(logAction(action), uuid, name, crate, applied,
                Tr.t("via commande, ") + applied + Tr.t(" clés"));
    }

    private void giveAll(CommandSender sender, String[] args) {
        Optional<Crate> found = service.crate(args[1]);
        if (found.isEmpty()) {
            Messages.send(sender, "crates.unknown", Mini.value("crate", args[1]));
            return;
        }
        String input = args.length > 2 ? args[2] : "1";
        int amount = Numbers.parseCount(input).orElse(0);
        if (amount <= 0) {
            Messages.send(sender, "general.invalid-amount", Mini.value("input", input));
            return;
        }
        Crate crate = found.get();
        int served = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            service.giveVirtualKeys(online.getUniqueId(), crate, amount);
            served++;
        }
        Messages.send(sender, "crates.keys-given-all",
                Mini.value("amount", String.valueOf(amount)),
                Mini.value("players", String.valueOf(served)),
                Mini.styled("crate", crate.displayName()));
        CrateLog.console(CrateLog.KEY_GRANT, null, Tr.t("Tous les connectés"), crate, amount,
                served + Tr.t(" joueurs servis, ") + amount + Tr.t(" clés chacun"));
    }

    private void inspect(CommandSender sender, String name) {
        Optional<UUID> target = economy.resolve(name);
        if (target.isEmpty()) {
            Messages.send(sender, "general.unknown-player", Mini.value("player", name));
            return;
        }
        UUID uuid = target.get();
        Player online = Bukkit.getPlayer(uuid);
        Messages.send(sender, "crates.keys-header",
                Mini.value("player", economy.nameOf(uuid)));
        boolean any = false;
        for (Crate crate : service.crates()) {
            int virtual = service.virtualKeys(uuid, crate);
            int physical = online == null ? 0 : service.physicalKeys(online, crate);
            if (virtual == 0 && physical == 0) {
                continue;
            }
            any = true;
            Messages.send(sender, "crates.keys-entry",
                    Mini.styled("crate", crate.displayName()),
                    Mini.value("virtual", String.valueOf(virtual)),
                    Mini.value("physical", String.valueOf(physical)),
                    Mini.value("opened", String.valueOf(
                            service.keyRepository().opened(uuid, crate.id()))));
        }
        if (!any) {
            Messages.send(sender, "crates.keys-empty");
        }
    }

    private static String logAction(String action) {
        return switch (action) {
            case "take" -> CrateLog.KEY_TAKE;
            case "set" -> CrateLog.KEY_SET;
            case "physique" -> CrateLog.KEY_PHYSICAL;
            default -> CrateLog.KEY_GRANT;
        };
    }

    private static String action(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "give", "donner" -> "give";
            case "take", "retirer" -> "take";
            case "set", "definir" -> "set";
            case "physique", "physical" -> "physique";
            default -> "";
        };
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        boolean crateSecond = args.length > 0 && args[0].equalsIgnoreCase("all");
        boolean playerSecond = args.length > 0 && args[0].equalsIgnoreCase("check");
        return switch (args.length) {
            case 1 -> match(ACTIONS, args[0]);
            case 2 -> crateSecond ? match(service.crateIds(), args[1]) : economy.namesMatching(args[1], 40);
            case 3 -> crateSecond || playerSecond ? List.of() : match(service.crateIds(), args[2]);
            default -> List.of();
        };
    }
}
