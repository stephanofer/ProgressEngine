package com.stephanofer.progressengine.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.persistence.OperationResultPayload;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceDataException;
import com.stephanofer.progressengine.persistence.StoredOperation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OperationResultCodecTest {
    private final UUID playerId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final Instant completedAt = Instant.parse("2026-07-15T10:15:30.123456Z");

    @Test
    void successPayloadReconstructsReceiptExactly() {
        BalanceChange change = BalanceChange.single(this.playerId, 50L, 100L, 150L, 7L);
        StoredOperation operation = operation(OperationStatus.SUCCESS, OperationResultCodec.successPayload(change));

        DecodedOperationResult.Success decoded = (DecodedOperationResult.Success) OperationResultCodec.decode(
            operation,
            ReplayStatus.REPLAYED
        );

        assertEquals(ReplayStatus.REPLAYED, decoded.replayStatus());
        assertEquals(this.completedAt, decoded.receipt().createdAt());
        assertEquals("server-a", decoded.receipt().source().serverId());
        assertEquals("original", decoded.receipt().metadata().values().get("source"));
        assertEquals(change, decoded.receipt().changes().getFirst());
    }

    @Test
    void transferSuccessPayloadReconstructsBothDirectedChanges() {
        UUID receiverId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        BalanceChange sender = BalanceChange.related(this.playerId, receiverId, -50L, 100L, 50L, 3L);
        BalanceChange receiver = BalanceChange.related(receiverId, this.playerId, 50L, 10L, 60L, 8L);
        StoredOperation operation = operation(
            OperationType.TRANSFER,
            OperationStatus.SUCCESS,
            Optional.of(receiverId),
            50L,
            OperationResultCodec.transferSuccessPayload(sender, receiver)
        );

        DecodedOperationResult.Success decoded = (DecodedOperationResult.Success) OperationResultCodec.decode(
            operation,
            ReplayStatus.REPLAYED
        );

        assertEquals(ReplayStatus.REPLAYED, decoded.replayStatus());
        assertEquals(OperationType.TRANSFER, decoded.receipt().type());
        assertEquals(java.util.List.of(sender, receiver), decoded.receipt().changes());
    }

    @Test
    void rejectionPayloadMustBeEmptyObject() {
        StoredOperation operation = operation(OperationStatus.INSUFFICIENT_FUNDS, OperationResultCodec.rejectionPayload());

        DecodedOperationResult.Rejected decoded = (DecodedOperationResult.Rejected) OperationResultCodec.decode(
            operation,
            ReplayStatus.ORIGINAL
        );

        assertEquals(OperationStatus.INSUFFICIENT_FUNDS, decoded.status());
        assertThrows(PersistenceDataException.class, () -> OperationResultCodec.decode(
            operation(OperationStatus.INSUFFICIENT_FUNDS, OperationResultPayload.of(1, "{\"balance_before\":1}")),
            ReplayStatus.REPLAYED
        ));
    }

    @Test
    void successPayloadRejectsNonIntegerNumbersAndUnknownFields() {
        assertThrows(PersistenceDataException.class, () -> OperationResultCodec.decode(
            operation(OperationStatus.SUCCESS, OperationResultPayload.of(1,
                "{\"balance_before\":1.0,\"balance_after\":2,\"revision\":1}")),
            ReplayStatus.ORIGINAL
        ));
        assertThrows(PersistenceDataException.class, () -> OperationResultCodec.decode(
            operation(OperationStatus.SUCCESS, OperationResultPayload.of(1,
                "{\"balance_before\":1,\"balance_after\":2,\"revision\":1,\"extra\":0}")),
            ReplayStatus.ORIGINAL
        ));
    }

    @Test
    void transferPayloadRejectsCorruptShapeAndAmounts() {
        UUID receiverId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        assertThrows(PersistenceDataException.class, () -> OperationResultCodec.decode(
            operation(OperationType.TRANSFER, OperationStatus.SUCCESS, Optional.of(receiverId), 50L,
                OperationResultPayload.of(1, "{\"balance_before\":1,\"balance_after\":2,\"revision\":1}")),
            ReplayStatus.ORIGINAL
        ));
        assertThrows(PersistenceDataException.class, () -> OperationResultCodec.decode(
            operation(OperationType.TRANSFER, OperationStatus.SUCCESS, Optional.of(receiverId), 51L,
                OperationResultCodec.transferSuccessPayload(
                    BalanceChange.related(this.playerId, receiverId, -50L, 100L, 50L, 3L),
                    BalanceChange.related(receiverId, this.playerId, 50L, 10L, 60L, 8L)
                )),
            ReplayStatus.ORIGINAL
        ));
    }

    private StoredOperation operation(OperationStatus status, OperationResultPayload payload) {
        return operation(OperationType.CREDIT, status, Optional.empty(), 50L, payload);
    }

    private StoredOperation operation(OperationType type, OperationStatus status, Optional<UUID> relatedPlayerId, long amount,
                                      OperationResultPayload payload) {
        byte[] fingerprint = new byte[32];
        fingerprint[0] = 1;
        return new StoredOperation(
            OperationId.generate(),
            1,
            fingerprint,
            type,
            status,
            this.playerId,
            relatedPlayerId,
            amount,
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-a"),
            OperationReason.of("test:credit"),
            "{\"source\":\"original\"}",
            payload,
            this.completedAt,
            Optional.of(this.completedAt)
        );
    }
}
