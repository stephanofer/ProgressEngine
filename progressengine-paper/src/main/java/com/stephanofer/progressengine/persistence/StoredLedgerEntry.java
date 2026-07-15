package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StoredLedgerEntry(long ledgerId, OperationId operationId, UUID playerId, Optional<UUID> relatedPlayerId,
                                long delta, long balanceBefore, long balanceAfter, long revision, Instant createdAt) {
    public StoredLedgerEntry {
        if (ledgerId < 1L) throw new PersistenceDataException("Stored ledger id must be positive");
        Objects.requireNonNull(operationId, "operationId");
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        UUID validatedPlayerId = playerId;
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(related -> {
            BinaryUuid.requireValid(related, "relatedPlayerId");
            if (related.equals(validatedPlayerId)) {
                throw new PersistenceDataException("Stored ledger relatedPlayerId cannot equal playerId");
            }
        });
        if (balanceBefore < 0L || balanceAfter < 0L) {
            throw new PersistenceDataException("Stored ledger balances cannot be negative");
        }
        if (revision < 1L) throw new PersistenceDataException("Stored ledger revision must be positive");
        if (delta != Math.subtractExact(balanceAfter, balanceBefore)) {
            throw new PersistenceDataException("Stored ledger delta is inconsistent with balances");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
