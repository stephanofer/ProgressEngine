package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HistoryEntry(long ledgerId, OperationId operationId, OperationType type, UUID playerId,
                           Optional<UUID> relatedPlayerId, long delta, long balanceBefore, long balanceAfter,
                           long revision, OperationReason reason, OperationActor actor, OperationSource source,
                           Instant createdAt) {
    public HistoryEntry {
        if (ledgerId < 1L) throw new PersistenceDataException("History ledger id must be positive");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(id -> BinaryUuid.requireValid(id, "relatedPlayerId"));
        if (balanceBefore < 0L || balanceAfter < 0L) throw new PersistenceDataException("History balances cannot be negative");
        if (revision < 1L) throw new PersistenceDataException("History revision must be positive");
        if (delta != Math.subtractExact(balanceAfter, balanceBefore)) throw new PersistenceDataException("History delta is inconsistent");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
