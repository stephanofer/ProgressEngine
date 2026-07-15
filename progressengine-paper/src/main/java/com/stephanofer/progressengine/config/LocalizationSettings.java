package com.stephanofer.progressengine.config;

import java.util.Objects;

public record LocalizationSettings(String fallbackLanguage, String consoleLanguage, long awardCoalescingWindowTicks) {
    public LocalizationSettings {
        fallbackLanguage = requireLanguage(fallbackLanguage, "fallbackLanguage");
        consoleLanguage = requireLanguage(consoleLanguage, "consoleLanguage");
        if (awardCoalescingWindowTicks < 0L || awardCoalescingWindowTicks > 200L) {
            throw new IllegalArgumentException("awardCoalescingWindowTicks must be between 0 and 200");
        }
    }

    private static String requireLanguage(String value, String name) {
        Objects.requireNonNull(value, name);
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "es" -> "es";
            case "en" -> "en";
            default -> throw new IllegalArgumentException(name + " must be es or en");
        };
    }
}
