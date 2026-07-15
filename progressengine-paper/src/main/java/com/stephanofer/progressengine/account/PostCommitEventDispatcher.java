package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PostCommitEventDispatcher {
    CompletableFuture<Void> dispatch(OperationReceipt receipt, List<BalanceChange> acceptedBalanceChanges);
}
