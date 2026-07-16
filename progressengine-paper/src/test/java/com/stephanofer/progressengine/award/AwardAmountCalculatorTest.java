package com.stephanofer.progressengine.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.result.AwardCalculation;
import com.stephanofer.progressengine.booster.AwardBoostCalculation;
import com.stephanofer.progressengine.config.AwardRounding;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AwardAmountCalculatorTest {
    @Test
    void floorsDecimalBoostedAmountsWithoutRoundingUp() {
        AwardCalculation calculation = AwardAmountCalculator.calculate(
            10L,
            10L,
            new AwardBoostCalculation(new BigDecimal("1.25"), new BigDecimal("12.99"), List.of("points_x125"), false, true),
            AwardRounding.FLOOR
        );

        assertEquals(12L, calculation.finalAmount());
        assertEquals(new BigDecimal("12.99"), calculation.calculatedAmount());
        assertEquals(List.of("points_x125"), calculation.appliedBoosterIds());
        assertTrue(calculation.boostersEvaluated());
        assertFalse(calculation.capped());
    }

    @Test
    void preservesCappedCalculationMetadata() {
        AwardCalculation calculation = AwardAmountCalculator.calculate(
            100L,
            80L,
            new AwardBoostCalculation(new BigDecimal("5"), new BigDecimal("250"), List.of("global_cap"), true, true),
            AwardRounding.FLOOR
        );

        assertEquals(100L, calculation.requestedBaseAmount());
        assertEquals(80L, calculation.preparedBaseAmount());
        assertEquals(250L, calculation.finalAmount());
        assertTrue(calculation.capped());
    }

    @Test
    void representsFloorToZeroAsZeroCalculationForDurableNoMovementAward() {
        AwardCalculation calculation = AwardAmountCalculator.calculate(
            1L,
            1L,
            new AwardBoostCalculation(new BigDecimal("0.5"), new BigDecimal("0.5"), List.of(), true, true),
            AwardRounding.FLOOR
        );

        assertEquals(0L, calculation.finalAmount());
        assertFalse(AwardAmountCalculator.exceedsLong(calculation));
    }

    @Test
    void detectsRoundedAmountsThatExceedLongEvenWhenFinalAmountIsSaturated() {
        AwardCalculation calculation = AwardAmountCalculator.calculate(
            10L,
            10L,
            new AwardBoostCalculation(BigDecimal.ONE, new BigDecimal("9223372036854775808"), List.of(), true, true),
            AwardRounding.FLOOR
        );

        assertEquals(Long.MAX_VALUE, calculation.finalAmount());
        assertTrue(AwardAmountCalculator.exceedsLong(calculation));
    }

    @Test
    void rejectsUnsupportedRoundingPoliciesFailClosed() {
        assertThrows(IllegalStateException.class, () -> AwardAmountCalculator.calculate(
            1L,
            1L,
            AwardBoostCalculation.neutral(1L, false),
            null
        ));
    }
}
