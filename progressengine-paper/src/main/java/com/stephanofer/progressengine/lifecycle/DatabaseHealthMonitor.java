package com.stephanofer.progressengine.lifecycle;

import com.stephanofer.progressengine.persistence.OperationalSnapshot;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseHealthMonitor implements AutoCloseable {
    private final ProgressPersistence persistence;
    private final LongSupplier intervalSeconds;
    private final DelayedTaskScheduler scheduler;
    private final Consumer<HealthUpdate> listener;
    private final Logger logger;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<AutoCloseable> scheduled = new AtomicReference<>();
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    public DatabaseHealthMonitor(ProgressPersistence persistence, LongSupplier intervalSeconds,
                                 DelayedTaskScheduler scheduler, Consumer<HealthUpdate> listener,
                                 Logger logger, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.intervalSeconds = Objects.requireNonNull(intervalSeconds, "intervalSeconds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start() {
        if (!this.closed.get()) {
            scheduleNext();
        }
    }

    public Status status() {
        return new Status(
            this.running.get(),
            effectiveIntervalSeconds(),
            Optional.ofNullable(this.lastAttempt.get()),
            Optional.ofNullable(this.lastSuccess.get()),
            Optional.ofNullable(this.lastFailure.get())
        );
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        cancelScheduled();
    }

    private void scheduleNext() {
        if (this.closed.get()) {
            return;
        }
        cancelScheduled();
        try {
            AutoCloseable task = this.scheduler.schedule(this::probe, Math.multiplyExact(effectiveIntervalSeconds(), 20L));
            this.scheduled.set(task);
        } catch (RuntimeException exception) {
            if (!this.closed.get()) {
                this.logger.log(Level.WARNING, "ProgressEngine could not schedule database health probe", exception);
            }
        }
    }

    private void probe() {
        this.scheduled.set(null);
        if (this.closed.get() || !this.running.compareAndSet(false, true)) {
            return;
        }
        this.lastAttempt.set(Instant.now(this.clock));
        this.persistence.operational().snapshot().whenComplete((snapshot, failure) -> {
            try {
                HealthUpdate update = update(snapshot, failure);
                if (!this.closed.get()) {
                    this.listener.accept(update);
                }
            } catch (RuntimeException exception) {
                this.logger.log(Level.WARNING, "ProgressEngine database health listener failed", exception);
            } finally {
                this.running.set(false);
                scheduleNext();
            }
        });
    }

    private HealthUpdate update(OperationalSnapshot snapshot, Throwable failure) {
        if (failure != null) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            this.lastFailure.set(message);
            return new HealthUpdate(false, Optional.of(message));
        }
        if (snapshot.databaseHealthy()) {
            this.lastSuccess.set(Instant.now(this.clock));
            this.lastFailure.set(null);
            return new HealthUpdate(true, Optional.empty());
        }
        String message = snapshot.failureMessage().orElse("database health probe failed");
        this.lastFailure.set(message);
        return new HealthUpdate(false, Optional.of(message));
    }

    private void cancelScheduled() {
        AutoCloseable task = this.scheduled.getAndSet(null);
        if (task == null) {
            return;
        }
        try {
            task.close();
        } catch (Exception exception) {
            this.logger.log(Level.FINE, "Failed to cancel ProgressEngine database health task", exception);
        }
    }

    private long effectiveIntervalSeconds() {
        return Math.max(1L, this.intervalSeconds.getAsLong());
    }

    @FunctionalInterface
    public interface DelayedTaskScheduler {
        AutoCloseable schedule(Runnable task, long delayTicks);
    }

    public record HealthUpdate(boolean healthy, Optional<String> failureMessage) {
        public HealthUpdate {
            failureMessage = Objects.requireNonNull(failureMessage, "failureMessage");
        }
    }

    public record Status(boolean running, long intervalSeconds, Optional<Instant> lastAttempt,
                         Optional<Instant> lastSuccess, Optional<String> lastFailure) {
        public Status {
            lastAttempt = Objects.requireNonNull(lastAttempt, "lastAttempt");
            lastSuccess = Objects.requireNonNull(lastSuccess, "lastSuccess");
            lastFailure = Objects.requireNonNull(lastFailure, "lastFailure");
        }
    }
}
