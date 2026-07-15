package com.stephanofer.progressengine.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PersistenceValueTest {
    @Test
    void binaryUuidRoundTripsAndRejectsInvalidValues() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        byte[] encoded = BinaryUuid.encode(uuid);

        assertEquals(16, encoded.length);
        assertEquals(uuid, BinaryUuid.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> BinaryUuid.encode(new UUID(0L, 0L)));
        assertThrows(PersistenceDataException.class, () -> BinaryUuid.decode(new byte[15]));
    }

    @Test
    void playerUsernamesUseAsciiRootNormalizationOnly() {
        assertEquals("vendimia_123", PlayerUsernames.normalize("Vendimia_123"));
        assertEquals("Vendimia_123", PlayerUsernames.requireValid("Vendimia_123"));

        assertThrows(IllegalArgumentException.class, () -> PlayerUsernames.requireValid("ab"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUsernames.requireValid("jugadorñ"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUsernames.normalize("İsim"));
    }

    @Test
    void operationDraftDefensivelyCopiesFingerprintAndMetadataIsCanonical() {
        byte[] fingerprint = new byte[32];
        fingerprint[31] = 7;
        UUID playerId = UUID.randomUUID();
        OperationDraft draft = new OperationDraft(
            OperationId.generate(),
            1,
            fingerprint,
            OperationType.CREDIT,
            playerId,
            Optional.empty(),
            Optional.empty(),
            100L,
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-1"),
            OperationReason.of("test:credit"),
            OperationMetadata.of(Map.of("z", "last", "a", "first")),
            Instant.EPOCH
        );

        fingerprint[31] = 99;

        assertEquals("{\"a\":\"first\",\"z\":\"last\"}", draft.metadataJson());
        assertEquals(7, draft.requestFingerprint()[31]);
        byte[] returned = draft.requestFingerprint();
        returned[31] = 10;
        assertEquals(7, draft.requestFingerprint()[31]);
    }

    @Test
    void operationDraftRequiresTransferCounterpartyOnlyForTransfers() {
        byte[] fingerprint = new byte[32];
        fingerprint[0] = 1;
        UUID playerId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        OperationDraft transfer = new OperationDraft(
            OperationId.generate(),
            1,
            fingerprint,
            OperationType.TRANSFER,
            playerId,
            Optional.of(receiverId),
            Optional.empty(),
            100L,
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-1"),
            OperationReason.of("test:transfer"),
            OperationMetadata.empty(),
            Instant.EPOCH
        );

        assertEquals(Optional.of(receiverId), transfer.relatedPlayerId());
        assertThrows(IllegalArgumentException.class, () -> new OperationDraft(
            OperationId.generate(), 1, fingerprint, OperationType.TRANSFER, playerId, Optional.empty(), Optional.empty(), 100L,
            OperationActor.plugin(), new OperationSource("TestPlugin", "server-1"), OperationReason.of("test:transfer"),
            OperationMetadata.empty(), Instant.EPOCH
        ));
        assertThrows(IllegalArgumentException.class, () -> new OperationDraft(
            OperationId.generate(), 1, fingerprint, OperationType.CREDIT, playerId, Optional.of(receiverId), Optional.empty(), 100L,
            OperationActor.plugin(), new OperationSource("TestPlugin", "server-1"), OperationReason.of("test:credit"),
            OperationMetadata.empty(), Instant.EPOCH
        ));
    }

    @Test
    void operationResultPayloadRequiresVersionAndJsonTogether() {
        assertFalse(OperationResultPayload.empty().version().isPresent());
        assertTrue(OperationResultPayload.of(1, "{}").json().isPresent());

        assertThrows(IllegalArgumentException.class, () -> OperationResultPayload.of(0, "{}"));
        assertThrows(IllegalArgumentException.class, () -> OperationResultPayload.of(1, " "));
        assertThrows(IllegalArgumentException.class, () -> new OperationResultPayload(Optional.of(1), Optional.empty()));
    }

    @Test
    void ledgerCursorAndPageValidateStablePaginationData() {
        assertThrows(IllegalArgumentException.class, () -> new LedgerCursor(Instant.EPOCH, 0L));

        StoredLedgerEntry entry = new StoredLedgerEntry(
            1L,
            OperationId.generate(),
            UUID.randomUUID(),
            Optional.empty(),
            0L,
            10L,
            10L,
            1L,
            Instant.EPOCH
        );
        LedgerPage page = new LedgerPage(java.util.List.of(entry), Optional.of(new LedgerCursor(Instant.EPOCH, 1L)));

        assertEquals(1, page.entries().size());
        assertTrue(page.nextCursor().isPresent());
        assertThrows(UnsupportedOperationException.class, () -> page.entries().add(entry));
    }

    @Test
    void databaseTablePrefixMustFitEveryProgressEngineTable() {
        DatabaseTables.validatePrefix("progress_");

        String tooLong = "x".repeat(64 - DatabaseTables.OPERATIONS.length() + 1);
        assertThrows(RuntimeException.class, () -> DatabaseTables.validatePrefix(tooLong));
    }

    @Test
    void storedOperationRejectsCorruptPendingCompletionState() {
        byte[] fingerprint = new byte[32];

        assertThrows(PersistenceDataException.class, () -> new StoredOperation(
            OperationId.generate(),
            1,
            fingerprint,
            OperationType.CREDIT,
            OperationStatus.PENDING,
            UUID.randomUUID(),
            Optional.empty(),
            Optional.empty(),
            100L,
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-1"),
            OperationReason.of("test:credit"),
            "{}",
            OperationResultPayload.empty(),
            Instant.EPOCH,
            Optional.of(Instant.EPOCH)
        ));
    }

    @Test
    void storedOperationRequiresTransferCounterpartyOnlyForTransfers() {
        byte[] fingerprint = new byte[32];
        fingerprint[0] = 1;
        UUID playerId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        StoredOperation transfer = storedOperation(OperationType.TRANSFER, playerId, Optional.of(receiverId), 100L, fingerprint);

        assertEquals(Optional.of(receiverId), transfer.relatedPlayerId());
        assertThrows(PersistenceDataException.class, () -> storedOperation(
            OperationType.TRANSFER,
            playerId,
            Optional.empty(),
            100L,
            fingerprint
        ));
        assertThrows(PersistenceDataException.class, () -> storedOperation(
            OperationType.CREDIT,
            playerId,
            Optional.of(receiverId),
            100L,
            fingerprint
        ));
    }

    @Test
    void binaryUuidUsesCanonicalBigEndianLayout() {
        UUID uuid = new UUID(0x0102030405060708L, 0x1112131415161718L);

        assertArrayEquals(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8,
            17, 18, 19, 20, 21, 22, 23, 24
        }, BinaryUuid.encode(uuid));
    }

    private static StoredOperation storedOperation(OperationType type, UUID playerId, Optional<UUID> relatedPlayerId,
                                                   long amount, byte[] fingerprint) {
        return new StoredOperation(
            OperationId.generate(),
            1,
            fingerprint,
            type,
            OperationStatus.SUCCESS,
            playerId,
            relatedPlayerId,
            Optional.empty(),
            amount,
            OperationActor.plugin(),
            new OperationSource("TestPlugin", "server-1"),
            OperationReason.of("test:operation"),
            "{}",
            OperationResultPayload.of(1, "{}"),
            Instant.EPOCH,
            Optional.of(Instant.EPOCH)
        );
    }
}
