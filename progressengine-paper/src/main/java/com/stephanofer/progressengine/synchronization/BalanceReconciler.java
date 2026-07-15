package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.PlayerLifecycleCoordinator;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.persistence.StoredAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class BalanceReconciler {
    private final ProgressPersistence persistence;
    private final BalanceStore balanceStore;
    private final PlayerLifecycleCoordinator playerLifecycle;
    private final RemoteBalanceRefresher refresher;
    private final Supplier<ProgressEngineConfig> configSupplier;
    private final Logger logger;
    private final Clock clock;

    public BalanceReconciler(ProgressPersistence persistence, BalanceStore balanceStore,
                             PlayerLifecycleCoordinator playerLifecycle, RemoteBalanceRefresher refresher,
                             Supplier<ProgressEngineConfig> configSupplier, Logger logger, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.playerLifecycle = Objects.requireNonNull(playerLifecycle, "playerLifecycle");
        this.refresher = Objects.requireNonNull(refresher, "refresher");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletableFuture<Result> reconcile() {
        Set<UUID> readyPlayers = this.playerLifecycle.readyPlayerIds();
        if (readyPlayers.isEmpty()) {
            return CompletableFuture.completedFuture(new Result(Instant.now(this.clock), 0, 0, 0));
        }
        List<List<UUID>> batches = batches(readyPlayers, this.configSupplier.get().reconciliation().batchSize());
        ReconciliationAccumulator accumulator = new ReconciliationAccumulator();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (List<UUID> batch : batches) {
            chain = chain.thenCompose(ignored -> reconcileBatch(batch, accumulator));
        }
        return chain.thenApply(ignored -> new Result(Instant.now(this.clock), accumulator.checked, accumulator.changed, accumulator.published));
    }

    private CompletableFuture<Void> reconcileBatch(List<UUID> batch, ReconciliationAccumulator accumulator) {
        return this.persistence.accounts().loadRevisions(batch).thenCompose(revisions -> {
            accumulator.checked += batch.size();
            List<UUID> changed = new ArrayList<>();
            for (UUID playerId : batch) {
                Long databaseRevision = revisions.get(playerId);
                if (databaseRevision == null) {
                    this.logger.warning("Ready player has no ProgressEngine account during reconciliation: " + playerId);
                    continue;
                }
                this.balanceStore.cached(playerId).ifPresentOrElse(snapshot -> {
                    if (databaseRevision > snapshot.revision()) {
                        changed.add(playerId);
                    } else if (databaseRevision < snapshot.revision()) {
                        this.logger.warning("ProgressEngine local snapshot is ahead of MySQL for " + playerId
                            + ": local=" + snapshot.revision() + ", mysql=" + databaseRevision);
                    }
                }, () -> changed.add(playerId));
            }
            accumulator.changed += changed.size();
            if (changed.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return this.persistence.accounts().loadAccounts(changed).thenCompose(accounts -> publishAccounts(accounts, changed, accumulator));
        });
    }

    private CompletableFuture<Void> publishAccounts(Map<UUID, StoredAccount> accounts, List<UUID> requested,
                                                    ReconciliationAccumulator accumulator) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (UUID playerId : requested) {
            StoredAccount account = accounts.get(playerId);
            if (account == null) {
                this.logger.warning("Changed account disappeared during ProgressEngine reconciliation: " + playerId);
                continue;
            }
            BalanceSnapshot snapshot = new BalanceSnapshot(account.playerId(), account.balance(), account.revision(), Instant.now(this.clock));
            chain = chain.thenCompose(ignored -> this.refresher.publishReconciled(snapshot).thenAccept(published -> {
                if (published.isPresent()) {
                    accumulator.published++;
                }
            }));
        }
        return chain;
    }

    private static List<List<UUID>> batches(Collection<UUID> players, int batchSize) {
        List<UUID> ids = List.copyOf(players);
        List<List<UUID>> batches = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += batchSize) {
            batches.add(ids.subList(offset, Math.min(ids.size(), offset + batchSize)));
        }
        return batches;
    }

    public record Result(Instant completedAt, int checkedPlayers, int changedPlayers, int publishedSnapshots) {
    }

    private static final class ReconciliationAccumulator {
        private int checked;
        private int changed;
        private int published;

        private ReconciliationAccumulator() {
        }
    }
}
