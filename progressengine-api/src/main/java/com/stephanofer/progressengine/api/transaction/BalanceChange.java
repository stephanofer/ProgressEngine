package com.stephanofer.progressengine.api.transaction;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.util.Optional;
import java.util.UUID;

/**
 * Confirmed balance movement for one account.
 */
public record BalanceChange(UUID playerId, Optional<UUID> relatedPlayerId, long delta, long balanceBefore,
                            long balanceAfter, long revision) {

    /**
     * Creates a balance change.
     *
     * @param playerId affected account
     * @param relatedPlayerId optional counterparty account
     * @param delta signed change
     * @param balanceBefore locked balance before the movement
     * @param balanceAfter confirmed balance after the movement
     * @param revision resulting account revision
     */
    public BalanceChange {
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        if (relatedPlayerId == null) {
            throw new NullPointerException("relatedPlayerId cannot be null");
        }
        if (relatedPlayerId.isPresent()) {
            UUID related = ApiValidation.requireUuid(relatedPlayerId.get(), "relatedPlayerId");
            if (related.equals(playerId)) {
                throw new IllegalArgumentException("relatedPlayerId cannot equal playerId");
            }
            relatedPlayerId = Optional.of(related);
        }
        balanceBefore = ApiValidation.requireNonNegative(balanceBefore, "balanceBefore");
        balanceAfter = ApiValidation.requireNonNegative(balanceAfter, "balanceAfter");
        revision = ApiValidation.requirePositiveRevision(revision, "revision");
        long expectedDelta = Math.subtractExact(balanceAfter, balanceBefore);
        if (delta != expectedDelta) {
            throw new IllegalArgumentException("delta must equal balanceAfter - balanceBefore");
        }
    }

    /**
     * Creates a balance change without a counterparty.
     *
     * @param playerId affected account
     * @param delta signed change
     * @param balanceBefore locked balance before the movement
     * @param balanceAfter confirmed balance after the movement
     * @param revision resulting account revision
     * @return the balance change
     */
    public static BalanceChange single(UUID playerId, long delta, long balanceBefore, long balanceAfter, long revision) {
        return new BalanceChange(playerId, Optional.empty(), delta, balanceBefore, balanceAfter, revision);
    }

    /**
     * Creates a balance change with a counterparty.
     *
     * @param playerId affected account
     * @param relatedPlayerId counterparty account
     * @param delta signed change
     * @param balanceBefore locked balance before the movement
     * @param balanceAfter confirmed balance after the movement
     * @param revision resulting account revision
     * @return the balance change
     */
    public static BalanceChange related(UUID playerId, UUID relatedPlayerId, long delta, long balanceBefore,
                                        long balanceAfter, long revision) {
        return new BalanceChange(playerId, Optional.of(relatedPlayerId), delta, balanceBefore, balanceAfter, revision);
    }
}
