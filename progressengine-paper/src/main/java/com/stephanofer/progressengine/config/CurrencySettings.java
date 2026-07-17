package com.stephanofer.progressengine.config;

import java.util.Objects;

public record CurrencySettings(String displayName, String symbol, String format, PriceFormat priceFormat) {
    public CurrencySettings {
        displayName = text(displayName, "displayName", false);
        symbol = text(symbol, "symbol", true);
        format = text(format, "format", false);
        if (format.indexOf("%price%") < 0 || format.indexOf("%price%") != format.lastIndexOf("%price%")) {
            throw new IllegalArgumentException("currency format must contain %price% exactly once");
        }
        String withoutKnownPlaceholders = format
            .replace("%price%", "")
            .replace("%symbol%", "")
            .replace("%display-name%", "");
        if (withoutKnownPlaceholders.contains("%")) {
            throw new IllegalArgumentException("currency format contains an unknown placeholder");
        }
        Objects.requireNonNull(priceFormat, "priceFormat");
    }

    public static CurrencySettings defaults() {
        return new CurrencySettings("Points", "", "%price% %display-name%", PriceFormat.COMPACT);
    }

    private static String text(String value, String name, boolean allowEmpty) {
        Objects.requireNonNull(value, name);
        if ((!allowEmpty && value.isBlank()) || value.length() > 64 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new IllegalArgumentException(name + " must be plain text between 1 and 64 characters");
        }
        return value;
    }
}
