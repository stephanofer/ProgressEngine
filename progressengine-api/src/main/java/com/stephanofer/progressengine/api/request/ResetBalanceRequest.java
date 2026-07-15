package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;

/**
 * Administrative request to reset an account balance to zero.
 */
public record ResetBalanceRequest(OperationId operationId, UUID playerId, OperationReason reason,
                                  OperationActor actor, OperationMetadata metadata) implements EconomicRequest {

    /** Creates a reset-balance request with plugin actor and empty metadata. */
    public ResetBalanceRequest(OperationId operationId, UUID playerId, OperationReason reason) {
        this(operationId, playerId, reason, OperationActor.plugin(), OperationMetadata.empty());
    }

    /** Creates a reset-balance request. */
    public ResetBalanceRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        playerId = ApiValidation.requireUuid(playerId, "playerId");
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
