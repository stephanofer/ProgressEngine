package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RemoteBalanceRefresher {
    private final BalanceStore balanceStore;
    private final RemoteBalanceEventDispatcher eventDispatcher;
    private final Logger logger;

    public RemoteBalanceRefresher(BalanceStore balanceStore, RemoteBalanceEventDispatcher eventDispatcher, Logger logger) {
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<Optional<BalanceSnapshot>> refreshCached(BalanceInvalidationMessage message) {
        Objects.requireNonNull(message, "message");
        Optional<BalanceSnapshot> current = this.balanceStore.cached(message.playerId());
        if (current.isEmpty() || current.orElseThrow().revision() >= message.revision()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return refreshAndPublish(
            message.playerId(),
            message.revision(),
            current,
            Optional.of(message.operationId())
        );
    }

    public CompletableFuture<Optional<BalanceSnapshot>> refreshForFeedback(UUID playerId, long minimumRevision) {
        UUID validPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<BalanceSnapshot> current = this.balanceStore.cached(validPlayerId);
        if (current.isPresent() && current.orElseThrow().revision() >= minimumRevision) {
            return CompletableFuture.completedFuture(current);
        }
        return refreshAndPublish(validPlayerId, minimumRevision, current, Optional.empty());
    }

    public CompletableFuture<Optional<BalanceSnapshot>> publishReconciled(BalanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Optional<BalanceSnapshot> previous = this.balanceStore.cached(snapshot.playerId());
        BalanceStore.Publication publication = this.balanceStore.publish(snapshot);
        if (!publication.updated() || previous.isEmpty()) {
            return CompletableFuture.completedFuture(publication.updated() ? Optional.of(publication.snapshot()) : Optional.empty());
        }
        BalanceChange change = change(previous.orElseThrow(), publication.snapshot());
        return this.eventDispatcher.dispatch(change, Optional.empty())
            .handle((ignored, failure) -> {
                if (failure != null) {
                    this.logger.log(Level.SEVERE, "Failed to dispatch reconciled balance event for " + snapshot.playerId(), failure);
                }
                return Optional.of(publication.snapshot());
            });
    }

    private CompletableFuture<Optional<BalanceSnapshot>> refreshAndPublish(UUID playerId, long minimumRevision,
                                                                           Optional<BalanceSnapshot> previous,
                                                                           Optional<OperationId> announcedOperationId) {
        return this.balanceStore.refreshAtLeast(playerId, minimumRevision).thenCompose(loaded -> {
            Optional<BalanceSnapshot> before = previous.or(() -> this.balanceStore.cached(playerId).filter(snapshot -> snapshot.revision() < loaded.revision()));
            if (before.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.of(loaded));
            }
            Optional<OperationId> eventOperationId = loaded.revision() == minimumRevision ? announcedOperationId : Optional.empty();
            return this.eventDispatcher.dispatch(change(before.orElseThrow(), loaded), eventOperationId)
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        this.logger.log(Level.SEVERE, "Failed to dispatch remote balance event for " + playerId, failure);
                    }
                    return Optional.of(loaded);
                });
        });
    }

    private static BalanceChange change(BalanceSnapshot previous, BalanceSnapshot current) {
        return BalanceChange.single(
            current.playerId(),
            Math.subtractExact(current.balance(), previous.balance()),
            previous.balance(),
            current.balance(),
            current.revision()
        );
    }
}
