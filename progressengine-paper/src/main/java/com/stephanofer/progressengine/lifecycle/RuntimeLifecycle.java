package com.stephanofer.progressengine.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeLifecycle {
    private static final Map<RuntimeState, Set<RuntimeState>> TRANSITIONS = transitions();

    private final AtomicReference<RuntimeState> state = new AtomicReference<>(RuntimeState.STARTING);

    public RuntimeState state() {
        return this.state.get();
    }

    public RuntimeState transitionTo(RuntimeState target) {
        Objects.requireNonNull(target, "target");
        while (true) {
            RuntimeState current = this.state.get();
            if (current == target) {
                return current;
            }
            if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
                throw new IllegalStateException("Illegal runtime state transition from " + current + " to " + target);
            }
            if (this.state.compareAndSet(current, target)) {
                return target;
            }
        }
    }

    boolean acceptsNewWork(WorkKind kind) {
        RuntimeState current = state();
        if (current == RuntimeState.SHUTTING_DOWN || current == RuntimeState.CLOSED) {
            return false;
        }
        if (kind == WorkKind.MUTATION) {
            return current == RuntimeState.READY || current == RuntimeState.DEGRADED_REDIS;
        }
        return current != RuntimeState.UNAVAILABLE_DATABASE;
    }

    private static Map<RuntimeState, Set<RuntimeState>> transitions() {
        EnumMap<RuntimeState, Set<RuntimeState>> transitions = new EnumMap<>(RuntimeState.class);
        transitions.put(RuntimeState.STARTING, EnumSet.of(
            RuntimeState.READY,
            RuntimeState.DEGRADED_REDIS,
            RuntimeState.UNAVAILABLE_DATABASE,
            RuntimeState.SHUTTING_DOWN
        ));
        transitions.put(RuntimeState.READY, EnumSet.of(
            RuntimeState.DEGRADED_REDIS,
            RuntimeState.UNAVAILABLE_DATABASE,
            RuntimeState.SHUTTING_DOWN
        ));
        transitions.put(RuntimeState.DEGRADED_REDIS, EnumSet.of(
            RuntimeState.READY,
            RuntimeState.UNAVAILABLE_DATABASE,
            RuntimeState.SHUTTING_DOWN
        ));
        transitions.put(RuntimeState.UNAVAILABLE_DATABASE, EnumSet.of(
            RuntimeState.READY,
            RuntimeState.DEGRADED_REDIS,
            RuntimeState.SHUTTING_DOWN
        ));
        transitions.put(RuntimeState.SHUTTING_DOWN, EnumSet.of(RuntimeState.CLOSED));
        transitions.put(RuntimeState.CLOSED, EnumSet.noneOf(RuntimeState.class));
        return Map.copyOf(transitions);
    }
}
