package com.stephanofer.progressengine.award;

import com.stephanofer.progressengine.api.result.AwardCalculation;
import com.stephanofer.progressengine.booster.AwardBoostCalculation;
import com.stephanofer.progressengine.config.AwardRounding;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

public final class AwardAmountCalculator {
    private AwardAmountCalculator() {
    }

    public static AwardCalculation calculate(long requestedBaseAmount, long preparedBaseAmount, AwardBoostCalculation boost,
                                             AwardRounding rounding) {
        Objects.requireNonNull(boost, "boost");
        if (rounding != AwardRounding.FLOOR) {
            throw new IllegalStateException("Unsupported award rounding policy " + rounding);
        }
        BigDecimal rounded = boost.calculatedAmount().setScale(0, RoundingMode.FLOOR);
        long finalAmount = rounded.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
            ? Long.MAX_VALUE
            : rounded.longValueExact();
        return new AwardCalculation(
            requestedBaseAmount,
            preparedBaseAmount,
            boost.multiplier(),
            boost.calculatedAmount(),
            finalAmount,
            boost.appliedBoosterIds(),
            boost.capped(),
            boost.boostersEvaluated()
        );
    }

    public static boolean exceedsLong(AwardCalculation calculation) {
        BigInteger rounded = calculation.calculatedAmount().setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        return rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;
    }
}
