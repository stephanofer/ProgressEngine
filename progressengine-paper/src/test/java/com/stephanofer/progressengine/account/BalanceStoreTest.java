package com.stephanofer.progressengine.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import com.stephanofer.progressengine.lifecycle.RuntimeState;
import com.stephanofer.progressengine.persistence.StoredAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BalanceStoreTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void publishesOnlyNewerRevisions() {
        BalanceStore store = store(playerId -> CompletableFuture.failedFuture(new AssertionError("loader must not run")));
        UUID playerId = UUID.randomUUID();
        BalanceSnapshot revisionTwo = new BalanceSnapshot(playerId, 20L, 2L, CLOCK.instant());
        BalanceSnapshot revisionOne = new BalanceSnapshot(playerId, 10L, 1L, CLOCK.instant());

        assertTrue(store.publish(revisionTwo).updated());
        BalanceStore.Publication stale = store.publish(revisionOne);
        BalanceStore.Publication equal = store.publish(revisionTwo);

        assertFalse(stale.updated());
        assertFalse(equal.updated());
        assertEquals(revisionTwo, store.cached(playerId).orElseThrow());
    }

    @Test
    void distinguishesMissingSnapshotFromLoadedZeroBalance() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.completedFuture(account(id, 0L, 0L)));

        assertTrue(store.cached(playerId).isEmpty());
        BalanceSnapshot loaded = store.load(playerId).join();

        assertEquals(0L, loaded.balance());
        assertEquals(0L, loaded.revision());
        assertEquals(loaded, store.cached(playerId).orElseThrow());
    }

    @Test
    void coalescesConcurrentLoadsAndExternalCancellationDoesNotCancelSharedLoad() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger physicalLoads = new AtomicInteger();
        CompletableFuture<StoredAccount> pending = new CompletableFuture<>();
        BalanceStore store = store(id -> {
            physicalLoads.incrementAndGet();
            return pending;
        });

        CompletableFuture<BalanceSnapshot> first = store.refresh(playerId);
        CompletableFuture<BalanceSnapshot> second = store.load(playerId);

        assertEquals(1, physicalLoads.get());
        assertTrue(first.cancel(true));
        pending.complete(account(playerId, 15L, 1L));

        assertEquals(15L, second.join().balance());
        assertEquals(0, store.inFlightLoads());
    }

    @Test
    void slowLoadCannotReplaceNewerPublishedSnapshot() {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<StoredAccount> pending = new CompletableFuture<>();
        BalanceStore store = store(id -> pending);

        CompletableFuture<BalanceSnapshot> refresh = store.refresh(playerId);
        BalanceSnapshot newer = new BalanceSnapshot(playerId, 30L, 2L, CLOCK.instant());
        store.publish(newer);
        pending.complete(account(playerId, 10L, 1L));

        assertEquals(newer, refresh.join());
        assertEquals(newer, store.cached(playerId).orElseThrow());
    }

    @Test
    void refreshAtLeastRetriesAfterSharedLoadReturnsOlderRevision() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger physicalLoads = new AtomicInteger();
        BalanceStore store = store(id -> {
            int attempt = physicalLoads.incrementAndGet();
            return CompletableFuture.completedFuture(attempt == 1
                ? account(id, 10L, 1L)
                : account(id, 20L, 2L));
        });

        BalanceSnapshot loaded = store.refreshAtLeast(playerId, 2L).join();

        assertEquals(2L, loaded.revision());
        assertEquals(2, physicalLoads.get());
    }

    @Test
    void refreshAtLeastFailsInsteadOfRetryingForeverForFutureRevision() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(id -> CompletableFuture.completedFuture(account(id, 10L, 1L)));

        assertThrows(CompletionException.class, () -> store.refreshAtLeast(playerId, 99L).join());
    }

    @Test
    void expirationRemovesSnapshotInsteadOfReturningZero() {
        MutableTicker ticker = new MutableTicker();
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(
            id -> CompletableFuture.completedFuture(account(id, 0L, 0L)),
            new ProgressEngineConfig.CacheSettings(10L, 1L, false),
            new RuntimeLifecycle(),
            ticker
        );

        store.publish(new BalanceSnapshot(playerId, 50L, 1L, CLOCK.instant()));
        ticker.advance(Duration.ofSeconds(2L));

        assertTrue(store.cached(playerId).isEmpty());
    }

    @Test
    void rejectsNewLoadsDuringShutdownWithoutPublishingZero() {
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();
        lifecycle.transitionTo(RuntimeState.SHUTTING_DOWN);
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store(
            id -> CompletableFuture.completedFuture(account(id, 0L, 0L)),
            settings(),
            lifecycle,
            Ticker.systemTicker()
        );

        assertThrows(CompletionException.class, () -> store.refresh(playerId).join());
        assertTrue(store.cached(playerId).isEmpty());
    }

    private static BalanceStore store(BalanceStore.AccountSnapshotLoader loader) {
        return store(loader, settings(), new RuntimeLifecycle(), Ticker.systemTicker());
    }

    private static BalanceStore store(BalanceStore.AccountSnapshotLoader loader, ProgressEngineConfig.CacheSettings settings,
                                      RuntimeLifecycle lifecycle, Ticker ticker) {
        return new BalanceStore(loader, settings, new InFlightTracker(lifecycle), CLOCK, ticker);
    }

    private static ProgressEngineConfig.CacheSettings settings() {
        return new ProgressEngineConfig.CacheSettings(100L, 60L, true);
    }

    private static StoredAccount account(UUID playerId, long balance, long revision) {
        return new StoredAccount(playerId, balance, revision, CLOCK.instant(), CLOCK.instant());
    }

    private static final class MutableTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return this.nanos;
        }

        void advance(Duration duration) {
            this.nanos += duration.toNanos();
        }
    }
}
