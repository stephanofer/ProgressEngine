package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.api.event.BalanceChangeOrigin;
import com.stephanofer.progressengine.api.event.PointsBalanceChangedEvent;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.lifecycle.PaperDispatchGate;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitRemoteBalanceEventDispatcher implements RemoteBalanceEventDispatcher {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PaperDispatchGate gate;

    public BukkitRemoteBalanceEventDispatcher(JavaPlugin plugin, Logger logger) {
        this(plugin, logger, new PaperDispatchGate());
    }

    public BukkitRemoteBalanceEventDispatcher(JavaPlugin plugin, Logger logger, PaperDispatchGate gate) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public CompletableFuture<Void> dispatch(BalanceChange change, Optional<OperationId> operationId) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(operationId, "operationId");
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!this.gate.acceptsDispatch()) {
            result.complete(null);
            return result;
        }
        Runnable task = () -> {
            try {
                if (!this.gate.acceptsDispatch()) {
                    return;
                }
                new PointsBalanceChangedEvent(change, BalanceChangeOrigin.REMOTE, operationId).callEvent();
            } catch (RuntimeException exception) {
                this.logger.log(Level.SEVERE, "A listener failed while handling remote PointsBalanceChangedEvent", exception);
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
            this.logger.log(Level.WARNING, "ProgressEngine could not schedule remote balance event during shutdown", exception);
            result.complete(null);
        }
        return result;
    }
}
