package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;

/**
 * Request to credit points without applying boosters.
 */
public record CreditRequest(OperationId operationId, UUID playerId, long amount, OperationReason reason,
                            OperationActor actor, OperationMetadata metadata) implements EconomicRequest {

    /** Creates a credit request with plugin actor and empty metadata. */
    public CreditRequest(OperationId operationId, UUID playerId, long amount, OperationReason reason) {
        this(operationId, playerId, amount, reason, OperationActor.plugin(), OperationMetadata.empty());
    }

    /** Creates a credit request. */
    public CreditRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        amount = ApiValidation.requirePositive(amount, "amount");
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
