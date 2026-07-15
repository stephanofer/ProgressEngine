package com.stephanofer.progressengine.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EventContractTest {
    @Test
    void readyEventRequiresMatchingSnapshot() {
        UUID playerId = UUID.randomUUID();
        BalanceSnapshot snapshot = new BalanceSnapshot(playerId, 10L, 1L, Instant.now());

        PlayerPointsReadyEvent event = new PlayerPointsReadyEvent(playerId, snapshot);

        assertEquals(playerId, event.playerId());
        assertEquals(snapshot, event.snapshot());
        assertNotNull(event.getHandlers());
        assertThrows(IllegalArgumentException.class, () -> new PlayerPointsReadyEvent(UUID.randomUUID(), snapshot));
    }

    @Test
    void awardPrepareEventSupportsOnlyExpectedMutability() {
        AwardRequest request = new AwardRequest(
            OperationId.generate(),
            UUID.randomUUID(),
            10L,
            OperationReason.of("test:award"),
            com.stephanofer.progressengine.api.source.OperationActor.plugin(),
            OperationMetadata.empty()
        );

        PointsAwardPrepareEvent event = new PointsAwardPrepareEvent(request, new OperationSource("TestPlugin", "server-1"));

        assertEquals(10L, event.requestedBaseAmount());
        assertTrue(event.gameId().isEmpty());
        assertEquals("TestPlugin", event.source().pluginName());
        event.setPreparedBaseAmount(12L);
        assertEquals(12L, event.preparedBaseAmount());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertThrows(IllegalArgumentException.class, () -> event.setPreparedBaseAmount(0L));
        assertNotNull(PointsAwardPrepareEvent.getHandlerList());
    }

    @Test
    void balanceChangedEventAllowsMissingOperationId() {
        BalanceChange change = BalanceChange.single(UUID.randomUUID(), 5L, 0L, 5L, 1L);

        PointsBalanceChangedEvent event = new PointsBalanceChangedEvent(change, BalanceChangeOrigin.REMOTE, Optional.empty());

        assertEquals(BalanceChangeOrigin.REMOTE, event.origin());
        assertTrue(event.operationId().isEmpty());
        assertNotNull(event.getHandlers());
    }
}
