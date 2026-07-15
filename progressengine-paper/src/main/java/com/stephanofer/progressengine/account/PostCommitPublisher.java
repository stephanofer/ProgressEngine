package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PostCommitPublisher implements AccountPostCommitPublisher {
    private final BalanceStore balanceStore;
    private final PostCommitEventDispatcher eventDispatcher;
    private final Logger logger;

    public PostCommitPublisher(BalanceStore balanceStore, PostCommitEventDispatcher eventDispatcher, Logger logger) {
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<Void> publish(OperationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        List<BalanceChange> acceptedChanges = new ArrayList<>(receipt.changes().size());
        for (BalanceChange change : receipt.changes()) {
            try {
                BalanceStore.Publication publication = this.balanceStore.publish(change);
                if (publication.updated()) {
                    acceptedChanges.add(change);
                }
            } catch (RuntimeException exception) {
                this.logger.log(Level.SEVERE, "Failed to publish committed balance snapshot for operation "
                    + receipt.operationId(), exception);
            }
        }

        CompletableFuture<Void> dispatched;
        try {
            dispatched = this.eventDispatcher.dispatch(receipt, List.copyOf(acceptedChanges));
        } catch (RuntimeException exception) {
            this.logger.log(Level.SEVERE, "Failed to dispatch post-commit events for operation " + receipt.operationId(), exception);
            return CompletableFuture.completedFuture(null);
        }
        return dispatched.handle((ignored, failure) -> {
            if (failure != null) {
                this.logger.log(Level.SEVERE, "Post-commit event dispatch failed for operation " + receipt.operationId(), failure);
            }
            return null;
        });
    }
}
