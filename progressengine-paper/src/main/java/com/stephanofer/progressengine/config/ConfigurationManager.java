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

        EffectiveReload effective = effectiveReload(previous, loaded.snapshot());
        this.activeSnapshot.set(effective.snapshot());
        return ConfigurationReloadResult.success(effective.snapshot(), effective.changes());
    }

    private ConfigurationReloadResult failure(ConfigurationProblem problem) {
        return ConfigurationReloadResult.failure(activeSnapshot(), List.of(problem));
    }

    private static EffectiveReload effectiveReload(ConfigurationSnapshot previous, ConfigurationSnapshot candidate) {
        if (previous == null) {
            return new EffectiveReload(candidate, ConfigurationChangeSet.initial());
        }
        java.util.ArrayList<String> applied = new java.util.ArrayList<>();
        java.util.ArrayList<String> restartRequired = new java.util.ArrayList<>();
        ProgressEngineConfig candidateConfig = candidate.config();
        ProgressEngineConfig previousConfig = previous.config();

        ProgressEngineConfig effectiveConfig = new ProgressEngineConfig(
            previousConfig.serverId(),
            candidateConfig.economy(),
            previousConfig.database(),
            previousConfig.redis(),
            previousConfig.cache(),
            candidateConfig.reconciliation(),
            previousConfig.integrations(),
            candidateConfig.runtime()
        );
        if (!candidateConfig.serverId().equals(previousConfig.serverId())) restartRequired.add("server-id");
        if (!candidateConfig.database().equals(previousConfig.database())) restartRequired.add("database");
        if (!candidateConfig.redis().equals(previousConfig.redis())) restartRequired.add("redis");
        if (!candidateConfig.cache().equals(previousConfig.cache())) restartRequired.add("cache");
        if (!candidateConfig.integrations().equals(previousConfig.integrations())) restartRequired.add("integrations");
        if (!candidateConfig.economy().equals(previousConfig.economy())) applied.add("economy");
        if (!candidateConfig.reconciliation().equals(previousConfig.reconciliation())) applied.add("reconciliation");
        if (!candidateConfig.runtime().equals(previousConfig.runtime())) applied.add("runtime");
        if (!candidate.localization().equals(previous.localization())) applied.add("localization");
        if (!candidate.messages().equals(previous.messages())) applied.add("messages");

        IdentitySettings effectiveIdentity = candidate.identity();
        if (candidate.identity().offlineCacheMaximumSize() != previous.identity().offlineCacheMaximumSize()
            || candidate.identity().offlineCacheExpireAfterWriteSeconds() != previous.identity().offlineCacheExpireAfterWriteSeconds()) {
            restartRequired.add("identity.offline-cache");
            effectiveIdentity = new IdentitySettings(
                candidate.identity().parts(),
                candidate.identity().separator(),
                previous.identity().offlineCacheMaximumSize(),
                previous.identity().offlineCacheExpireAfterWriteSeconds()
            );
        }
        if (!candidate.identity().equals(previous.identity())) applied.add("identity");

        CommandSettings effectiveCommands = candidate.commands();
        if (!candidate.commands().registration().equals(previous.commands().registration())) {
            restartRequired.add("commands.registration");
            effectiveCommands = new CommandSettings(
                previous.commands().registration(),
                effectiveCommands.availability(),
                effectiveCommands.permissions(),
                effectiveCommands.pay(),
                effectiveCommands.history(),
                effectiveCommands.suggestions(),
                effectiveCommands.reasons()
            );
        }
        if (!candidate.commands().permissions().equals(previous.commands().permissions())) {
            restartRequired.add("commands.permissions");
            effectiveCommands = new CommandSettings(
                effectiveCommands.registration(),
                effectiveCommands.availability(),
                previous.commands().permissions(),
                effectiveCommands.pay(),
                effectiveCommands.history(),
                effectiveCommands.suggestions(),
                effectiveCommands.reasons()
            );
        }
        if (!candidate.commands().equals(previous.commands())) applied.add("commands");

        ConfigurationSnapshot effective = new ConfigurationSnapshot(
            candidate.revision(),
            candidate.loadedAt(),
            effectiveConfig,
            candidate.localization(),
            effectiveIdentity,
            candidate.messages(),
            effectiveCommands
        );
        if (applied.isEmpty()) applied.add("none");
        return new EffectiveReload(effective, new ConfigurationChangeSet(applied, restartRequired));
    }

    private record EffectiveReload(ConfigurationSnapshot snapshot, ConfigurationChangeSet changes) {
    }
}
