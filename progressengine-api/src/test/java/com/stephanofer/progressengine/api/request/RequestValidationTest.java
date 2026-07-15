package com.stephanofer.progressengine.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RequestValidationTest {
    private static final OperationId OPERATION_ID = OperationId.generate();
    private static final OperationReason REASON = OperationReason.of("test:reason");
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    @Test
    void acceptsPositiveAmountsAndLongMaximum() {
        assertEquals(Long.MAX_VALUE, new AwardRequest(OPERATION_ID, PLAYER, Long.MAX_VALUE, REASON).baseAmount());
        assertEquals(Long.MAX_VALUE, new CreditRequest(OPERATION_ID, PLAYER, Long.MAX_VALUE, REASON).amount());
        assertEquals(Long.MAX_VALUE, new DebitRequest(OPERATION_ID, PLAYER, Long.MAX_VALUE, REASON).amount());
        assertEquals(Long.MAX_VALUE, new TransferRequest(OPERATION_ID, PLAYER, OTHER_PLAYER, Long.MAX_VALUE, REASON).amount());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new AwardRequest(OPERATION_ID, PLAYER, 0L, REASON));
        assertThrows(IllegalArgumentException.class, () -> new CreditRequest(OPERATION_ID, PLAYER, -1L, REASON));
        assertThrows(IllegalArgumentException.class, () -> new DebitRequest(OPERATION_ID, PLAYER, 0L, REASON));
        assertThrows(IllegalArgumentException.class, () -> new TransferRequest(OPERATION_ID, PLAYER, OTHER_PLAYER, -1L, REASON));
    }

    @Test
    void setAcceptsZeroButRejectsNegative() {
        assertEquals(0L, new SetBalanceRequest(OPERATION_ID, PLAYER, 0L, REASON).targetBalance());
        assertThrows(IllegalArgumentException.class, () -> new SetBalanceRequest(OPERATION_ID, PLAYER, -1L, REASON));
    }

    @Test
    void transferRequestAllowsSelfTransferForTypedBusinessRejection() {
        TransferRequest request = new TransferRequest(OPERATION_ID, PLAYER, PLAYER, 10L, REASON);

        assertEquals(PLAYER, request.senderId());
        assertEquals(PLAYER, request.receiverId());
    }

    @Test
    void rejectsNilUuid() {
        UUID nil = new UUID(0L, 0L);

        assertThrows(IllegalArgumentException.class, () -> new ResetBalanceRequest(OPERATION_ID, nil, REASON));
    }
}
