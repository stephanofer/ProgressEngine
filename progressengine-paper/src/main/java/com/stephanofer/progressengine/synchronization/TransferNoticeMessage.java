package com.stephanofer.progressengine.synchronization;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.util.Objects;
import java.util.UUID;

public record TransferNoticeMessage(OperationId operationId, UUID senderId, UUID receiverId, long amount,
                                    long receiverRevision, String sourceServerId) {
    public TransferNoticeMessage {
        Objects.requireNonNull(operationId, "operationId");
        senderId = requireUuid(senderId, "senderId");
        receiverId = requireUuid(receiverId, "receiverId");
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("senderId and receiverId must be different");
        }
        if (amount < 1L) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (receiverRevision < 1L) {
            throw new IllegalArgumentException("receiverRevision must be positive");
        }
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
