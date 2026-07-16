package com.stephanofer.progressengine.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import com.stephanofer.progressengine.persistence.StoredAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

final class RemoteBalanceRefresherTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void ignoresInvalidationWhenAccountIsNotCached() {
        AtomicInteger loads = new AtomicInteger();
        BalanceStore store = store(id -> {
            loads.incrementAndGet();
            return CompletableFuture.completedFuture(account(id, 100L, 2L));
        });
        RemoteBalanceRefresher refresher = refresher(store, new ArrayList<>());

        Optional<BalanceSnapshot> refreshed = refresher.refreshCached(
            new BalanceInvalidationMessage(UUID.randomUUID(), 2L, OperationId.generate(), "server-a")
        ).join();

        assertTrue(refreshed.isEmpty());
        assertEquals(0, loads.get());
    }

    @Test
    void ignoresEqualOrOlderInvalidations() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.failedFuture(new AssertionError("loader must not run")));
        store.publish(snapshot(playerId, 100L, 3L));
        List<Dispatched> dispatched = new ArrayList<>();
        RemoteBalanceRefresher refresher = refresher(store, dispatched);

        assertTrue(refresher.refreshCached(new BalanceInvalidationMessage(playerId, 3L, OperationId.generate(), "server-a")).join().isEmpty());
        assertTrue(refresher.refreshCached(new BalanceInvalidationMessage(playerId, 2L, OperationId.generate(), "server-a")).join().isEmpty());

        assertTrue(dispatched.isEmpty());
        assertEquals(3L, store.cached(playerId).orElseThrow().revision());
    }

    @Test
    void newerInvalidationRefreshesAndDispatchesRemoteChangeWithOperationId() {
        UUID playerId = UUID.randomUUID();
        OperationId operationId = OperationId.generate();
        BalanceStore store = store(id -> CompletableFuture.completedFuture(account(id, 150L, 4L)));
        store.publish(snapshot(playerId, 100L, 3L));
        List<Dispatched> dispatched = new ArrayList<>();
        RemoteBalanceRefresher refresher = refresher(store, dispatched);

        BalanceSnapshot refreshed = refresher.refreshCached(new BalanceInvalidationMessage(playerId, 4L, operationId, "server-a"))
            .join().orElseThrow();

        assertEquals(150L, refreshed.balance());
        assertEquals(4L, store.cached(playerId).orElseThrow().revision());
        assertEquals(1, dispatched.size());
        assertEquals(50L, dispatched.getFirst().change().delta());
        assertEquals(Optional.of(operationId), dispatched.getFirst().operationId());
    }

    @Test
    void refreshForFeedbackLoadsMissingSnapshotWithoutDispatchingSyntheticChange() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.completedFuture(account(id, 75L, 2L)));
        List<Dispatched> dispatched = new ArrayList<>();
        RemoteBalanceRefresher refresher = refresher(store, dispatched);

        BalanceSnapshot refreshed = refresher.refreshForFeedback(playerId, 2L).join().orElseThrow();

        assertEquals(75L, refreshed.balance());
        assertTrue(dispatched.isEmpty());
    }

    @Test
    void reconciledSnapshotDispatchesOnlyWhenReplacingAnExistingSnapshot() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.failedFuture(new AssertionError("loader must not run")));
        List<Dispatched> dispatched = new ArrayList<>();
        RemoteBalanceRefresher refresher = refresher(store, dispatched);

        refresher.publishReconciled(snapshot(playerId, 10L, 1L)).join();
        refresher.publishReconciled(snapshot(playerId, 30L, 2L)).join();

        assertEquals(1, dispatched.size());
        assertEquals(20L, dispatched.getFirst().change().delta());
        assertTrue(dispatched.getFirst().operationId().isEmpty());
    }

    @Test
    void dispatcherFailureIsObservationalForRemoteRefresh() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.completedFuture(account(id, 200L, 2L)));
        store.publish(snapshot(playerId, 100L, 1L));
        RemoteBalanceRefresher refresher = new RemoteBalanceRefresher(
            store,
            (change, operationId) -> CompletableFuture.failedFuture(new IllegalStateException("listener failed")),
            Logger.getLogger("progressengine-test")
        );

        BalanceSnapshot refreshed = refresher.refreshCached(new BalanceInvalidationMessage(playerId, 2L, OperationId.generate(), "server-a"))
            .join().orElseThrow();

        assertEquals(200L, refreshed.balance());
        assertEquals(2L, store.cached(playerId).orElseThrow().revision());
    }

    private static RemoteBalanceRefresher refresher(BalanceStore store, List<Dispatched> dispatched) {
        return new RemoteBalanceRefresher(
            store,
            (change, operationId) -> {
                dispatched.add(new Dispatched(change, operationId));
                return CompletableFuture.completedFuture(null);
            },
            Logger.getLogger("progressengine-test")
        );
    }

    private static BalanceStore store(BalanceStore.AccountSnapshotLoader loader) {
        return new BalanceStore(
            loader,
            new ProgressEngineConfig.CacheSettings(100L, 60L, false),
            new InFlightTracker(new RuntimeLifecycle()),
            CLOCK,
            Ticker.systemTicker()
        );
    }

    private static BalanceSnapshot snapshot(UUID playerId, long balance, long revision) {
        return new BalanceSnapshot(playerId, balance, revision, CLOCK.instant());
    }

    private static StoredAccount account(UUID playerId, long balance, long revision) {
        return new StoredAccount(playerId, balance, revision, CLOCK.instant(), CLOCK.instant());
    }

    private record Dispatched(com.stephanofer.progressengine.api.transaction.BalanceChange change,
                              Optional<OperationId> operationId) {
    }
}
