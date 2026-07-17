package com.stephanofer.progressengine.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.stephanofer.progressengine.config.CurrencySettings;
import com.stephanofer.progressengine.config.MessageCatalog;
import com.stephanofer.progressengine.config.NumberFormatSettings;
import com.stephanofer.progressengine.config.PriceFormat;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

final class PointsDisplayTest {
    @Test
    void styledUsesAbsoluteMagnitudeForNegativeDeltas() {
        ProgressEngineConfig.AmountColors colors = new ProgressEngineConfig.AmountColors(true, List.of(
            new ProgressEngineConfig.AmountColorTier(0L, "#111111"),
            new ProgressEngineConfig.AmountColorTier(1_000L, "#222222"),
            new ProgressEngineConfig.AmountColorTier(1_000_000L, "#333333")
        ));

        assertEquals(TextColor.fromHexString("#333333"), PointsDisplay.styled(-1_500_000L, catalog(), colors).color());
    }

    private static MessageCatalog catalog() {
        return new MessageCatalog("en", numberFormat(), new CurrencySettings("Points", "", "%price% %display-name%", PriceFormat.COMPACT), Map.of(), Map.of());
    }

    private static NumberFormatSettings numberFormat() {
        return new NumberFormatSettings(",", ".", 1, false, Map.of(
            NumberFormatSettings.CompactMagnitude.THOUSAND, "K",
            NumberFormatSettings.CompactMagnitude.MILLION, "M",
            NumberFormatSettings.CompactMagnitude.BILLION, "B",
            NumberFormatSettings.CompactMagnitude.TRILLION, "T",
            NumberFormatSettings.CompactMagnitude.QUADRILLION, "Qa",
            NumberFormatSettings.CompactMagnitude.QUINTILLION, "Qi"
        ), "Loading...");
    }
}
