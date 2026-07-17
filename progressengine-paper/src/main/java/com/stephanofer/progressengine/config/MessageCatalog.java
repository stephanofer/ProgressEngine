package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MessageCatalog(String language, NumberFormatSettings numberFormat, CurrencySettings currency, Map<String, String> messages,
                              Map<String, List<FeedbackActionConfig>> feedback) {
    public MessageCatalog {
        language = Objects.requireNonNull(language, "language");
        Objects.requireNonNull(numberFormat, "numberFormat");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(feedback, "feedback");
        messages = Map.copyOf(messages);
        feedback = feedback.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public MessageCatalog(String language, NumberFormatSettings numberFormat, Map<String, String> messages,
                          Map<String, List<FeedbackActionConfig>> feedback) {
        this(language, numberFormat, CurrencySettings.defaults(), messages, feedback);
    }
}
