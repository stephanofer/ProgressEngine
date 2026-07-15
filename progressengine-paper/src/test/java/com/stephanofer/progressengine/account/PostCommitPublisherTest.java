package com.stephanofer.progressengine.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

final class PostCommitPublisherTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void publishesCacheBeforeDispatchAndCompletesAfterDispatcher() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store();
        CompletableFuture<Void> dispatched = new CompletableFuture<>();
        List<BalanceChange> observed = new ArrayList<>();
        OperationReceipt receipt = receipt(BalanceChange.single(playerId, 25L, 0L, 25L, 1L));
        PostCommitPublisher publisher = new PostCommitPublisher(store, (eventReceipt, changes) -> {
            assertEquals(25L, store.cached(playerId).orElseThrow().balance());
            observed.addAll(changes);
            return dispatched;
        }, Logger.getLogger("test"));

        CompletableFuture<Void> result = publisher.publish(receipt);

        assertFalse(result.isDone());
        assertEquals(List.of(receipt.changes().getFirst()), observed);
        dispatched.complete(null);
        result.join();
    }

    @Test
    void staleChangeDoesNotEmitBalanceChangedButStillDispatchesTransaction() {
        UUID playerId = UUID.randomUUID();
        BalanceStore store = store();
        store.publish(new BalanceSnapshot(playerId, 100L, 2L, CLOCK.instant()));
        List<List<BalanceChange>> dispatches = new ArrayList<>();
        PostCommitPublisher publisher = new PostCommitPublisher(store, (receipt, changes) -> {
            dispatches.add(changes);
            return CompletableFuture.completedFuture(null);
        }, Logger.getLogger("test"));

        publisher.publish(receipt(BalanceChange.single(playerId, 10L, 0L, 10L, 1L))).join();

        assertEquals(1, dispatches.size());
        assertTrue(dispatches.getFirst().isEmpty());
        assertEquals(100L, store.cached(playerId).orElseThrow().balance());
    }

    @Test
    void dispatcherFailureIsObservationalAndDoesNotFailPublisher() {
        BalanceStore store = store();
        UUID playerId = UUID.randomUUID();
        PostCommitPublisher publisher = new PostCommitPublisher(store, (receipt, changes) -> {
            throw new IllegalStateException("listener bridge failed");
        }, Logger.getLogger("test"));

        publisher.publish(receipt(BalanceChange.single(playerId, 5L, 0L, 5L, 1L))).join();

        assertEquals(5L, store.cached(playerId).orElseThrow().balance());
    }

    @Test
    void networkPublicationStartsButDoesNotDelayLocalDispatchFuture() {
        UUID playerId = UUID.randomUUID();
        CompletableFuture<Void> network = new CompletableFuture<>();
        BalanceStore store = store();
        PostCommitPublisher publisher = new PostCommitPublisher(
            store,
            (receipt, changes) -> CompletableFuture.completedFuture(null),
            receipt -> network,
            Logger.getLogger("test")
        );

        CompletableFuture<Void> result = publisher.publish(receipt(BalanceChange.single(playerId, 5L, 0L, 5L, 1L)));

        result.join();
        assertFalse(network.isDone());
    }

    private static BalanceStore store() {
        return new BalanceStore(
            playerId -> CompletableFuture.failedFuture(new AssertionError("loader must not run")),
            new ProgressEngineConfig.CacheSettings(100L, 60L, false),
            new InFlightTracker(new RuntimeLifecycle()),
            CLOCK,
            Ticker.systemTicker()
        );
    }

    private static OperationReceipt receipt(BalanceChange change) {
        return new OperationReceipt(
            OperationId.generate(),
            OperationType.CREDIT,
            OperationReason.of("test:credit"),
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-a"),
            OperationMetadata.empty(),
            List.of(change),
            CLOCK.instant()
        );
    }
}
