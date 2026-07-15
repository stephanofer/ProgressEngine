package com.stephanofer.progressengine.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RuntimeLifecycleTest {
    @Test
    void validatesStateTransitions() {
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();

        assertEquals(RuntimeState.STARTING, lifecycle.state());
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(RuntimeState.CLOSED));
        lifecycle.transitionTo(RuntimeState.READY);
        lifecycle.transitionTo(RuntimeState.DEGRADED_REDIS);
        lifecycle.transitionTo(RuntimeState.UNAVAILABLE_DATABASE);
        lifecycle.transitionTo(RuntimeState.SHUTTING_DOWN);
        lifecycle.transitionTo(RuntimeState.CLOSED);
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(RuntimeState.READY));
    }

    @Test
    void gatesMutationsByRuntimeStateAndRejectsShutdownWork() {
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();
        InFlightTracker tracker = new InFlightTracker(lifecycle);

        assertTrue(tracker.acquire(WorkKind.LOAD).isPresent());
        assertTrue(tracker.acquire(WorkKind.MUTATION).isEmpty());

        lifecycle.transitionTo(RuntimeState.READY);
        WorkPermit mutation = tracker.acquire(WorkKind.MUTATION).orElseThrow();
        assertEquals(1, tracker.counts().mutations());
        mutation.close();
        mutation.close();
        assertEquals(0, tracker.counts().mutations());

        lifecycle.transitionTo(RuntimeState.SHUTTING_DOWN);
        assertTrue(tracker.acquire(WorkKind.LOAD).isEmpty());
        assertTrue(tracker.acquire(WorkKind.MUTATION).isEmpty());
    }

    @Test
    void awaitDrainedTimesOutAndThenSucceeds() throws Exception {
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();
        InFlightTracker tracker = new InFlightTracker(lifecycle);
        WorkPermit load = tracker.acquire(WorkKind.LOAD).orElseThrow();

        assertFalse(tracker.awaitDrained(Duration.ofMillis(10L)));
        load.close();
        assertTrue(tracker.awaitDrained(Duration.ofMillis(10L)));
    }

    @Test
    void closesResourcesInReverseOrderAndReportsFailures() throws Exception {
        LifecycleResources resources = new LifecycleResources();
        List<String> closed = new ArrayList<>();
        resources.register("first", () -> closed.add("first"));
        resources.register("second", () -> closed.add("second"));

        resources.close();
        resources.close();

        assertEquals(List.of("second", "first"), closed);

        LifecycleResources failing = new LifecycleResources();
        failing.register("first", () -> {
            throw new IllegalStateException("first");
        });
        failing.register("second", () -> {
            throw new IllegalStateException("second");
        });

        RuntimeException exception = assertThrows(RuntimeException.class, failing::close);
        assertEquals(1, exception.getSuppressed().length);
    }
}
