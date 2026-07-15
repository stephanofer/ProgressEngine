package com.stephanofer.progressengine.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReceiptValidationTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final OperationReason REASON = OperationReason.of("test:reason");
    private static final OperationSource SOURCE = new OperationSource("TestPlugin", "server-1");

    @Test
    void balanceChangeRequiresExactDelta() {
        BalanceChange change = BalanceChange.single(PLAYER, 50L, 100L, 150L, 1L);

        assertEquals(50L, change.delta());
        assertThrows(IllegalArgumentException.class, () -> BalanceChange.single(PLAYER, 51L, 100L, 150L, 1L));
    }

    @Test
    void oneAccountReceiptRequiresOneChange() {
        OperationReceipt receipt = receipt(OperationType.CREDIT, List.of(BalanceChange.single(PLAYER, 10L, 0L, 10L, 1L)));

        assertEquals(OperationType.CREDIT, receipt.type());
        assertThrows(UnsupportedOperationException.class, () -> receipt.changes().add(BalanceChange.single(PLAYER, 1L, 10L, 11L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> receipt(OperationType.CREDIT, List.of(
            BalanceChange.single(PLAYER, 10L, 0L, 10L, 1L),
            BalanceChange.single(OTHER, 10L, 0L, 10L, 1L)
        )));
    }

    @Test
    void transferReceiptRequiresTwoConservingChanges() {
        BalanceChange sender = BalanceChange.related(PLAYER, OTHER, -10L, 20L, 10L, 2L);
        BalanceChange receiver = BalanceChange.related(OTHER, PLAYER, 10L, 0L, 10L, 1L);

        OperationReceipt receipt = receipt(OperationType.TRANSFER, List.of(sender, receiver));

        assertEquals(2, receipt.changes().size());
        assertThrows(IllegalArgumentException.class, () -> receipt(OperationType.TRANSFER, List.of(sender)));
        assertThrows(IllegalArgumentException.class, () -> receipt(OperationType.TRANSFER, List.of(sender, BalanceChange.related(OTHER, PLAYER, 9L, 0L, 9L, 1L))));
    }

    @Test
    void relatedPlayerCannotEqualPlayer() {
        assertThrows(IllegalArgumentException.class, () -> new BalanceChange(PLAYER, Optional.of(PLAYER), 0L, 10L, 10L, 1L));
    }

    private static OperationReceipt receipt(OperationType type, List<BalanceChange> changes) {
        return new OperationReceipt(
            OperationId.generate(),
            type,
            REASON,
            OperationActor.plugin(),
            SOURCE,
            OperationMetadata.empty(),
            changes,
            Instant.now()
        );
    }
}
