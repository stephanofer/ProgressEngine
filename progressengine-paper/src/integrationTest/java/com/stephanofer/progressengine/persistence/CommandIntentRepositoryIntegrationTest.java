package com.stephanofer.progressengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.ActorType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CommandIntentRepositoryIntegrationTest extends PersistenceIntegrationTestSupport {
    @Test
    void commandIntentLifecycleIsDurableAndSingleTokenAddressed() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            byte[] hash = new byte[32];
            hash[31] = 12;
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            Instant created = Instant.parse("2026-07-16T00:00:00Z");
            CommandIntentDraft draft = new CommandIntentDraft(
                hash,
                OperationId.generate(),
                CommandIntentType.PAY,
                CommandIntentState.AWAITING_CONFIRMATION,
                Optional.of(senderId),
                ActorType.PLAYER,
                Optional.of(senderId),
                senderId,
                Optional.of(receiverId),
                500L,
                OperationReason.of("test:pay"),
                Optional.of(7L),
                "server-a",
                created,
                created.plusSeconds(30L)
            );

            persistence.commandIntents().insert(draft).join();
            CommandIntent awaiting = persistence.commandIntents().find(hash).join().orElseThrow();

            assertEquals(CommandIntentState.AWAITING_CONFIRMATION, awaiting.state());
            assertEquals(Optional.of(7L), awaiting.observedRevision());

            CommandIntent submitted = persistence.commandIntents().markSubmitted(hash, created.plusSeconds(2L), created.plusSeconds(300L))
                .join().orElseThrow();
            CommandIntent submittedReplay = persistence.commandIntents().markSubmitted(hash, created.plusSeconds(3L), created.plusSeconds(301L))
                .join().orElseThrow();
            persistence.commandIntents().markResolved(hash, created.plusSeconds(4L)).join();
            CommandIntent resolved = persistence.commandIntents().find(hash).join().orElseThrow();

            assertEquals(CommandIntentState.SUBMITTED, submitted.state());
            assertEquals(submitted.submittedAt(), submittedReplay.submittedAt());
            assertEquals(CommandIntentState.RESOLVED, resolved.state());
            assertEquals(Optional.of(created.plusSeconds(4L)), resolved.resolvedAt());
            assertEquals(0, persistence.commandIntents().deleteExpired(created.plusSeconds(299L), 10).join());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void expiredUnresolvedIntentCanBeDeletedInBoundedBatches() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            Instant created = Instant.parse("2026-07-16T00:00:00Z");
            byte[] firstHash = hash(1);
            byte[] secondHash = hash(2);
            persistence.commandIntents().insert(intent(firstHash, created, created.plusSeconds(1L))).join();
            persistence.commandIntents().insert(intent(secondHash, created, created.plusSeconds(1L))).join();

            assertEquals(1, persistence.commandIntents().deleteExpired(created.plusSeconds(2L), 1).join());
            assertEquals(1, persistence.commandIntents().deleteExpired(created.plusSeconds(2L), 10).join());

            assertTrue(persistence.commandIntents().find(firstHash).join().isEmpty()
                || persistence.commandIntents().find(secondHash).join().isEmpty());
        } finally {
            cleanup(persistence);
        }
    }

    private static CommandIntentDraft intent(byte[] hash, Instant created, Instant expiresAt) {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        return new CommandIntentDraft(
            hash,
            OperationId.generate(),
            CommandIntentType.PAY,
            CommandIntentState.AWAITING_CONFIRMATION,
            Optional.of(senderId),
            ActorType.PLAYER,
            Optional.of(senderId),
            senderId,
            Optional.of(receiverId),
            100L,
            OperationReason.of("test:pay"),
            Optional.of(1L),
            "server-a",
            created,
            expiresAt
        );
    }

    private static byte[] hash(int marker) {
        byte[] hash = new byte[32];
        hash[31] = (byte) marker;
        return hash;
    }
}
