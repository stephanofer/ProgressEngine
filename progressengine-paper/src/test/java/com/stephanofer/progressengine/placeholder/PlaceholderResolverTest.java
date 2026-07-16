package com.stephanofer.progressengine.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.AwardRounding;
import com.stephanofer.progressengine.config.CommandSettings;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.IdentitySettings;
import com.stephanofer.progressengine.config.LocalizationSettings;
import com.stephanofer.progressengine.config.MessageCatalog;
import com.stephanofer.progressengine.config.MessageCatalogs;
import com.stephanofer.progressengine.config.NumberFormatSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class PlaceholderResolverTest {
    private final UUID playerId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicReference<BalanceSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<String> language = new AtomicReference<>();
    private final AtomicReference<ConfigurationSnapshot> configuration = new AtomicReference<>(snapshot("en", enFormat(",", "Loading..."), esFormat(".", "Cargando...")));

    private final PlaceholderResolver resolver = new PlaceholderResolver(
        ignored -> this.ready.get(),
        ignored -> Optional.ofNullable(this.snapshot.get()),
        ignored -> this.language.get(),
        this.configuration::get
    );

    @Test
    void resolvesReadyBalanceFromLocalSnapshotOnly() {
        this.ready.set(true);
        this.snapshot.set(new BalanceSnapshot(this.playerId, 1_234_567L, 9L, Instant.EPOCH));
        this.language.set("es");

        assertEquals("1234567", this.resolver.resolve(this.playerId, "points", true));
        assertEquals("1.234.567", this.resolver.resolve(this.playerId, "points_formatted", true));
        assertEquals("1,2M", this.resolver.resolve(this.playerId, "points_compact", true));
        assertEquals("true", this.resolver.resolve(this.playerId, "ready", true));
    }

    @Test
    void representsRealZeroOnlyWhenReadyAndCached() {
        this.ready.set(true);
        this.snapshot.set(new BalanceSnapshot(this.playerId, 0L, 1L, Instant.EPOCH));
        this.language.set("en");

        assertEquals("0", this.resolver.resolve(this.playerId, "points", true));
        assertEquals("0", this.resolver.resolve(this.playerId, "points_formatted", true));
        assertEquals("0", this.resolver.resolve(this.playerId, "points_compact", true));
        assertEquals("true", this.resolver.resolve(this.playerId, "ready", true));
    }

    @Test
    void neverRepresentsLoadingAsZero() {
        this.ready.set(false);
        this.language.set("es");

        assertEquals("", this.resolver.resolve(this.playerId, "points", true));
        assertEquals("Cargando...", this.resolver.resolve(this.playerId, "points_formatted", true));
        assertEquals("Cargando...", this.resolver.resolve(this.playerId, "points_compact", true));
        assertEquals("false", this.resolver.resolve(this.playerId, "ready", true));
    }

    @Test
    void treatsMissingSnapshotAsNotReadyEvenIfLifecycleIsReady() {
        this.ready.set(true);
        this.language.set("en");

        assertEquals("", this.resolver.resolve(this.playerId, "points", true));
        assertEquals("Loading...", this.resolver.resolve(this.playerId, "points_formatted", true));
        assertEquals("false", this.resolver.resolve(this.playerId, "ready", true));
    }

    @Test
    void revalidatesReadinessAfterReadingSnapshot() {
        AtomicInteger calls = new AtomicInteger();
        PlaceholderResolver racingResolver = new PlaceholderResolver(
            ignored -> calls.incrementAndGet() == 1,
            ignored -> Optional.of(new BalanceSnapshot(this.playerId, 500L, 3L, Instant.EPOCH)),
            ignored -> "en",
            this.configuration::get
        );

        assertEquals("Loading...", racingResolver.resolve(this.playerId, "points_formatted", true));
        assertEquals(2, calls.get());
    }

    @Test
    void contextWithoutPlayerUsesDocumentedFallbacks() {
        assertEquals("", this.resolver.resolve(null, "points", true));
        assertEquals("", this.resolver.resolve(null, "points_formatted", true));
        assertEquals("", this.resolver.resolve(null, "points_compact", true));
        assertEquals("false", this.resolver.resolve(null, "ready", true));
    }

    @Test
    void inactiveExpansionDoesNotExposeBalances() {
        this.ready.set(true);
        this.snapshot.set(new BalanceSnapshot(this.playerId, 100L, 1L, Instant.EPOCH));

        assertEquals("", this.resolver.resolve(this.playerId, "points", false));
        assertEquals("", this.resolver.resolve(this.playerId, "points_formatted", false));
        assertEquals("false", this.resolver.resolve(this.playerId, "ready", false));
    }

    @Test
    void unknownPlaceholderReturnsNull() {
        assertNull(this.resolver.resolve(this.playerId, "rank", true));
        assertNull(this.resolver.resolve(this.playerId, "rank", false));
    }

    @Test
    void usesActiveConfigurationOnEveryRequest() {
        this.ready.set(true);
        this.snapshot.set(new BalanceSnapshot(this.playerId, 1_234L, 1L, Instant.EPOCH));
        this.language.set("en");

        assertEquals("1,234", this.resolver.resolve(this.playerId, "POINTS_FORMATTED", true));

        this.configuration.set(snapshot("en", enFormat("_", "Please wait"), esFormat(".", "Cargando...")));

        assertEquals("1_234", this.resolver.resolve(this.playerId, "points_formatted", true));
        this.ready.set(false);
        assertEquals("Please wait", this.resolver.resolve(this.playerId, "points_formatted", true));
    }

    private static ConfigurationSnapshot snapshot(String fallbackLanguage, NumberFormatSettings en, NumberFormatSettings es) {
        return new ConfigurationSnapshot(
            1L,
            Instant.EPOCH,
            config(),
            new LocalizationSettings(fallbackLanguage, "en", 10L),
            new IdentitySettings(
                List.of(IdentitySettings.IdentityPart.PREFIX, IdentitySettings.IdentityPart.NICK, IdentitySettings.IdentityPart.COUNTRY_FLAG),
                " ",
                5_000L,
                300L
            ),
            new MessageCatalogs(Map.of(
                "en", new MessageCatalog("en", en, Map.of(), Map.of()),
                "es", new MessageCatalog("es", es, Map.of(), Map.of())
            )),
            CommandSettings.defaults()
        );
    }

    private static NumberFormatSettings enFormat(String grouping, String loadingText) {
        return numberFormat(grouping, ".", loadingText);
    }

    private static NumberFormatSettings esFormat(String grouping, String loadingText) {
        return numberFormat(grouping, ",", loadingText);
    }

    private static NumberFormatSettings numberFormat(String grouping, String decimal, String loadingText) {
        return new NumberFormatSettings(grouping, decimal, 1, false, Map.of(
            NumberFormatSettings.CompactMagnitude.THOUSAND, "K",
            NumberFormatSettings.CompactMagnitude.MILLION, "M",
            NumberFormatSettings.CompactMagnitude.BILLION, "B",
            NumberFormatSettings.CompactMagnitude.TRILLION, "T",
            NumberFormatSettings.CompactMagnitude.QUADRILLION, "Qa",
            NumberFormatSettings.CompactMagnitude.QUINTILLION, "Qi"
        ), loadingText);
    }

    private static ProgressEngineConfig config() {
        return new ProgressEngineConfig(
            "server-1",
            new ProgressEngineConfig.EconomySettings(Long.MAX_VALUE, AwardRounding.FLOOR),
            new ProgressEngineConfig.DatabaseSettings(
                "127.0.0.1",
                3306,
                "hera_network",
                "progressengine",
                "secret",
                "",
                new ProgressEngineConfig.DatabasePoolSettings(10, 2, 10_000, 5_000, 600_000, 1_800_000, 0)
            ),
            new ProgressEngineConfig.RedisSettings(
                "127.0.0.1",
                6379,
                0,
                "secret",
                "",
                false,
                true,
                3_000,
                3_000,
                10_000,
                10_000,
                100,
                10_000,
                2,
                2,
                new ProgressEngineConfig.RedisNamespace("hera", "production")
            ),
            new ProgressEngineConfig.CacheSettings(10_000, 1_800, true),
            new ProgressEngineConfig.ReconciliationSettings(60, 10, 200),
            new ProgressEngineConfig.IntegrationSettings(true, true),
            new ProgressEngineConfig.RuntimeSettings(10, 10)
        );
    }
}
