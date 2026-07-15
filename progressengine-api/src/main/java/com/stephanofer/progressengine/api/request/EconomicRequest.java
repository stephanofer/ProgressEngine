package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;

/**
 * Common data carried by every economic request.
 */
public interface EconomicRequest {
    /**
     * Returns the idempotency identifier of this economic intention.
     *
     * @return operation id
     */
    OperationId operationId();

    /**
     * Returns the stable reason for this request.
     *
     * @return operation reason
     */
    OperationReason reason();

    /**
     * Returns the actor that initiated the request.
     *
     * @return operation actor
     */
    OperationActor actor();

    /**
     * Returns bounded diagnostic metadata.
     *
     * @return operation metadata
     */
    OperationMetadata metadata();
}
