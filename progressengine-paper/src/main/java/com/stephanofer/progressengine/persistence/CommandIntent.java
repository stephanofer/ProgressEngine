package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.ActorType;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CommandIntent(byte[] tokenHash, OperationId operationId, CommandIntentType type,
                            CommandIntentState state, Optional<UUID> ownerId, ActorType actorType,
                            Optional<UUID> actorId, UUID playerId, Optional<UUID> targetId,
                            long amount, OperationReason reason, Optional<Long> observedRevision,
                            String sourceServerId, Instant createdAt, Instant expiresAt,
                            Optional<Instant> submittedAt, Optional<Instant> resolvedAt) {
    public CommandIntent {
        tokenHash = CommandIntentDraft.validateHash(tokenHash);
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ownerId, "ownerId");
        ownerId.ifPresent(id -> BinaryUuid.requireValid(id, "ownerId"));
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        Objects.requireNonNull(targetId, "targetId");
        targetId.ifPresent(id -> BinaryUuid.requireValid(id, "targetId"));
        if (amount < 0L) throw new PersistenceDataException("Stored command intent amount cannot be negative");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(observedRevision, "observedRevision");
        if (sourceServerId == null || sourceServerId.isBlank()) throw new PersistenceDataException("Stored sourceServerId cannot be blank");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    public OperationActor actor() {
        return this.actorType == ActorType.PLAYER
            ? OperationActor.player(this.actorId.orElseThrow())
            : OperationActor.console();
    }

    public boolean expiredAt(Instant now) {
        return !this.expiresAt.isAfter(now);
    }
}
