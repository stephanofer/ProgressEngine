package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AccountPostCommitPublisher {
    AccountPostCommitPublisher NOOP = receipt -> CompletableFuture.completedFuture(null);

    CompletableFuture<Void> publish(OperationReceipt receipt);

    static AccountPostCommitPublisher noop() {
        return NOOP;
    }

    static AccountPostCommitPublisher require(AccountPostCommitPublisher publisher) {
        return Objects.requireNonNull(publisher, "publisher");
    }
}
