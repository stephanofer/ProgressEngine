package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;

/**
 * Request to award gameplay points to one player.
 */
public record AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason,
                           OperationActor actor, OperationMetadata metadata) implements EconomicRequest {

    /**
     * Creates an award request with plugin actor and empty metadata.
     */
    public AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason) {
        this(operationId, playerId, baseAmount, reason, OperationActor.plugin(), OperationMetadata.empty());
    }

    /**
     * Creates an award request.
     */
    public AwardRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        baseAmount = ApiValidation.requirePositive(baseAmount, "baseAmount");
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
