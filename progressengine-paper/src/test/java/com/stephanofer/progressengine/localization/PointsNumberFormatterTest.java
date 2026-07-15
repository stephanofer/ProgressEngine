package com.stephanofer.progressengine.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.stephanofer.progressengine.config.NumberFormatSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PointsNumberFormatterTest {
    private static final NumberFormatSettings EN = new NumberFormatSettings(
        ",",
        ".",
        1,
        false,
        Map.of(
            NumberFormatSettings.CompactMagnitude.THOUSAND, "K",
            NumberFormatSettings.CompactMagnitude.MILLION, "M",
            NumberFormatSettings.CompactMagnitude.BILLION, "B",
            NumberFormatSettings.CompactMagnitude.TRILLION, "T",
            NumberFormatSettings.CompactMagnitude.QUADRILLION, "Qa",
            NumberFormatSettings.CompactMagnitude.QUINTILLION, "Qi"
        )
    );

    @Test
    void formatsFullLongRangeWithoutOverflow() {
        assertEquals("0", PointsNumberFormatter.formatted(0L, EN));
        assertEquals("1,234,567", PointsNumberFormatter.formatted(1_234_567L, EN));
        assertEquals("-9,223,372,036,854,775,808", PointsNumberFormatter.formatted(Long.MIN_VALUE, EN));
        assertEquals("9,223,372,036,854,775,807", PointsNumberFormatter.formatted(Long.MAX_VALUE, EN));
    }

    @Test
    void compactsByTruncatingPresentationOnly() {
        assertEquals("999", PointsNumberFormatter.compact(999L, EN));
        assertEquals("1K", PointsNumberFormatter.compact(1_000L, EN));
        assertEquals("1.9K", PointsNumberFormatter.compact(1_999L, EN));
        assertEquals("1.2M", PointsNumberFormatter.compact(1_234_567L, EN));
        assertEquals("-1.2M", PointsNumberFormatter.compact(-1_234_567L, EN));
    }
}
