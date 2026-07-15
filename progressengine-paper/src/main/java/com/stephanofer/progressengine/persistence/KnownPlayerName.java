package com.stephanofer.progressengine.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KnownPlayerName(String normalizedUsername, UUID playerId, String username, Instant lastSeenAt) {
    public KnownPlayerName {
        normalizedUsername = PlayerUsernames.normalize(normalizedUsername);
        playerId = BinaryUuid.requireValid(playerId, "playerId");
        username = PlayerUsernames.requireValid(username);
        if (!normalizedUsername.equals(PlayerUsernames.normalize(username))) {
            throw new PersistenceDataException("Stored normalized username does not match username");
        }
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    }
}
