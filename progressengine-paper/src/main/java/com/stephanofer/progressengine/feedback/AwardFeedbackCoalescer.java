package com.stephanofer.progressengine.feedback;

import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AwardFeedbackCoalescer implements AutoCloseable {
    private final JavaPlugin plugin;
    private final FeedbackService feedback;
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, PendingAward> pending = new ConcurrentHashMap<>();

    public AwardFeedbackCoalescer(JavaPlugin plugin, FeedbackService feedback, Supplier<ConfigurationSnapshot> snapshotSupplier,
                                  Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void record(UUID playerId, long amount, long balance) {
        long window = this.snapshotSupplier.get().localization().awardCoalescingWindowTicks();
        if (window == 0L) {
            this.feedback.sendAward(playerId, amount, balance);
            return;
        }
        this.pending.compute(playerId, (ignored, existing) -> {
            if (existing == null) {
                PendingAward created = new PendingAward(amount, balance);
                created.task(schedule(playerId, created, window));
                return created;
            }
            try {
                existing.amount(Math.addExact(existing.amount(), amount));
                existing.balance(balance);
                return existing;
            } catch (ArithmeticException overflow) {
                flush(playerId, existing);
                PendingAward replacement = new PendingAward(amount, balance);
                replacement.task(schedule(playerId, replacement, window));
                return replacement;
            }
        });
    }

    @Override
    public void close() {
        for (PendingAward award : this.pending.values()) {
            award.cancel();
        }
        this.pending.clear();
    }

    private BukkitTask schedule(UUID playerId, PendingAward award, long window) {
        return this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            if (this.pending.remove(playerId, award)) {
                flush(playerId, award);
            }
        }, window);
    }

    private void flush(UUID playerId, PendingAward award) {
        try {
            award.cancel();
            this.feedback.sendAward(playerId, award.amount(), award.balance());
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "ProgressEngine could not flush award feedback", exception);
        }
    }

    private static final class PendingAward {
        private long amount;
        private long balance;
        private BukkitTask task;

        private PendingAward(long amount, long balance) {
            this.amount = amount;
            this.balance = balance;
        }

        long amount() {
            return this.amount;
        }

        void amount(long amount) {
            this.amount = amount;
        }

        long balance() {
            return this.balance;
        }

        void balance(long balance) {
            this.balance = balance;
        }

        void task(BukkitTask task) {
            this.task = task;
        }

        void cancel() {
            if (this.task != null) {
                this.task.cancel();
            }
        }
    }
}
