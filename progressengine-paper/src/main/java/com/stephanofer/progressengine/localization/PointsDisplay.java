package com.stephanofer.progressengine.localization;

import com.stephanofer.progressengine.config.CurrencySettings;
import com.stephanofer.progressengine.config.MessageCatalog;
import com.stephanofer.progressengine.config.PriceFormat;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public final class PointsDisplay {
    private PointsDisplay() {
    }

    public static String display(long value, MessageCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        CurrencySettings currency = catalog.currency();
        String price = switch (currency.priceFormat()) {
            case RAW -> PointsNumberFormatter.raw(value);
            case FORMATTED -> PointsNumberFormatter.formatted(value, catalog.numberFormat());
            case COMPACT -> PointsNumberFormatter.compact(value, catalog.numberFormat());
        };
        return currency.format()
            .replace("%price%", price)
            .replace("%symbol%", currency.symbol())
            .replace("%display-name%", currency.displayName());
    }

    public static Component styled(long value, MessageCatalog catalog, ProgressEngineConfig.AmountColors colors) {
        Component result = Component.text(display(value, catalog));
        if (!colors.enabled()) return result;
        long magnitude = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        String selected = colors.tiers().getFirst().color();
        for (ProgressEngineConfig.AmountColorTier tier : colors.tiers()) {
            if (magnitude >= tier.minimum()) selected = tier.color(); else break;
        }
        TextColor color = TextColor.fromHexString(selected);
        return color == null ? result : result.color(color);
    }
}
