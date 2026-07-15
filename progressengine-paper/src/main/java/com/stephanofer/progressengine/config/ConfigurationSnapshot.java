package com.stephanofer.progressengine.config;

import java.time.Instant;
import java.util.Objects;

public record ConfigurationSnapshot(long revision, Instant loadedAt, ProgressEngineConfig config,
                                    LocalizationSettings localization, IdentitySettings identity,
                                    MessageCatalogs messages) {
    public ConfigurationSnapshot {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(loadedAt, "loadedAt");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(localization, "localization");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(messages, "messages");
    }

    public ConfigurationSnapshot(long revision, Instant loadedAt, ProgressEngineConfig config) {
        this(
            revision,
            loadedAt,
            config,
            new LocalizationSettings("en", "es", 10L),
            new IdentitySettings(
                java.util.List.of(IdentitySettings.IdentityPart.PREFIX, IdentitySettings.IdentityPart.NICK, IdentitySettings.IdentityPart.COUNTRY_FLAG),
                " ",
                5_000L,
                300L
            ),
            new MessageCatalogs(java.util.Map.of(
                "en", new MessageCatalog("en", defaultNumberFormat(), java.util.Map.of(), java.util.Map.of()),
                "es", new MessageCatalog("es", defaultNumberFormat(), java.util.Map.of(), java.util.Map.of())
            ))
        );
    }

    private static NumberFormatSettings defaultNumberFormat() {
        return new NumberFormatSettings(
            ",",
            ".",
            1,
            false,
            java.util.Map.of(
                NumberFormatSettings.CompactMagnitude.THOUSAND, "K",
                NumberFormatSettings.CompactMagnitude.MILLION, "M",
                NumberFormatSettings.CompactMagnitude.BILLION, "B",
                NumberFormatSettings.CompactMagnitude.TRILLION, "T",
                NumberFormatSettings.CompactMagnitude.QUADRILLION, "Qa",
                NumberFormatSettings.CompactMagnitude.QUINTILLION, "Qi"
            )
        );
    }
}
