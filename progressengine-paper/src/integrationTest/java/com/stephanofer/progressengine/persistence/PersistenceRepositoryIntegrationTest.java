package com.stephanofer.progressengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PersistenceRepositoryIntegrationTest extends PersistenceIntegrationTestSupport {
    @Test
    void accountsOperationsLedgerAndNamesRoundTrip() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            StoredAccount account = persistence.accounts().createOrLoad(playerId).join();
            assertEquals(0L, account.balance());
            assertEquals(Map.of(playerId, 0L), persistence.accounts().loadRevisions(List.of(playerId, playerId)).join());

            OperationId operationId = OperationId.generate();
            byte[] fingerprint = new byte[32];
            fingerprint[0] = 1;
            Instant now = Instant.now();
            OperationDraft draft = new OperationDraft(
                operationId,
                1,
                fingerprint,
                OperationType.CREDIT,
                playerId,
                Optional.empty(),
                0L,
                OperationActor.plugin(),
                new OperationSource("IntegrationTest", "server-1"),
                OperationReason.of("test:credit"),
                OperationMetadata.empty(),
                now
            );

            persistence.database().transaction(connection -> {
                OperationReservation reservation = persistence.operations().reserve(connection, draft);
                assertTrue(reservation instanceof OperationReservation.Reserved);
                persistence.operations().complete(connection, new OperationCompletion(
                    operationId,
                    OperationStatus.SUCCESS,
                    OperationResultPayload.empty(),
                    now.plusMillis(1)
                ));
                persistence.ledger().append(connection, List.of(new LedgerEntryDraft(
                    operationId,
                    playerId,
                    Optional.empty(),
                    0L,
                    0L,
                    0L,
                    1L,
                    now.plusMillis(1)
                )));
                return null;
            }).join();

            StoredOperation operation = persistence.operations().find(operationId).join().orElseThrow();
            assertEquals(OperationStatus.SUCCESS, operation.status());
            assertEquals(1, persistence.ledger().findByOperation(operationId).join().size());
            LedgerPage history = persistence.ledger().history(playerId, 10, Optional.empty()).join();
            assertEquals(1, history.entries().size());
            assertFalse(history.nextCursor().isPresent());

            persistence.playerNames().updateCurrentMapping(playerId, "Vendimia", now).join();
            assertEquals("Vendimia", persistence.playerNames().findByUsername("vendimia").join().orElseThrow().username());
            persistence.playerNames().updateCurrentMapping(playerId, "VendimiaPro", now.plusSeconds(1)).join();
            assertTrue(persistence.playerNames().findByUsername("vendimia").join().isEmpty());
            assertEquals(playerId, persistence.playerNames().findByUsername("vendimiapro").join().orElseThrow().playerId());
            assertEquals(1, persistence.playerNames().loadRecentSuggestions(10).join().size());
        } finally {
            cleanup(persistence);
        }
    }
}
