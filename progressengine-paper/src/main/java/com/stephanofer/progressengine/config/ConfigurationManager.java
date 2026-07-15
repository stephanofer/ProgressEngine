package com.stephanofer.progressengine.config;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigurationManager implements AutoCloseable {
    private final ConfigurationLoader loader;
    private final Executor executor;
    private final AtomicReference<ConfigurationSnapshot> activeSnapshot = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<ConfigurationReloadResult>> inFlightReload = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ConfigurationManager(ConfigurationLoader loader, Executor executor) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Optional<ConfigurationSnapshot> activeSnapshot() {
        return Optional.ofNullable(this.activeSnapshot.get());
    }

    public CompletableFuture<ConfigurationReloadResult> reloadAsync() {
        while (true) {
            CompletableFuture<ConfigurationReloadResult> current = this.inFlightReload.get();
            if (current != null) {
                return current;
            }

            CompletableFuture<ConfigurationReloadResult> created = new CompletableFuture<>();
            if (!this.inFlightReload.compareAndSet(null, created)) {
                continue;
            }

            try {
                this.executor.execute(() -> completeReload(created));
            } catch (RejectedExecutionException exception) {
                this.inFlightReload.compareAndSet(created, null);
                created.completeExceptionally(exception);
            }
            return created;
        }
    }

    @Override
    public void close() {
        this.closed.set(true);
    }

    private void completeReload(CompletableFuture<ConfigurationReloadResult> future) {
        try {
            future.complete(reloadNow());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        } finally {
            this.inFlightReload.compareAndSet(future, null);
        }
    }

    private ConfigurationReloadResult reloadNow() {
        if (this.closed.get()) {
            return failure(new ConfigurationProblem("runtime", "configuration manager is closed"));
        }

        ConfigurationSnapshot previous = this.activeSnapshot.get();
        long nextRevision = previous == null ? 1L : previous.revision() + 1L;
        LoadedConfiguration loaded;
        try {
            loaded = this.loader.load(nextRevision);
        } catch (ConfigurationLoadException exception) {
            return ConfigurationReloadResult.failure(activeSnapshot(), exception.problems());
        }

        if (this.closed.get()) {
            return failure(new ConfigurationProblem("runtime", "configuration manager closed before publication"));
        }

        try {
            this.loader.persist(loaded);
        } catch (IOException exception) {
            return failure(new ConfigurationProblem("config.yml", "could not persist validated configuration"));
        }

        if (this.closed.get()) {
            return failure(new ConfigurationProblem("runtime", "configuration manager closed before publication"));
        }

        this.activeSnapshot.set(loaded.snapshot());
        return ConfigurationReloadResult.success(loaded.snapshot());
    }

    private ConfigurationReloadResult failure(ConfigurationProblem problem) {
        return ConfigurationReloadResult.failure(activeSnapshot(), List.of(problem));
    }
}
