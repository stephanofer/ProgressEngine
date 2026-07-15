package com.stephanofer.progressengine.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AccountMutationSequencer {
    private final Map<UUID, CompletableFuture<Void>> tails = new HashMap<>();

    public <T> CompletableFuture<T> submit(Collection<UUID> playerIds, Supplier<CompletableFuture<T>> task) {
        List<UUID> keys = canonicalKeys(playerIds);
        Objects.requireNonNull(task, "task");

        CompletableFuture<Void> gate;
        CompletableFuture<Void> tail = new CompletableFuture<>();
        synchronized (this.tails) {
            List<CompletableFuture<Void>> previous = new ArrayList<>(keys.size());
            for (UUID key : keys) {
                CompletableFuture<Void> existing = this.tails.get(key);
                if (existing != null) {
                    previous.add(existing);
                }
            }
            gate = previous.isEmpty() ? CompletableFuture.completedFuture(null) : CompletableFuture.allOf(previous.toArray(CompletableFuture[]::new));
            for (UUID key : keys) {
                this.tails.put(key, tail);
            }
        }

        CompletableFuture<T> internal = gate.handle((ignored, failure) -> null).thenCompose(ignored -> {
            try {
                return Objects.requireNonNull(task.get(), "task future cannot be null");
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        });
        internal.whenComplete((ignored, failure) -> {
            tail.complete(null);
            synchronized (this.tails) {
                for (UUID key : keys) {
                    if (this.tails.get(key) == tail) {
                        this.tails.remove(key);
                    }
                }
            }
        });
        return copyOf(internal);
    }

    public int trackedKeys() {
        synchronized (this.tails) {
            return this.tails.size();
        }
    }

    private static List<UUID> canonicalKeys(Collection<UUID> playerIds) {
        return CanonicalAccountOrder.sort(playerIds);
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
}
