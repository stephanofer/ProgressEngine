package com.stephanofer.progressengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BoostedYamlConfigurationLoaderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path directory;

    @Test
    void loadsDefaultConfigurationAndPersistsValidatedFile() throws Exception {
        BoostedYamlConfigurationLoader loader = loader();

        LoadedConfiguration loaded = loader.load(1L);
        loader.persist(loaded);

        assertEquals(1L, loaded.snapshot().revision());
        assertEquals(Instant.parse("2026-07-14T00:00:00Z"), loaded.snapshot().loadedAt());
        assertEquals(Long.MAX_VALUE, loaded.snapshot().config().economy().maximumBalance());
        assertEquals("lobby-1", loaded.snapshot().config().serverId());
        assertEquals("en", loaded.snapshot().localization().fallbackLanguage());
        assertEquals("Loading...", loaded.snapshot().messages().require("en").numberFormat().loadingText());
        assertEquals("Cargando...", loaded.snapshot().messages().require("es").numberFormat().loadingText());
        assertEquals(PriceFormat.COMPACT, loaded.snapshot().messages().require("es").currency().priceFormat());
        assertEquals("9,5K Points", com.stephanofer.progressengine.localization.PointsDisplay.display(9_500L, loaded.snapshot().messages().require("es")));
        assertTrue(loaded.snapshot().messages().require("en").messages().containsKey("points-loading"));
        assertTrue(Files.exists(this.directory.resolve("config.yml")));
        assertTrue(Files.exists(this.directory.resolve("identity.yml")));
        assertTrue(Files.exists(this.directory.resolve("commands.yml")));
        assertTrue(Files.exists(this.directory.resolve("dialogs/pay-confirmation.yml")));
        assertTrue(Files.exists(this.directory.resolve("messages/es.yml")));
        assertTrue(Files.exists(this.directory.resolve("messages/en.yml")));
        assertEquals("Confirm payment", loaded.snapshot().payDialog().locales().get("en").title());
        assertEquals(420, loaded.snapshot().payDialog().bodyWidth());
        assertTrue(loaded.serializedDocuments().containsKey("dialogs/pay-confirmation.yml"));
        assertFalse(loaded.snapshot().config().database().toString().contains("password=\""));
        assertTrue(loaded.snapshot().config().database().toString().contains("password=<hidden>"));
    }

    @Test
    void rejectsInvalidPayDialogWidth() throws Exception {
        writeDialog(defaultResource("dialogs/pay-confirmation.yml").replace("body-width: 420", "body-width: 0"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("dialogs/pay-confirmation.yml:behavior.body-width")));
    }

    @Test
    void rejectsMissingPayDialogLocale() throws Exception {
        writeDialog(defaultResource("dialogs/pay-confirmation.yml").replace("  es:\n    title:", "  fr:\n    title:"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("dialogs/pay-confirmation.yml:locales.es")));
    }

    @Test
    void rejectsUnknownPayDialogPlaceholder() throws Exception {
        writeDialog(defaultResource("dialogs/pay-confirmation.yml").replace("Exact amount: <amount_exact>", "Exact amount: <unexpected>"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("dialogs/pay-confirmation.yml:locales.en.body")));
    }

    @Test
    void invalidReloadKeepsPreviousPayDialog() throws Exception {
        BoostedYamlConfigurationLoader loader = loader();
        ConfigurationManager manager = new ConfigurationManager(loader, Runnable::run);

        ConfigurationReloadResult first = manager.reloadAsync().join();
        writeDialog(defaultResource("dialogs/pay-confirmation.yml").replace("body-width: 420", "body-width: 0"));
        ConfigurationReloadResult second = manager.reloadAsync().join();

        assertTrue(first.success());
        assertFalse(second.success());
        assertEquals(first.activeSnapshot().orElseThrow().payDialog(), manager.activeSnapshot().orElseThrow().payDialog());
        assertEquals(1L, manager.activeSnapshot().orElseThrow().revision());
    }

    @Test
    void invalidMessageCatalogKeepsCandidateUnpublished() throws Exception {
        Files.createDirectories(this.directory.resolve("messages"));
        Files.writeString(this.directory.resolve("messages/en.yml"), defaultResource("messages/en.yml").replace(
            "points-loading: \"<yellow>Your points are still loading.</yellow>\"",
            "points-loading: \"<yellow>Your points are <unknown></yellow>.\""
        ));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("messages/en.yml:messages.points-loading")));
    }

    @Test
    void rejectsMiniMessageLoadingTextForPlaceholders() throws Exception {
        Files.createDirectories(this.directory.resolve("messages"));
        Files.writeString(this.directory.resolve("messages/en.yml"), defaultResource("messages/en.yml").replace(
            "loading-text: \"Loading...\"",
            "loading-text: \"<yellow>Loading...</yellow>\""
        ));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("messages/en.yml:number-format")));
    }

    @Test
    void rejectsUnknownCurrencyFormatPlaceholder() throws Exception {
        Files.createDirectories(this.directory.resolve("messages"));
        Files.writeString(this.directory.resolve("messages/en.yml"), defaultResource("messages/en.yml").replace(
            "format: \"%price% %display-name%\"",
            "format: \"%price% %unknown%\""
        ));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("messages/en.yml:currency")));
    }

    @Test
    void missingNonFallbackKeyUsesFallbackCatalog() throws Exception {
        Files.createDirectories(this.directory.resolve("messages"));
        Files.writeString(this.directory.resolve("messages/es.yml"), defaultResource("messages/es.yml").replace(
            "  points-loading: \"<yellow>Tus points todavía están cargando.</yellow>\"\n",
            ""
        ));

        LoadedConfiguration loaded = loader().load(1L);

        assertEquals(
            loaded.snapshot().messages().require("en").messages().get("points-loading"),
            loaded.snapshot().messages().require("es").messages().get("points-loading")
        );
    }

    @Test
    void rejectsUnknownKeys() throws Exception {
        Files.writeString(this.directory.resolve("config.yml"), defaultConfig() + "\nunknown-key: true\n");

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("unknown-key")));
    }

    @Test
    void rejectsDuplicateKeysBeforeValidation() throws Exception {
        Files.writeString(this.directory.resolve("config.yml"), "config-version: 1\nconfig-version: 1\n");

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("config.yml")));
    }

    @Test
    void rejectsCrossFieldViolations() throws Exception {
        String invalid = defaultConfig()
            .replace("minimum-idle: 2", "minimum-idle: 11")
            .replace("validation-timeout-millis: 5000", "validation-timeout-millis: 10000")
            .replace("degraded-interval-seconds: 10", "degraded-interval-seconds: 60");
        Files.writeString(this.directory.resolve("config.yml"), invalid);

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("database.pool.minimum-idle")));
        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("database.pool.validation-timeout-millis")));
        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("reconciliation.degraded-interval-seconds")));
    }

    @Test
    void loadsCommandSettingsAndRejectsInvalidCommandRange() throws Exception {
        BoostedYamlConfigurationLoader loader = loader();

        LoadedConfiguration loaded = loader.load(1L);

        assertEquals("points", loaded.snapshot().commands().registration().root());
        assertEquals(1L, loaded.snapshot().commands().pay().minimum());

        Files.writeString(this.directory.resolve("commands.yml"), defaultResource("commands.yml")
            .replace("maximum: 1000000000", "maximum: 0"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(2L));
        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().contains("commands.yml:pay.maximum")));
    }

    @Test
    void rejectsFutureConfigVersion() throws Exception {
        Files.writeString(this.directory.resolve("config.yml"), defaultConfig().replace("config-version: 2", "config-version: 999"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("config.yml") || problem.path().equals("config-version")));
    }

    private BoostedYamlConfigurationLoader loader() {
        return new BoostedYamlConfigurationLoader(this.directory, BoostedYamlConfigurationLoaderTest::defaultStream, CLOCK);
    }

    private static InputStream defaultStream(String name) {
        return BoostedYamlConfigurationLoaderTest.class.getResourceAsStream("/" + name);
    }

    private static String defaultConfig() throws Exception {
        return defaultResource("config.yml");
    }

    private void writeDialog(String content) throws Exception {
        Files.createDirectories(this.directory.resolve("dialogs"));
        Files.writeString(this.directory.resolve("dialogs/pay-confirmation.yml"), content);
    }

    private static String defaultResource(String name) throws Exception {
        try (InputStream stream = defaultStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
