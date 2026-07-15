package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.util.Objects;
import java.util.UUID;

public record BalanceInvalidationMessage(UUID playerId, long revision, OperationId operationId, String sourceServerId) {
    public BalanceInvalidationMessage {
        playerId = requireUuid(playerId, "playerId");
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(operationId, "operationId");
        sourceServerId = RedisMessageCodec.requireComponent(sourceServerId, "sourceServerId");
    }

    private static UUID requireUuid(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be nil UUID");
        }
        return value;
    }
}
