package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RemoteBalanceEventDispatcher {
    CompletableFuture<Void> dispatch(BalanceChange change, Optional<OperationId> operationId);
}
