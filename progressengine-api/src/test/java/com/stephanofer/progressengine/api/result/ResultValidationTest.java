package com.stephanofer.progressengine.api.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ResultValidationTest {
    @Test
    void replayIsStoredOnDurableOutcome() {
        OperationId id = OperationId.generate();

        DebitResult result = new DebitResult.InsufficientFunds(id, ReplayStatus.REPLAYED);

        assertEquals(id, result.operationId());
    }

    @Test
    void successResultRequiresMatchingReceiptType() {
        OperationReceipt creditReceipt = receipt(OperationType.CREDIT);

        assertEquals(creditReceipt.operationId(), new CreditResult.Success(creditReceipt, ReplayStatus.ORIGINAL).operationId());
        assertThrows(IllegalArgumentException.class, () -> new DebitResult.Success(creditReceipt, ReplayStatus.ORIGINAL));
    }

    @Test
    void awardCalculationIsImmutableAndPositive() {
        AwardCalculation calculation = new AwardCalculation(10L, 12L, BigDecimal.ONE, 12L, List.of("booster:test"), false);

        assertEquals(12L, calculation.finalAmount());
        assertThrows(UnsupportedOperationException.class, () -> calculation.appliedBoosterIds().add("other"));
        assertEquals(0L, new AwardCalculation(1L, 1L, new BigDecimal("0.5"), 0L, List.of(), false).finalAmount());
    }

    @Test
    void noPointsAwardIsDurableAndRequiresZeroCalculation() {
        OperationId operationId = OperationId.generate();
        AwardCalculation zeroCalculation = new AwardCalculation(
            1L,
            1L,
            new BigDecimal("0.5"),
            0L,
            List.of(),
            false
        );

        AwardResult.NoPointsAwarded result = new AwardResult.NoPointsAwarded(
            operationId,
            zeroCalculation,
            ReplayStatus.REPLAYED
        );

        assertEquals(operationId, result.operationId());
        assertThrows(
            IllegalArgumentException.class,
            () -> new AwardResult.NoPointsAwarded(
                operationId,
                new AwardCalculation(1L, 1L, BigDecimal.ONE, 1L, List.of(), false),
                ReplayStatus.ORIGINAL
            )
        );
    }

    private static OperationReceipt receipt(OperationType type) {
        UUID playerId = UUID.randomUUID();
        return new OperationReceipt(
            OperationId.generate(),
            type,
            OperationReason.of("test:reason"),
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-1"),
            OperationMetadata.empty(),
            List.of(BalanceChange.single(playerId, 10L, 0L, 10L, 1L)),
            Instant.now()
        );
    }
}
