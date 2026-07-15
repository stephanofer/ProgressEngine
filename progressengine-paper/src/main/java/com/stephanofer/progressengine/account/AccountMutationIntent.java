package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.request.CreditRequest;
import com.stephanofer.progressengine.api.request.DebitRequest;
import com.stephanofer.progressengine.api.request.ResetBalanceRequest;
import com.stephanofer.progressengine.api.request.SetBalanceRequest;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.Objects;
import java.util.UUID;

record AccountMutationIntent(OperationId operationId, OperationType type, UUID playerId, long requestedAmount,
                             OperationReason reason, OperationActor actor, OperationMetadata metadata) {
    AccountMutationIntent {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        if (requestedAmount < 0L) throw new IllegalArgumentException("requestedAmount cannot be negative");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(metadata, "metadata");
    }

    static AccountMutationIntent credit(CreditRequest request) {
        return new AccountMutationIntent(
            request.operationId(),
            OperationType.CREDIT,
            request.playerId(),
            request.amount(),
            request.reason(),
            request.actor(),
            request.metadata()
        );
    }

    static AccountMutationIntent debit(DebitRequest request) {
        return new AccountMutationIntent(
            request.operationId(),
            OperationType.DEBIT,
            request.playerId(),
            request.amount(),
            request.reason(),
            request.actor(),
            request.metadata()
        );
    }

    static AccountMutationIntent set(SetBalanceRequest request) {
        return new AccountMutationIntent(
            request.operationId(),
            OperationType.SET_BALANCE,
            request.playerId(),
            request.targetBalance(),
            request.reason(),
            request.actor(),
            request.metadata()
        );
    }

    static AccountMutationIntent reset(ResetBalanceRequest request) {
        return new AccountMutationIntent(
            request.operationId(),
            OperationType.RESET_BALANCE,
            request.playerId(),
            0L,
            request.reason(),
            request.actor(),
            request.metadata()
        );
    }
}
