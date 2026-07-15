package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.time.Instant;
import java.util.Objects;

public record OperationCompletion(OperationId operationId, OperationStatus status, OperationResultPayload payload, Instant completedAt) {
    public OperationCompletion {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(status, "status");
        if (status == OperationStatus.PENDING) {
            throw new IllegalArgumentException("completion status cannot be PENDING");
        }
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
