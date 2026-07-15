package com.stephanofer.progressengine.localization;

import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.FeedbackActionConfig;
import com.stephanofer.progressengine.config.MessageCatalog;
import com.stephanofer.progressengine.config.NumberFormatSettings;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class LocalizedMessages {
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LocalizedMessages(Supplier<ConfigurationSnapshot> snapshotSupplier) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    public Component message(String key, String language, MessageArguments arguments) {
        MessageCatalog catalog = catalog(language);
        String template = catalog.messages().get(key);
        if (template == null) {
            template = fallback().messages().getOrDefault(key, key);
        }
        return render(template, arguments);
    }

    public List<FeedbackActionConfig> feedback(String key, String language) {
        MessageCatalog catalog = catalog(language);
        List<FeedbackActionConfig> actions = catalog.feedback().get(key);
        if (actions == null) {
            actions = fallback().feedback().getOrDefault(key, List.of());
        }
        return actions;
    }

    public NumberFormatSettings numberFormat(String language) {
        return catalog(language).numberFormat();
    }

    public String raw(long value) {
        return PointsNumberFormatter.raw(value);
    }

    public String formatted(long value, String language) {
        return PointsNumberFormatter.formatted(value, numberFormat(language));
    }

    public String compact(long value, String language) {
        return PointsNumberFormatter.compact(value, numberFormat(language));
    }

    public Component render(String template, MessageArguments arguments) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(arguments, "arguments");
        TagResolver.Builder builder = TagResolver.builder();
        arguments.unparsed().forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, value)));
        arguments.components().forEach((key, value) -> builder.resolver(Placeholder.component(key, value)));
        return this.miniMessage.deserialize(template, builder.build());
    }

    private MessageCatalog catalog(String language) {
        String normalized = normalizeLanguage(language);
        ConfigurationSnapshot snapshot = this.snapshotSupplier.get();
        MessageCatalog catalog = snapshot.messages().catalogs().get(normalized);
        if (catalog != null) {
            return catalog;
        }
        return fallback();
    }

    private MessageCatalog fallback() {
        ConfigurationSnapshot snapshot = this.snapshotSupplier.get();
        return snapshot.messages().require(snapshot.localization().fallbackLanguage());
    }

    public static String normalizeLanguage(String language) {
        if (language == null) {
            return "en";
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "es" -> "es";
            case "en" -> "en";
            default -> "en";
        };
    }
}
