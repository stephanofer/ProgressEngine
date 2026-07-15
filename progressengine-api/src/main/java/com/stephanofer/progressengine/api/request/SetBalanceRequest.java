package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;

/**
 * Administrative request to set an account balance.
 */
public record SetBalanceRequest(OperationId operationId, UUID playerId, long targetBalance, OperationReason reason,
                                OperationActor actor, OperationMetadata metadata) implements EconomicRequest {

    /** Creates a set-balance request with plugin actor and empty metadata. */
    public SetBalanceRequest(OperationId operationId, UUID playerId, long targetBalance, OperationReason reason) {
        this(operationId, playerId, targetBalance, reason, OperationActor.plugin(), OperationMetadata.empty());
    }

    /** Creates a set-balance request. */
    public SetBalanceRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        targetBalance = ApiValidation.requireNonNegative(targetBalance, "targetBalance");
        if (reason == null) {
            throw new NullPointerException("reason cannot be null");
        }
        if (actor == null) {
            throw new NullPointerException("actor cannot be null");
        }
        if (metadata == null) {
            throw new NullPointerException("metadata cannot be null");
        }
    }
}
