package com.stephanofer.progressengine.booster;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.calculation.BoostCalculation;
import com.stephanofer.networkboosters.api.calculation.BoostRequest;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NetworkBoostersAwardCalculator implements AwardBoosterCalculator {
    private final NetworkBoostersService service;

    public NetworkBoostersAwardCalculator(NetworkBoostersService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public CompletableFuture<AwardBoostCalculation> calculate(UUID playerId, long preparedBaseAmount, Optional<String> gameId, String serverId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(serverId, "serverId");
        BigDecimal base = BigDecimal.valueOf(preparedBaseAmount);
        return this.service.load(playerId).thenApply(snapshot -> {
            BoostRequest request = BoostRequest.of(
                playerId,
                BoosterTarget.NETWORK_PROGRESSION_POINTS,
                base,
                gameId.orElse(null),
                serverId
            );
            BoostCalculation calculation = this.service.calculate(request, snapshot);
            validateCalculation(base, calculation);
            return new AwardBoostCalculation(
                calculation.multiplier(),
                calculation.finalAmount(),
                calculation.appliedBoosts().stream().map(applied -> applied.boosterId().value()).toList(),
                calculation.capped(),
                true
            );
        });
    }

    private static void validateCalculation(BigDecimal expectedBase, BoostCalculation calculation) {
        Objects.requireNonNull(calculation, "calculation");
        if (calculation.baseAmount().compareTo(expectedBase) != 0) {
            throw new IllegalStateException("NetworkBoosters returned a calculation for a different base amount");
        }
        if (calculation.multiplier().signum() <= 0) {
            throw new IllegalStateException("NetworkBoosters returned a non-positive multiplier");
        }
        if (calculation.finalAmount().signum() < 0) {
            throw new IllegalStateException("NetworkBoosters returned a negative final amount");
        }
    }
}
