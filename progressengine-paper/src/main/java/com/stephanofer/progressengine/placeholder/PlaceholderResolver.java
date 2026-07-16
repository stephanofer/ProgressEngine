package com.stephanofer.progressengine.placeholder;

import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.MessageCatalog;
import com.stephanofer.progressengine.localization.LocalizedMessages;
import com.stephanofer.progressengine.localization.PointsNumberFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PlaceholderResolver {
    static final String POINTS = "points";
    static final String POINTS_FORMATTED = "points_formatted";
    static final String POINTS_COMPACT = "points_compact";
    static final String READY = "ready";

    private final Predicate<UUID> readyLookup;
    private final Function<UUID, Optional<BalanceSnapshot>> snapshotLookup;
    private final Function<UUID, String> languageLookup;
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;

    public PlaceholderResolver(Predicate<UUID> readyLookup, Function<UUID, Optional<BalanceSnapshot>> snapshotLookup,
                               Function<UUID, String> languageLookup, Supplier<ConfigurationSnapshot> snapshotSupplier) {
        this.readyLookup = Objects.requireNonNull(readyLookup, "readyLookup");
        this.snapshotLookup = Objects.requireNonNull(snapshotLookup, "snapshotLookup");
        this.languageLookup = Objects.requireNonNull(languageLookup, "languageLookup");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    String resolve(UUID playerId, String params, boolean active) {
        String normalized = normalizeParams(params);
        if (!isKnown(normalized)) {
            return null;
        }
        if (!active || playerId == null) {
            return inactiveValue(normalized);
        }

        Optional<BalanceSnapshot> snapshot = availableSnapshot(playerId);
        if (READY.equals(normalized)) {
            return Boolean.toString(snapshot.isPresent());
        }
        if (snapshot.isEmpty()) {
            return POINTS.equals(normalized) ? "" : loadingText(playerId);
        }

        long balance = snapshot.orElseThrow().balance();
        ConfigurationSnapshot configuration = this.snapshotSupplier.get();
        MessageCatalog catalog = catalog(playerId, configuration);
        return switch (normalized) {
            case POINTS -> PointsNumberFormatter.raw(balance);
            case POINTS_FORMATTED -> PointsNumberFormatter.formatted(balance, catalog.numberFormat());
            case POINTS_COMPACT -> PointsNumberFormatter.compact(balance, catalog.numberFormat());
            default -> null;
        };
    }

    private Optional<BalanceSnapshot> availableSnapshot(UUID playerId) {
        if (!this.readyLookup.test(playerId)) {
            return Optional.empty();
        }
        Optional<BalanceSnapshot> snapshot = this.snapshotLookup.apply(playerId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        if (!this.readyLookup.test(playerId)) {
            return Optional.empty();
        }
        return snapshot;
    }

    private String loadingText(UUID playerId) {
        ConfigurationSnapshot configuration = this.snapshotSupplier.get();
        return catalog(playerId, configuration).numberFormat().loadingText();
    }

    private MessageCatalog catalog(UUID playerId, ConfigurationSnapshot configuration) {
        String language = this.languageLookup.apply(playerId);
        if (language == null || language.isBlank()) {
            language = configuration.localization().fallbackLanguage();
        } else {
            language = LocalizedMessages.normalizeLanguage(language);
        }
        return configuration.messages().require(language);
    }

    private static String inactiveValue(String normalized) {
        return READY.equals(normalized) ? "false" : "";
    }

    private static String normalizeParams(String params) {
        if (params == null) {
            return "";
        }
        return params.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isKnown(String params) {
        return POINTS.equals(params) || POINTS_FORMATTED.equals(params) || POINTS_COMPACT.equals(params) || READY.equals(params);
    }
}
