package com.stephanofer.progressengine.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.persistence.StoredAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

final class PlayerLifecycleCoordinatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
    private static final ProgressEngineConfig.CacheSettings CACHE_SETTINGS = new ProgressEngineConfig.CacheSettings(100L, 60L, false);

    @Test
    void cachedSnapshotDoesNotMeanPlayerIsReady() {
        UUID playerId = UUID.randomUUID();
        TestRuntime runtime = runtime(id -> CompletableFuture.completedFuture(account(id, 0L, 0L)));

        runtime.store.publish(new BalanceSnapshot(playerId, 25L, 1L, CLOCK.instant()));

        assertTrue(runtime.store.cached(playerId).isPresent());
        assertFalse(runtime.coordinator.isReady(playerId));
        assertEquals(0, runtime.readyEvents.get());
    }

    @Test
    void marksReadyAfterBalanceNameSettingsAndOptionalBoostersComplete() {
        UUID playerId = UUID.randomUUID();
        TestRuntime runtime = runtime(id -> CompletableFuture.completedFuture(account(id, 0L, 0L)));

        runtime.coordinator.startSession(playerId, "Vendimia", () -> true);

        assertTrue(runtime.coordinator.isReady(playerId));
        assertEquals(1, runtime.readyEvents.get());
        assertEquals(playerId, runtime.lastNameUpdate.get());
    }

    @Test
    void preloginRefreshIsReusedBySession() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger physicalLoads = new AtomicInteger();
        CompletableFuture<StoredAccount> pending = new CompletableFuture<>();
        TestRuntime runtime = runtime(id -> {
            physicalLoads.incrementAndGet();
            return pending;
        });

        runtime.coordinator.preload(playerId);
        runtime.coordinator.startSession(playerId, "Vendimia", () -> true);

        assertEquals(1, physicalLoads.get());
        assertFalse(runtime.coordinator.isReady(playerId));

        pending.complete(account(playerId, 10L, 1L));

        assertTrue(runtime.coordinator.isReady(playerId));
        assertEquals(1, runtime.readyEvents.get());
    }

    @Test
    void quitPreventsLateReadyPublication() {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<StoredAccount> pending = new CompletableFuture<>();
        TestRuntime runtime = runtime(id -> pending);

        runtime.coordinator.startSession(playerId, "Vendimia", () -> true);
        runtime.coordinator.quit(playerId);
        pending.complete(account(playerId, 10L, 1L));

        assertFalse(runtime.coordinator.isReady(playerId));
        assertEquals(0, runtime.readyEvents.get());
    }

    @Test
    void activeBoostersGateReadiness() {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<Void> boostersLoad = new CompletableFuture<>();
        AtomicBoolean boostersReady = new AtomicBoolean();
        TestRuntime runtime = runtime(
            id -> CompletableFuture.completedFuture(account(id, 0L, 0L)),
            Optional.of(new PlayerLifecycleCoordinator.PlayerBoostersReadiness() {
                @Override
                public CompletableFuture<Void> load(UUID id) {
                    return boostersLoad;
                }

                @Override
                public boolean isReady(UUID id) {
                    return boostersReady.get();
                }
            })
        );

        runtime.coordinator.startSession(playerId, "Vendimia", () -> true);

        assertFalse(runtime.coordinator.isReady(playerId));
        assertEquals(0, runtime.readyEvents.get());

        boostersReady.set(true);
        boostersLoad.complete(null);

        assertTrue(runtime.coordinator.isReady(playerId));
        assertEquals(1, runtime.readyEvents.get());
    }

    @Test
    void settingsAreRevalidatedBeforeReadyPublication() {
        UUID playerId = UUID.randomUUID();
        AtomicBoolean settingsReady = new AtomicBoolean(true);
        CompletableFuture<StoredAccount> pending = new CompletableFuture<>();
        TestRuntime runtime = runtime(id -> pending, Optional.empty(), settingsReady);

        runtime.coordinator.startSession(playerId, "Vendimia", () -> true);
        settingsReady.set(false);
        pending.complete(account(playerId, 10L, 1L));

        assertFalse(runtime.coordinator.isReady(playerId));
        assertEquals(0, runtime.readyEvents.get());
    }

    private static TestRuntime runtime(BalanceStore.AccountSnapshotLoader loader) {
        return runtime(loader, Optional.empty());
    }

    private static TestRuntime runtime(BalanceStore.AccountSnapshotLoader loader,
                                       Optional<PlayerLifecycleCoordinator.PlayerBoostersReadiness> boosters) {
        return runtime(loader, boosters, new AtomicBoolean(true));
    }

    private static TestRuntime runtime(BalanceStore.AccountSnapshotLoader loader,
                                       Optional<PlayerLifecycleCoordinator.PlayerBoostersReadiness> boosters,
                                       AtomicBoolean settingsReady) {
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();
        InFlightTracker inFlightTracker = new InFlightTracker(lifecycle);
        BalanceStore store = new BalanceStore(loader, CACHE_SETTINGS, inFlightTracker, CLOCK, Ticker.systemTicker());
        AtomicInteger readyEvents = new AtomicInteger();
        AtomicReference<UUID> lastNameUpdate = new AtomicReference<>();
        PlayerLifecycleCoordinator coordinator = new PlayerLifecycleCoordinator(
            store,
            (playerId, username, lastSeenAt) -> {
                lastNameUpdate.set(playerId);
                return CompletableFuture.completedFuture(null);
            },
            playerId -> settingsReady.get(),
            boosters,
            inFlightTracker,
            Runnable::run,
            (task, delayTicks) -> () -> { },
            event -> readyEvents.incrementAndGet(),
            Logger.getLogger("progressengine-test"),
            CLOCK
        );
        return new TestRuntime(store, coordinator, readyEvents, lastNameUpdate);
    }

    private static StoredAccount account(UUID playerId, long balance, long revision) {
        return new StoredAccount(playerId, balance, revision, CLOCK.instant(), CLOCK.instant());
    }

    private record TestRuntime(BalanceStore store, PlayerLifecycleCoordinator coordinator, AtomicInteger readyEvents,
                               AtomicReference<UUID> lastNameUpdate) {
    }
}
