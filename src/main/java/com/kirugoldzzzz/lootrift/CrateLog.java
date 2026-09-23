package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.log.LogTopic;
import com.kirugoldzzzz.lootrift.common.log.NexusLog;
import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Tr;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrateLog {

    public static final String OPEN = Tr.t("Ouverture");
    public static final String BULK = Tr.t("Ouverture groupée");
    public static final String REWARD = Tr.t("Récompense remise");
    public static final String MILESTONE = Tr.t("Palier atteint");
    public static final String KEY_GRANT = Tr.t("Clés créditées");
    public static final String KEY_TAKE = Tr.t("Clés retirées");
    public static final String KEY_SET = Tr.t("Clés redéfinies");
    public static final String KEY_BUY = Tr.t("Clé achetée");
    public static final String KEY_DAILY = Tr.t("Clé quotidienne");
    public static final String KEY_PHYSICAL = Tr.t("Clés physiques remises");
    public static final String KEY_WITHDRAW = Tr.t("Clé convertie en physique");
    public static final String KEY_REFUND = Tr.t("Clé remboursée");
    public static final String PLACED = Tr.t("Caisse posée");
    public static final String REMOVED = Tr.t("Caisse retirée");
    public static final String CONFIG = Tr.t("Configuration modifiée");
    public static final String CRATE_CREATED = Tr.t("Caisse créée");
    public static final String CRATE_DELETED = Tr.t("Caisse supprimée");
    public static final String CRATE_COPIED = Tr.t("Caisse dupliquée");
    public static final String REWARD_ADDED = Tr.t("Récompense ajoutée");
    public static final String REWARD_DELETED = Tr.t("Récompense supprimée");
    public static final String REWARD_IMPORTED = Tr.t("Récompenses importées");
    public static final String FAILURE = Tr.t("Anomalie");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_QUEUE = 20_000;

    private static final ConcurrentLinkedQueue<String> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean WRITING = new AtomicBoolean();

    private static volatile Path file;
    private static volatile boolean fileEnabled;
    private static volatile boolean verbose;

    private CrateLog() {
    }

    public static void bind(Path target, boolean toFile, boolean detailed) {
        file = target;
        fileEnabled = toFile;
        verbose = detailed;
    }

    public static boolean fileEnabled() {
        return fileEnabled;
    }

    public static boolean verbose() {
        return verbose;
    }

    public static int pending() {
        return PENDING.size();
    }

    public static void player(String action, Player player, Crate crate, double amount,
                              String detail) {
        guard(() -> {
            append(action, player.getName(), crate, detail);
        });
    }

    public static void about(String action, Player actor, UUID subject, String subjectName,
                             Crate crate, double amount, String detail) {
        guard(() -> {
            append(action, actor.getName() + Tr.t(" vers ") + subjectName, crate, detail);
        });
    }

    public static void console(String action, UUID subject, String subjectName, Crate crate,
                               double amount, String detail) {
        guard(() -> {
            append(action, Tr.t("Console vers ") + subjectName, crate, detail);
        });
    }

    public static void system(String action, Crate crate, String detail) {
        guard(() -> {
            append(action, Tr.t("Système"), crate, detail);
        });
    }

    private static void guard(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException broken) {
            NexusLog.warn(LogTopic.CRATES, Tr.t("Écriture de journal impossible"), broken);
        }
    }

    public static void failure(String context, Throwable error) {
        String message = context + " : " + error.getClass().getSimpleName()
                + " " + String.valueOf(error.getMessage());
        append(FAILURE, Tr.t("Système"), null, message);
    }

    public static void warn(String context) {
        append(FAILURE, Tr.t("Système"), null, context);
    }

    public static String describe(List<CrateService.Grant> grants) {
        StringBuilder builder = new StringBuilder();
        for (CrateService.Grant granted : grants) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(granted.amount()).append("x ")
                    .append(CrateService.rewardLabel(granted.reward()))
                    .append(" [").append(granted.reward().id())
                    .append('/').append(granted.reward().rarity().id()).append(']');
        }
        return builder.isEmpty() ? Tr.t("aucune récompense") : builder.toString();
    }

    private static String line(Crate crate, String detail) {
        if (crate == null) {
            return detail == null ? "" : detail;
        }
        return detail == null || detail.isBlank()
                ? crate.id()
                : crate.id() + " | " + detail;
    }

    private static void append(String action, String actor, Crate crate, String detail) {
        if (!fileEnabled || file == null || PENDING.size() >= MAX_QUEUE) {
            return;
        }
        PENDING.add(STAMP.format(Instant.now().atZone(ZoneId.systemDefault()))
                + " | " + action
                + " | " + actor
                + " | " + (crate == null ? "-" : crate.id())
                + " | " + (detail == null ? "" : detail));
        drain();
    }

    private static void drain() {
        if (!WRITING.compareAndSet(false, true)) {
            return;
        }
        try {
            Scheduling.async(() -> {
                try {
                    flushNow();
                } finally {
                    WRITING.set(false);
                }
            });
        } catch (RuntimeException unavailable) {
            WRITING.set(false);
        }
    }

    public static void flushNow() {
        Path target = file;
        if (target == null || PENDING.isEmpty()) {
            return;
        }
        List<String> batch = new ArrayList<>();
        String entry;
        while ((entry = PENDING.poll()) != null) {
            batch.add(entry);
        }
        if (batch.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (String pending : batch) {
            text.append(pending).append(System.lineSeparator());
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException failure) {
            PENDING.addAll(batch);
            fileEnabled = false;
            report(failure);
        }
    }

    private static void report(Throwable failure) {
        NexusLog.warn(LogTopic.CRATES, Tr.t("Journal fichier indisponible, ") + PENDING.size()
                + Tr.t(" entrées gardées en mémoire"), failure);
    }
}
