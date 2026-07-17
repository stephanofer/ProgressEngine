package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PayConfirmationDialogSettings(boolean canCloseWithEscape, boolean pause, int bodyWidth,
                                            int confirmButtonWidth, int cancelButtonWidth,
                                            Map<String, Locale> locales) {
    public PayConfirmationDialogSettings {
        if (bodyWidth < 1 || bodyWidth > 1024
            || confirmButtonWidth < 1 || confirmButtonWidth > 1024
            || cancelButtonWidth < 1 || cancelButtonWidth > 1024) {
            throw new IllegalArgumentException("dialog widths must be between 1 and 1024");
        }
        locales = Map.copyOf(Objects.requireNonNull(locales, "locales"));
        if (!locales.containsKey("en") || !locales.containsKey("es")) {
            throw new IllegalArgumentException("dialog locales must contain en and es");
        }
    }

    public record Locale(String title, String externalTitle, List<String> body, String confirmLabel,
                          String confirmTooltip, String cancelLabel, String cancelTooltip) {
        public Locale {
            title = text(title);
            externalTitle = text(externalTitle);
            body = List.copyOf(Objects.requireNonNull(body, "body"));
            if (body.isEmpty() || body.stream().allMatch(String::isBlank)) {
                throw new IllegalArgumentException("dialog body cannot be empty");
            }
            for (String line : body) {
                if (line == null || line.length() > 512) {
                    throw new IllegalArgumentException("dialog body line is invalid");
                }
            }
            confirmLabel = text(confirmLabel);
            confirmTooltip = text(confirmTooltip);
            cancelLabel = text(cancelLabel);
            cancelTooltip = text(cancelTooltip);
        }

        private static String text(String value) {
            if (value == null || value.isBlank() || value.length() > 512) {
                throw new IllegalArgumentException("dialog text is invalid");
            }
            return value;
        }
    }
}
