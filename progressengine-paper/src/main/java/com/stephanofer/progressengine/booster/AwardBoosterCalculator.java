package com.stephanofer.progressengine.booster;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface AwardBoosterCalculator {
    CompletableFuture<AwardBoostCalculation> calculate(UUID playerId, long preparedBaseAmount, Optional<String> gameId, String serverId);

    static AwardBoosterCalculator disabled() {
        return (playerId, preparedBaseAmount, gameId, serverId) ->
            CompletableFuture.completedFuture(AwardBoostCalculation.neutral(preparedBaseAmount, false));
    }

    static AwardBoosterCalculator unavailable(String reason) {
        return (playerId, preparedBaseAmount, gameId, serverId) ->
            CompletableFuture.failedFuture(new IllegalStateException(reason));
    }
}
