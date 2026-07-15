package com.stephanofer.progressengine.api.transaction;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.List;

/**
 * Durable receipt for a confirmed economic operation.
 */
public record OperationReceipt(OperationId operationId, OperationType type, OperationReason reason,
                               OperationActor actor, OperationSource source, OperationMetadata metadata,
                               List<BalanceChange> changes, Instant createdAt) {

    /**
     * Creates an operation receipt.
     *
     * @param operationId idempotency identifier
     * @param type operation type
     * @param reason stable operation reason
     * @param actor operation actor
     * @param source runtime source
     * @param metadata bounded metadata
     * @param changes confirmed balance changes
     * @param createdAt commit timestamp
     */
    public OperationReceipt {
        if (operationId == null) throw new NullPointerException("operationId cannot be null");
        if (type == null) throw new NullPointerException("type cannot be null");
        if (reason == null) throw new NullPointerException("reason cannot be null");
        if (actor == null) throw new NullPointerException("actor cannot be null");
        if (source == null) throw new NullPointerException("source cannot be null");
        if (metadata == null) throw new NullPointerException("metadata cannot be null");
        if (changes == null) throw new NullPointerException("changes cannot be null");
        if (createdAt == null) throw new NullPointerException("createdAt cannot be null");
        changes = List.copyOf(changes);
        validateChanges(type, changes);
    }

    private static void validateChanges(OperationType type, List<BalanceChange> changes) {
        if (type == OperationType.TRANSFER) {
            if (changes.size() != 2) {
                throw new IllegalArgumentException("transfer receipt must contain exactly two changes");
            }
            BalanceChange first = changes.get(0);
            BalanceChange second = changes.get(1);
            if (first.playerId().equals(second.playerId())) {
                throw new IllegalArgumentException("transfer changes must affect different players");
            }
            if (Math.addExact(first.delta(), second.delta()) != 0L) {
                throw new IllegalArgumentException("transfer changes must conserve the total balance");
            }
            return;
        }
        if (changes.size() != 1) {
            throw new IllegalArgumentException(type + " receipt must contain exactly one change");
        }
    }
}
