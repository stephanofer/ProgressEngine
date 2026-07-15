package com.stephanofer.progressengine.booster;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record AwardBoostCalculation(BigDecimal multiplier, BigDecimal calculatedAmount, List<String> appliedBoosterIds,
                                    boolean capped, boolean boostersEvaluated) {
    public AwardBoostCalculation {
        multiplier = Objects.requireNonNull(multiplier, "multiplier").stripTrailingZeros();
        calculatedAmount = Objects.requireNonNull(calculatedAmount, "calculatedAmount");
        if (multiplier.signum() <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        if (calculatedAmount.signum() < 0) {
            throw new IllegalArgumentException("calculatedAmount cannot be negative");
        }
        appliedBoosterIds = List.copyOf(Objects.requireNonNull(appliedBoosterIds, "appliedBoosterIds"));
    }

    public static AwardBoostCalculation neutral(long preparedBaseAmount, boolean boostersEvaluated) {
        BigDecimal base = BigDecimal.valueOf(preparedBaseAmount);
        return new AwardBoostCalculation(BigDecimal.ONE, base, List.of(), false, boostersEvaluated);
    }
}
