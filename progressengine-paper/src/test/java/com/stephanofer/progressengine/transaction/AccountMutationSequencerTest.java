package com.stephanofer.progressengine.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AccountMutationSequencerTest {
    @Test
    void serializesMutationsForTheSameAccountAndCleansTail() {
        AccountMutationSequencer sequencer = new AccountMutationSequencer();
        UUID playerId = UUID.randomUUID();
        List<String> order = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> firstStarted = new CompletableFuture<>();
        CompletableFuture<Void> releaseFirst = new CompletableFuture<>();

        CompletableFuture<String> first = sequencer.submit(List.of(playerId), () -> {
            order.add("first");
            firstStarted.complete(null);
            return releaseFirst.thenApply(ignored -> "first");
        });
        firstStarted.join();
        CompletableFuture<String> second = sequencer.submit(List.of(playerId), () -> {
            order.add("second");
            return CompletableFuture.completedFuture("second");
        });

        assertFalse(second.isDone());
        releaseFirst.complete(null);

        assertEquals("first", first.join());
        assertEquals("second", second.join());
        assertEquals(List.of("first", "second"), order);
        assertEquals(0, sequencer.trackedKeys());
    }

    @Test
    void differentAccountsCanRunConcurrently() {
        AccountMutationSequencer sequencer = new AccountMutationSequencer();
        AtomicInteger running = new AtomicInteger();
        CompletableFuture<Void> firstStarted = new CompletableFuture<>();
        CompletableFuture<Void> secondStarted = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();

        CompletableFuture<Integer> first = sequencer.submit(List.of(UUID.randomUUID()), () -> {
            firstStarted.complete(null);
            running.incrementAndGet();
            return release.thenApply(ignored -> running.get());
        });
        CompletableFuture<Integer> second = sequencer.submit(List.of(UUID.randomUUID()), () -> {
            secondStarted.complete(null);
            running.incrementAndGet();
            return release.thenApply(ignored -> running.get());
        });

        firstStarted.join();
        secondStarted.join();
        assertEquals(2, running.get());
        release.complete(null);
        assertEquals(2, first.join());
        assertEquals(2, second.join());
    }

    @Test
    void cancellingReturnedFutureDoesNotCancelInternalMutation() {
        AccountMutationSequencer sequencer = new AccountMutationSequencer();
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<Void> internalCompleted = new CompletableFuture<>();
        AtomicBoolean ran = new AtomicBoolean();

        CompletableFuture<String> exposed = sequencer.submit(List.of(UUID.randomUUID()), () -> {
            ran.set(true);
            return release.thenApply(ignored -> "done").whenComplete((ignored, failure) -> internalCompleted.complete(null));
        });

        assertTrue(exposed.cancel(true));
        release.complete(null);
        internalCompleted.join();

        assertTrue(ran.get());
        assertEquals(0, sequencer.trackedKeys());
    }

    @Test
    void canonicalOrderUsesUnsignedBinaryUuidLayout() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highSigned = UUID.fromString("80000000-0000-0000-0000-000000000000");

        assertEquals(List.of(low, highSigned), CanonicalAccountOrder.sort(List.of(highSigned, low)));
    }
}
