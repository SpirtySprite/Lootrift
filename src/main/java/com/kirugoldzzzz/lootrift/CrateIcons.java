package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import com.kirugoldzzzz.lootrift.common.item.ItemNames;
import com.kirugoldzzzz.lootrift.common.text.Card;
import com.kirugoldzzzz.lootrift.common.text.Lore;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class CrateIcons {

    private static final ThreadLocal<SimpleDateFormat> STAMP =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("dd/MM HH:mm"));

    private static final Map<CrateRarity, ItemStack> PANES = new ConcurrentHashMap<>();

    private static final Supplier<ItemStack> MYSTERY = memo(() -> renamed(
            new ItemStack(Material.BARRIER), Palette.MUTED + "?", List.of()));
    private static final Supplier<ItemStack> POINTER_DOWN = memo(() -> renamed(
            new ItemStack(Material.HOPPER), Palette.ACCENT + "▼", List.of()));
    private static final Supplier<ItemStack> POINTER_UP = memo(() -> renamed(
            new ItemStack(Material.HOPPER), Palette.ACCENT + "▲", List.of()));

    private static Supplier<ItemStack> memo(Supplier<ItemStack> source) {
        return new Supplier<>() {

            private volatile ItemStack built;

            @Override
            public ItemStack get() {
                ItemStack cached = built;
                if (cached == null) {
                    cached = source.get();
                    built = cached;
                }
                return cached;
            }
        };
    }

    private CrateIcons() {
    }

    public static ItemStack card(CrateReward reward) {
        CrateRarity rarity = reward.rarity();
        return renamed(reward.display(), rarity.title(rewardName(reward)), List.of(rarity.badge()));
    }

    public static ItemStack winner(CrateReward reward, int amount) {
        CrateRarity rarity = reward.rarity();
        Card card = Card.of(rarity.hex())
                .tag(Tr.t("Récompense ") + rarity.displayName())
                .section(Tr.t("Gain"))
                .stat(rarity.icon(), Tr.t("Rareté"), rarity.displayName())
                .count(Card.AMOUNT, Tr.t("Quantité"), amount);
        if (reward.hasMoney()) {
            card.money(Tr.t("Bonus"), reward.money());
        }
        if (reward.hasCommands()) {
            card.blank().note(Palette.SECONDARY, Card.STAR, Tr.t("Récompense spéciale activée"));
        }
        return renamed(reward.itemFor(amount), rarity.title(rewardName(reward)),
                card.blank().note(rarity.color(), rarity.icon(), rarity.color() + "<b>" + Card.small(Tr.t("Gagné !")) + "</b>")
                        .build());
    }

    public static ItemStack preview(Crate crate, CrateReward reward, boolean unlocked) {
        CrateRarity rarity = reward.rarity();
        double percent = crate.chanceOf(reward);
        Card card = Card.of(rarity.hex())
                .tag(Tr.t("Récompense ") + rarity.displayName())
                .section(Tr.t("Récompense"))
                .stat(rarity.icon(), Tr.t("Rareté"), rarity.displayName())
                .stat(Card.CHANCE, Tr.t("Chance"), chance(percent) + odds(percent))
                .stat(Card.AMOUNT, Tr.t("Quantité"), reward.amountLabel());
        if (reward.hasMoney()) {
            card.money(Tr.t("Bonus"), reward.money());
        }
        if (reward.hasCommands() || reward.solo() || reward.announced() || reward.restricted()) {
            card.blank();
        }
        if (reward.hasCommands()) {
            card.note(Palette.SECONDARY, Card.STAR, Tr.t("Déclenche une récompense spéciale"));
        }
        if (reward.solo()) {
            card.note(Palette.WARNING, "★", Tr.t("Unique, une seule fois par joueur"));
        }
        if (reward.announced()) {
            card.note(rarity.color(), Card.FLAG, Tr.t("Annoncé à tout le serveur"));
        }
        if (reward.restricted()) {
            if (unlocked) {
                card.note(Palette.SUCCESS, Palette.CHECK, Tr.t("Débloqué pour vous"));
            } else {
                card.deny("Requiert " + reward.permission());
            }
        }
        return renamed(reward.display(), rarity.title(rewardName(reward)), card.build());
    }

    static String odds(double percent) {
        if (percent >= 99.95D) {
            return " (garanti)";
        }
        if (percent <= 0.0D || percent >= 50.0D) {
            return "";
        }
        return " (1 sur " + Numbers.count(Math.round(100.0D / percent)) + ")";
    }

    private static String rewardName(CrateReward reward) {
        return Mini.plain(ItemNames.of(reward.display())).replace("<", "");
    }

    public static ItemStack editorReward(Crate crate, CrateReward reward) {
        CrateRarity rarity = reward.rarity();
        Lore lore = Lore.create()
                .blank()
                .highlight(Tr.t("Identifiant"), reward.id())
                .entry(Tr.t("Rareté"), rarity.colored(rarity.displayName()))
                .entry(Tr.t("Poids"), reward.weight())
                .highlight(Tr.t("Chance"), chance(crate.chanceOf(reward)))
                .entry(Tr.t("Quantité"), reward.amountLabel());
        if (reward.hasMoney()) {
            lore.money(Tr.t("Argent"), reward.money());
        }
        if (reward.hasCommands()) {
            lore.count(Tr.t("Commandes"), reward.commands().size());
        }
        if (reward.restricted()) {
            lore.entry(Tr.t("Permission"), reward.permission());
        }
        if (reward.solo()) {
            lore.hint(Tr.t("Unique par joueur"));
        }
        if (!reward.giveItem()) {
            lore.warn(Tr.t("L'objet n'est pas remis"));
        }
        return renamed(reward.display(),
                rarity.heading(Mini.plain(ItemNames.of(reward.display()))),
                lore.blank()
                        .click(Tr.t("Clic gauche"), "modifier")
                        .denyClick(Tr.t("Shift + clic droit"), "supprimer")
                        .build());
    }

    public static ItemStack crate(Crate crate, int physical, int virtual, boolean allowed) {
        Card card = Card.of(Palette.PRIMARY_HEX)
                .tag(Tr.t("Caisse"))
                .section(Tr.t("Contenu"))
                .count(Card.AMOUNT, Tr.t("Récompenses"), crate.rewards().size())
                .stat(Card.STAR, Tr.t("Animation"), crate.animation().displayName());
        if (crate.rolls() > 1) {
            card.count(Card.CHANCE, Tr.t("Tirages par ouverture"), crate.rolls());
        }
        card.section(Tr.t("Vos clés"))
                .count(Card.DONE, Tr.t("En main"), physical)
                .count(Card.DONE, Tr.t("Virtuelles"), virtual)
                .blank();
        int total = physical + virtual;
        if (!allowed) {
            card.deny(Tr.t("Caisse réservée"));
        } else if (crate.isEmpty()) {
            card.deny(Tr.t("Aucune récompense configurée"));
        } else {
            if (total > 0) {
                card.click(Tr.t("Clic gauche"), Tr.t("pour ouvrir une caisse"));
            } else {
                card.deny(Tr.t("Aucune clé disponible"));
            }
            card.click(Tr.t("Clic droit"), Tr.t("pour voir les récompenses"));
        }
        return renamed(crate.icon(), Palette.heading(crate.displayName()), card.build());
    }

    public static ItemStack editorCrate(Crate crate, int placements, int circulation, int opened) {
        Lore lore = Lore.create()
                .blank()
                .highlight(Tr.t("Identifiant"), crate.id())
                .count(Tr.t("Récompenses"), crate.rewards().size())
                .entry(Tr.t("Animation"), crate.animation().displayName())
                .entry(Tr.t("Bloc"), crate.block().name())
                .blank()
                .count(Tr.t("Caisses posées"), placements)
                .count(Tr.t("Clés virtuelles en circulation"), circulation)
                .count(Tr.t("Ouvertures enregistrées"), opened);
        if (crate.pityEnabled()) {
            lore.blank().entry(Tr.t("Pitié après"), crate.pityAfter() + " ouvertures");
            lore.entry(Tr.t("Palier garanti"), crate.pityFloor().colored(crate.pityFloor().displayName()));
        }
        if (crate.permission() != null && !crate.permission().isBlank()) {
            lore.entry(Tr.t("Permission"), crate.permission());
        }
        if (crate.throttled()) {
            lore.entry(Tr.t("Délai entre ouvertures"), crate.cooldownSeconds() + "s");
        }
        return renamed(crate.icon(), Palette.heading(crate.displayName()), lore.blank()
                .click(Tr.t("Clic gauche"), Tr.t("modifier la caisse"))
                .click(Tr.t("Shift + clic gauche"), Tr.t("dupliquer la caisse"))
                .denyClick(Tr.t("Shift + clic droit"), Tr.t("supprimer la caisse"))
                .build());
    }

    public static ItemStack pane(CrateRarity rarity) {
        return PANES.computeIfAbsent(rarity, tier -> renamed(new ItemStack(tier.pane()),
                tier.colored(tier.displayName()), List.of())).clone();
    }

    public static ItemStack mystery() {
        return MYSTERY.get().clone();
    }

    public static ItemStack pointer(boolean down) {
        return (down ? POINTER_DOWN : POINTER_UP).get().clone();
    }

    public static ItemStack pull(CratePull pull, String crateName) {
        CrateRarity rarity = pull.rarity();
        Card card = Card.of(rarity.hex())
                .tag(Tr.t("Tirage"))
                .section(Tr.t("Gain"))
                .stat(Card.FLAG, Tr.t("Caisse"), Mini.plain(Mini.label(crateName)))
                .stat(rarity.icon(), Tr.t("Rareté"), rarity.displayName())
                .count(Card.AMOUNT, Tr.t("Quantité"), pull.amount());
        if (pull.hasMoney()) {
            card.money(Tr.t("Argent"), pull.money());
        }
        card.blank().note(Palette.MUTED, Card.TIME, STAMP.get().format(new Date(pull.at())));
        return renamed(new ItemStack(rarity.pane()), rarity.title(pull.rewardName().replace("<", "")), card.build());
    }

    public static ItemStack key(Crate crate, int amount) {
        ItemStack key = CrateKeys.physicalKey(crate, amount);
        ItemMeta meta = key.getItemMeta();
        if (meta != null && !meta.hasDisplayName()) {
            meta.displayName(Mini.label(Palette.heading(Tr.t("Clé ") + crate.displayName())));
            meta.lore(Mini.labels(Card.of(Palette.PRIMARY_HEX)
                    .tag(Tr.t("Clé de caisse"))
                    .section(Tr.t("Description"))
                    .line(Tr.t("Ouvre une caisse ") + crate.displayName() + Palette.TEXT + ".")
                    .blank()
                    .click(Tr.t("Clic droit"), Tr.t("sur la caisse pour l'ouvrir"))
                    .build()));
            key.setItemMeta(meta);
        }
        return key;
    }

    public static String chance(double percent) {
        if (percent >= 1.0D) {
            return Numbers.plain(percent).replace(",", " ") + "%";
        }
        return String.format(java.util.Locale.US, "%.3f", percent) + "%";
    }

    public static ItemStack renamed(ItemStack base, String name, List<String> lore) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        meta.displayName(Mini.label(name));
        if (!lore.isEmpty()) {
            meta.lore(new ArrayList<>(Mini.labels(lore)));
        } else {
            meta.lore(null);
        }
        copy.setItemMeta(meta);
        return copy;
    }
}
