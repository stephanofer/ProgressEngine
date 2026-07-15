package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.ActorType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CommandIntentDraft(byte[] tokenHash, OperationId operationId, CommandIntentType type,
                                 CommandIntentState state, Optional<UUID> ownerId, ActorType actorType,
                                 Optional<UUID> actorId, UUID playerId, Optional<UUID> targetId,
                                 long amount, OperationReason reason, Optional<Long> observedRevision,
                                 String sourceServerId, Instant createdAt, Instant expiresAt) {
    public CommandIntentDraft {
        tokenHash = validateHash(tokenHash);
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        if (state != CommandIntentState.AWAITING_CONFIRMATION && state != CommandIntentState.SUBMITTED) {
            throw new IllegalArgumentException("new command intent must be awaiting confirmation or submitted");
        }
        Objects.requireNonNull(ownerId, "ownerId");
        ownerId.ifPresent(id -> BinaryUuid.requireValid(id, "ownerId"));
        Objects.requireNonNull(actorType, "actorType");
        if (actorType != ActorType.PLAYER && actorType != ActorType.CONSOLE) {
            throw new IllegalArgumentException("command intent actor must be player or console");
        }
        Objects.requireNonNull(actorId, "actorId");
        if (actorType == ActorType.PLAYER) {
            actorId = Optional.of(BinaryUuid.requireValid(actorId.orElseThrow(() -> new IllegalArgumentException("player actor requires id")), "actorId"));
        } else if (actorId.isPresent()) {
            throw new IllegalArgumentException("console actor cannot include actor id");
        }
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        UUID validPlayerId = playerId;
        Objects.requireNonNull(targetId, "targetId");
        targetId.ifPresent(target -> {
            BinaryUuid.requireValid(target, "targetId");
            if (target.equals(validPlayerId)) throw new IllegalArgumentException("targetId cannot equal playerId");
        });
        if (amount < 0L) throw new IllegalArgumentException("amount cannot be negative");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(observedRevision, "observedRevision");
        observedRevision.ifPresent(revision -> {
            if (revision < 1L) throw new IllegalArgumentException("observedRevision must be positive");
        });
        if (sourceServerId == null || sourceServerId.isBlank()) throw new IllegalArgumentException("sourceServerId cannot be blank");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must be after createdAt");
    }

    static byte[] validateHash(byte[] hash) {
        Objects.requireNonNull(hash, "hash");
        if (hash.length != 32) throw new IllegalArgumentException("token hash must be 32 bytes");
        return hash.clone();
    }
}
