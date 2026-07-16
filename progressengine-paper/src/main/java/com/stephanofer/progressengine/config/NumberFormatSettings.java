package com.stephanofer.progressengine.config;

import java.util.Map;
import java.util.Objects;

public record NumberFormatSettings(String groupingSeparator, String decimalSeparator, int compactDecimals,
                                   boolean compactSpace, Map<CompactMagnitude, String> compactSuffixes,
                                   String loadingText) {
    public NumberFormatSettings {
        groupingSeparator = requireSeparator(groupingSeparator, "groupingSeparator");
        decimalSeparator = requireSeparator(decimalSeparator, "decimalSeparator");
        if (groupingSeparator.equals(decimalSeparator)) {
            throw new IllegalArgumentException("groupingSeparator and decimalSeparator must be different");
        }
        if (compactDecimals < 0 || compactDecimals > 2) {
            throw new IllegalArgumentException("compactDecimals must be between 0 and 2");
        }
        Objects.requireNonNull(compactSuffixes, "compactSuffixes");
        compactSuffixes = Map.copyOf(compactSuffixes);
        for (CompactMagnitude magnitude : CompactMagnitude.values()) {
            String suffix = compactSuffixes.get(magnitude);
            if (suffix == null || suffix.isBlank() || suffix.length() > 8) {
                throw new IllegalArgumentException("compact suffix for " + magnitude + " must be present and at most 8 characters");
            }
        }
        loadingText = requireLoadingText(loadingText);
    }

    private static String requireSeparator(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > 3) {
            throw new IllegalArgumentException(name + " must contain 1 to 3 characters");
        }
        return value;
    }

    private static String requireLoadingText(String value) {
        Objects.requireNonNull(value, "loadingText");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 64) {
            throw new IllegalArgumentException("loadingText must be plain text between 1 and 64 characters without surrounding whitespace");
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new IllegalArgumentException("loadingText must be plain text, not MiniMessage");
        }
        return value;
    }

    public enum CompactMagnitude {
        THOUSAND(1_000L, "thousand"),
        MILLION(1_000_000L, "million"),
        BILLION(1_000_000_000L, "billion"),
        TRILLION(1_000_000_000_000L, "trillion"),
        QUADRILLION(1_000_000_000_000_000L, "quadrillion"),
        QUINTILLION(1_000_000_000_000_000_000L, "quintillion");

        private final long divisor;
        private final String configKey;

        CompactMagnitude(long divisor, String configKey) {
            this.divisor = divisor;
            this.configKey = configKey;
        }

        public long divisor() {
            return this.divisor;
        }

        public String configKey() {
            return this.configKey;
        }

        public static CompactMagnitude fromConfigKey(String key) {
            for (CompactMagnitude magnitude : values()) {
                if (magnitude.configKey.equals(key)) {
                    return magnitude;
                }
            }
            throw new IllegalArgumentException("unknown compact magnitude: " + key);
        }
    }
}
