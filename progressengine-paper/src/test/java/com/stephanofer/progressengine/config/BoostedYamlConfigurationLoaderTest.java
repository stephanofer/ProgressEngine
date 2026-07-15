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
        assertTrue(Files.exists(this.directory.resolve("config.yml")));
        assertFalse(loaded.snapshot().config().database().toString().contains("password=\""));
        assertTrue(loaded.snapshot().config().database().toString().contains("password=<hidden>"));
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
    void rejectsFutureConfigVersion() throws Exception {
        Files.writeString(this.directory.resolve("config.yml"), defaultConfig().replace("config-version: 1", "config-version: 999"));

        ConfigurationLoadException exception = assertThrows(ConfigurationLoadException.class, () -> loader().load(1L));

        assertTrue(exception.problems().stream().anyMatch(problem -> problem.path().equals("config.yml") || problem.path().equals("config-version")));
    }

    private BoostedYamlConfigurationLoader loader() {
        return new BoostedYamlConfigurationLoader(this.directory, BoostedYamlConfigurationLoaderTest::defaultStream, CLOCK);
    }

    private static InputStream defaultStream() {
        return BoostedYamlConfigurationLoaderTest.class.getResourceAsStream("/config.yml");
    }

    private static String defaultConfig() throws Exception {
        try (InputStream stream = defaultStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
