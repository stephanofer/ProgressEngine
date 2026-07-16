package com.stephanofer.progressengine.award;

import com.stephanofer.progressengine.api.event.PointsAwardPrepareEvent;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.lifecycle.PaperDispatchGate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitAwardPrepareEventDispatcher implements AwardPrepareEventDispatcher {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PaperDispatchGate gate;

    public BukkitAwardPrepareEventDispatcher(JavaPlugin plugin, Logger logger) {
        this(plugin, logger, new PaperDispatchGate());
    }

    public BukkitAwardPrepareEventDispatcher(JavaPlugin plugin, Logger logger, PaperDispatchGate gate) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public CompletableFuture<PointsAwardPrepareEvent> dispatch(AwardRequest request, OperationSource source) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");
        CompletableFuture<PointsAwardPrepareEvent> result = new CompletableFuture<>();
        if (!this.gate.acceptsDispatch()) {
            result.completeExceptionally(new IllegalStateException("ProgressEngine is shutting down"));
            return result;
        }
        Runnable task = () -> {
            try {
                if (!this.gate.acceptsDispatch()) {
                    result.completeExceptionally(new IllegalStateException("ProgressEngine is shutting down"));
                    return;
                }
                PointsAwardPrepareEvent event = new PointsAwardPrepareEvent(request, source);
                event.callEvent();
                result.complete(event);
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return result;
        }
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "ProgressEngine could not schedule award prepare event during shutdown", exception);
            result.completeExceptionally(exception);
        }
        return result;
    }
}
