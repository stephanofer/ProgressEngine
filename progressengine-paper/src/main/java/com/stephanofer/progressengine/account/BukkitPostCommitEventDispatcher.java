package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.event.BalanceChangeOrigin;
import com.stephanofer.progressengine.api.event.PointsBalanceChangedEvent;
import com.stephanofer.progressengine.api.event.PointsTransactionCommittedEvent;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitPostCommitEventDispatcher implements PostCommitEventDispatcher {
    private final JavaPlugin plugin;
    private final Logger logger;

    public BukkitPostCommitEventDispatcher(JavaPlugin plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<Void> dispatch(OperationReceipt receipt, List<BalanceChange> acceptedBalanceChanges) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(acceptedBalanceChanges, "acceptedBalanceChanges");
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                callSafely(new PointsTransactionCommittedEvent(receipt));
                for (BalanceChange change : acceptedBalanceChanges) {
                    callSafely(new PointsBalanceChangedEvent(change, BalanceChangeOrigin.LOCAL, receipt.operationId()));
                }
            } finally {
                result.complete(null);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
            return result;
        }

        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "ProgressEngine could not schedule post-commit events during shutdown", exception);
            result.complete(null);
        }
        return result;
    }

    private void callSafely(org.bukkit.event.Event event) {
        try {
            event.callEvent();
        } catch (RuntimeException exception) {
            this.logger.log(Level.SEVERE, "A listener failed while handling " + event.getEventName(), exception);
        }
    }
}
