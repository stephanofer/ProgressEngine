package com.stephanofer.progressengine.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.WorkKind;
import com.stephanofer.progressengine.lifecycle.WorkPermit;
import com.stephanofer.progressengine.persistence.BinaryUuid;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.persistence.StoredAccount;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BalanceStore implements AutoCloseable {
    private final Cache<UUID, BalanceSnapshot> snapshots;
    private final ConcurrentHashMap<UUID, CompletableFuture<BalanceSnapshot>> inFlightLoads = new ConcurrentHashMap<>();
    private final AccountSnapshotLoader loader;
    private final InFlightTracker inFlightTracker;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    public BalanceStore(ProgressPersistence persistence, ProgressEngineConfig.CacheSettings settings,
                        InFlightTracker inFlightTracker, Clock clock) {
        this(persistence.accounts()::createOrLoad, settings, inFlightTracker, clock, Ticker.systemTicker());
    }

    public BalanceStore(AccountSnapshotLoader loader, ProgressEngineConfig.CacheSettings settings,
                        InFlightTracker inFlightTracker, Clock clock, Ticker ticker) {
        this.loader = Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(settings, "settings");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(ticker, "ticker");

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(settings.maximumSize())
            .expireAfterAccess(Duration.ofSeconds(settings.expireAfterAccessSeconds()))
            .ticker(ticker);
        if (settings.recordStats()) {
            builder.recordStats();
        }
        this.snapshots = builder.build();
    }

    public Optional<BalanceSnapshot> cached(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        return Optional.ofNullable(this.snapshots.getIfPresent(validPlayerId));
    }

    public CompletableFuture<BalanceSnapshot> load(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        BalanceSnapshot existing = this.snapshots.getIfPresent(validPlayerId);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return coalescedLoad(validPlayerId);
    }

    public CompletableFuture<BalanceSnapshot> refresh(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        return coalescedLoad(validPlayerId);
    }

    public Publication publish(StoredAccount account) {
        Objects.requireNonNull(account, "account");
        return publish(new BalanceSnapshot(account.playerId(), account.balance(), account.revision(), this.clock.instant()));
    }

    public Publication publish(BalanceChange change) {
        Objects.requireNonNull(change, "change");
        return publish(new BalanceSnapshot(change.playerId(), change.balanceAfter(), change.revision(), this.clock.instant()));
    }

    public Publication publish(BalanceSnapshot incoming) {
        Objects.requireNonNull(incoming, "incoming");
        AtomicReference<BalanceSnapshot> previous = new AtomicReference<>();
        AtomicReference<BalanceSnapshot> effective = new AtomicReference<>();
        AtomicBoolean updated = new AtomicBoolean();

        this.snapshots.asMap().compute(incoming.playerId(), (playerId, current) -> {
            previous.set(current);
            if (current == null || incoming.revision() > current.revision()) {
                updated.set(true);
                effective.set(incoming);
                return incoming;
            }
            effective.set(current);
            return current;
        });

        return new Publication(Optional.ofNullable(previous.get()), effective.get(), updated.get());
    }

    public long estimatedSize() {
        return this.snapshots.estimatedSize();
    }

    public int inFlightLoads() {
        return this.inFlightLoads.size();
    }

    public CacheStats stats() {
        return this.snapshots.stats();
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.inFlightLoads.clear();
        this.snapshots.invalidateAll();
        this.snapshots.cleanUp();
    }

    private CompletableFuture<BalanceSnapshot> coalescedLoad(UUID playerId) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Balance store is closed"));
        }
        while (true) {
            CompletableFuture<BalanceSnapshot> existing = this.inFlightLoads.get(playerId);
            if (existing != null) {
                return copyOf(existing);
            }

            CompletableFuture<BalanceSnapshot> shared = new CompletableFuture<>();
            if (this.inFlightLoads.putIfAbsent(playerId, shared) == null) {
                CompletableFuture<BalanceSnapshot> physical = startPhysicalLoad(playerId);
                physical.whenComplete((value, failure) -> {
                    if (failure != null) {
                        shared.completeExceptionally(failure);
                    } else {
                        shared.complete(value);
                    }
                });
                shared.whenComplete((ignored, failure) -> this.inFlightLoads.remove(playerId, shared));
                return copyOf(shared);
            }
        }
    }

    private CompletableFuture<BalanceSnapshot> startPhysicalLoad(UUID playerId) {
        Optional<WorkPermit> acquired = this.inFlightTracker.acquire(WorkKind.LOAD);
        if (acquired.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ProgressEngine is not accepting balance loads"));
        }
        WorkPermit permit = acquired.orElseThrow();
        CompletableFuture<StoredAccount> accountFuture;
        try {
            accountFuture = Objects.requireNonNull(this.loader.load(playerId), "loader future cannot be null");
        } catch (RejectedExecutionException exception) {
            permit.close();
            return CompletableFuture.failedFuture(exception);
        } catch (RuntimeException exception) {
            permit.close();
            return CompletableFuture.failedFuture(exception);
        }

        return accountFuture.thenApply(account -> publish(account).snapshot())
            .whenComplete((ignored, failure) -> permit.close());
    }

    private static <T> CompletableFuture<T> copyOf(CompletableFuture<T> internal) {
        CompletableFuture<T> copy = new CompletableFuture<>();
        internal.whenComplete((value, failure) -> {
            if (failure != null) {
                copy.completeExceptionally(failure);
            } else {
                copy.complete(value);
            }
        });
        return copy;
    }

    @FunctionalInterface
    public interface AccountSnapshotLoader {
        CompletableFuture<StoredAccount> load(UUID playerId);
    }

    public record Publication(Optional<BalanceSnapshot> previous, BalanceSnapshot snapshot, boolean updated) {
        public Publication {
            previous = Objects.requireNonNull(previous, "previous");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
