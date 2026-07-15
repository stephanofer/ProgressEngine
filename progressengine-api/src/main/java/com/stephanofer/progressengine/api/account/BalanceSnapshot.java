package com.stephanofer.progressengine.api.account;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable local view of a player's point balance.
 */
public record BalanceSnapshot(UUID playerId, long balance, long revision, Instant loadedAt) {

    /**
     * Creates a balance snapshot.
     *
     * @param playerId the account owner
     * @param balance the non-negative cached balance
     * @param revision the account revision observed with this balance
     * @param loadedAt when this snapshot was loaded or published
     */
    public BalanceSnapshot {
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        balance = ApiValidation.requireNonNegative(balance, "balance");
        revision = ApiValidation.requireNonNegative(revision, "revision");
        if (loadedAt == null) {
            throw new NullPointerException("loadedAt cannot be null");
        }
    }
}
