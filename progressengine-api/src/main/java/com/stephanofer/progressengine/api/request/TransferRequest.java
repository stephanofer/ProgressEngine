package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;

/**
 * Request to atomically transfer points between two accounts.
 */
public record TransferRequest(OperationId operationId, UUID senderId, UUID receiverId, long amount,
                              OperationReason reason, OperationActor actor,
                              OperationMetadata metadata) implements EconomicRequest {

    /** Creates a transfer request with plugin actor and empty metadata. */
    public TransferRequest(OperationId operationId, UUID senderId, UUID receiverId, long amount, OperationReason reason) {
        this(operationId, senderId, receiverId, amount, reason, OperationActor.plugin(), OperationMetadata.empty());
    }

    /** Creates a transfer request. */
    public TransferRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        senderId = ApiValidation.requireUuid(senderId, "senderId");
        receiverId = ApiValidation.requireUuid(receiverId, "receiverId");
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
