package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.math.BigDecimal;
import java.util.List;

/**
 * Public breakdown of an award calculation.
 */
public record AwardCalculation(long requestedBaseAmount, long preparedBaseAmount, BigDecimal multiplier,
                               BigDecimal calculatedAmount, long finalAmount, List<String> appliedBoosterIds,
                               boolean capped, boolean boostersEvaluated) {

    /**
     * Creates an award calculation breakdown.
     */
    public AwardCalculation {
        requestedBaseAmount = ApiValidation.requirePositive(requestedBaseAmount, "requestedBaseAmount");
        preparedBaseAmount = ApiValidation.requirePositive(preparedBaseAmount, "preparedBaseAmount");
        if (multiplier == null) throw new NullPointerException("multiplier cannot be null");
        if (multiplier.signum() <= 0) throw new IllegalArgumentException("multiplier must be positive");
        if (calculatedAmount == null) throw new NullPointerException("calculatedAmount cannot be null");
        if (calculatedAmount.signum() < 0) throw new IllegalArgumentException("calculatedAmount cannot be negative");
        finalAmount = ApiValidation.requireNonNegative(finalAmount, "finalAmount");
        if (appliedBoosterIds == null) throw new NullPointerException("appliedBoosterIds cannot be null");
        appliedBoosterIds = appliedBoosterIds.stream()
            .map(id -> ApiValidation.requireText(id, "appliedBoosterId", 128))
            .toList();
    }
}
