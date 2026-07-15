package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record LedgerEntryDraft(OperationId operationId, UUID playerId, Optional<UUID> relatedPlayerId, long delta,
                               long balanceBefore, long balanceAfter, long revision, Instant createdAt) {
    public LedgerEntryDraft {
        Objects.requireNonNull(operationId, "operationId");
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        UUID validatedPlayerId = playerId;
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(related -> {
            BinaryUuid.requireValid(related, "relatedPlayerId");
            if (related.equals(validatedPlayerId)) throw new IllegalArgumentException("relatedPlayerId cannot equal playerId");
        });
        if (balanceBefore < 0L || balanceAfter < 0L) throw new IllegalArgumentException("balances cannot be negative");
        if (revision < 1L) throw new IllegalArgumentException("revision must be positive");
        if (delta != Math.subtractExact(balanceAfter, balanceBefore)) {
            throw new IllegalArgumentException("delta must equal balanceAfter - balanceBefore");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
