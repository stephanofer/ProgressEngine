package com.stephanofer.progressengine.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StoredAccount(UUID playerId, long balance, long revision, Instant createdAt, Instant updatedAt) {
    public StoredAccount {
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        if (balance < 0L) throw new PersistenceDataException("Stored account balance cannot be negative");
        if (revision < 0L) throw new PersistenceDataException("Stored account revision cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new PersistenceDataException("Stored account updatedAt cannot be before createdAt");
        }
    }
}
