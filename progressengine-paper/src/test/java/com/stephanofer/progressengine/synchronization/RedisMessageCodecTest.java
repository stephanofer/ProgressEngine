package com.stephanofer.progressengine.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RedisMessageCodecTest {
    private final RedisMessageCodec codec = new RedisMessageCodec();

    @Test
    void invalidationRoundTripsWithCanonicalFields() {
        OperationId operationId = OperationId.generate();
        UUID playerId = UUID.randomUUID();
        BalanceInvalidationMessage message = new BalanceInvalidationMessage(playerId, 7L, operationId, "lobby-1");

        BalanceInvalidationMessage decoded = this.codec.decodeInvalidation(this.codec.encode(message));

        assertEquals(message, decoded);
    }

    @Test
    void transferNoticeRoundTripsWithCanonicalFields() {
        TransferNoticeMessage message = new TransferNoticeMessage(
            OperationId.generate(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            500L,
            3L,
            "skywars-1"
        );

        TransferNoticeMessage decoded = this.codec.decodeTransferNotice(this.codec.encode(message));

        assertEquals(message, decoded);
    }

    @Test
    void rejectsDuplicateFields() {
        String payload = "{\"version\":1,\"version\":1,\"playerId\":\"" + UUID.randomUUID()
            + "\",\"revision\":1,\"operationId\":\"" + OperationId.generate()
            + "\",\"sourceServerId\":\"lobby-1\"}";

        assertThrows(IllegalArgumentException.class, () -> this.codec.decodeInvalidation(payload));
    }

    @Test
    void rejectsNumbersEncodedAsStrings() {
        String payload = "{\"version\":\"1\",\"playerId\":\"" + UUID.randomUUID()
            + "\",\"revision\":1,\"operationId\":\"" + OperationId.generate()
            + "\",\"sourceServerId\":\"lobby-1\"}";

        assertThrows(IllegalArgumentException.class, () -> this.codec.decodeInvalidation(payload));
    }

    @Test
    void rejectsUnknownFieldsAndNilUuid() {
        String payload = "{\"version\":1,\"playerId\":\"00000000-0000-0000-0000-000000000000\","
            + "\"revision\":1,\"operationId\":\"" + OperationId.generate()
            + "\",\"sourceServerId\":\"lobby-1\",\"extra\":\"bad\"}";

        assertThrows(IllegalArgumentException.class, () -> this.codec.decodeInvalidation(payload));
    }
}
