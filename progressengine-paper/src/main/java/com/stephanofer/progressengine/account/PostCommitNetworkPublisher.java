package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PostCommitNetworkPublisher {
    PostCommitNetworkPublisher NOOP = receipt -> CompletableFuture.completedFuture(null);

    CompletableFuture<Void> publish(OperationReceipt receipt);

    static PostCommitNetworkPublisher noop() {
        return NOOP;
    }

    static PostCommitNetworkPublisher require(PostCommitNetworkPublisher publisher) {
        return Objects.requireNonNull(publisher, "publisher");
    }
}
