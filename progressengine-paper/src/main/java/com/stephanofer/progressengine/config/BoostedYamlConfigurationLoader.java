package com.stephanofer.progressengine.config;

import com.stephanofer.progressengine.config.ProgressEngineConfig.CacheSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.DatabasePoolSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.DatabaseSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.EconomySettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.IntegrationSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.ReconciliationSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.RedisNamespace;
import com.stephanofer.progressengine.config.ProgressEngineConfig.RedisSettings;
import com.stephanofer.progressengine.config.ProgressEngineConfig.RuntimeSettings;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class BoostedYamlConfigurationLoader implements ConfigurationLoader {
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final int CONFIG_VERSION = 1;
    private static final Pattern REDIS_COMPONENT = Pattern.compile("[a-zA-Z0-9._:-]+");
    private static final Pattern TABLE_PREFIX = Pattern.compile("[a-zA-Z0-9_]*");
    private static final Set<String> ALLOWED_ROUTES = Set.of(
        "config-version",
        "server-id",
        "economy",
        "economy.maximum-balance",
        "economy.award-rounding",
        "database",
        "database.host",
        "database.port",
        "database.name",
        "database.username",
        "database.password",
        "database.table-prefix",
        "database.pool",
        "database.pool.maximum-size",
        "database.pool.minimum-idle",
        "database.pool.connection-timeout-millis",
        "database.pool.validation-timeout-millis",
        "database.pool.idle-timeout-millis",
        "database.pool.max-lifetime-millis",
        "database.pool.leak-detection-threshold-millis",
        "redis",
        "redis.host",
        "redis.port",
        "redis.database",
        "redis.username",
        "redis.password",
        "redis.ssl",
        "redis.verify-peer",
        "redis.command-timeout-millis",
        "redis.connect-timeout-millis",
        "redis.shutdown-timeout-millis",
        "redis.request-queue-size",
        "redis.reconnect-min-delay-millis",
        "redis.reconnect-max-delay-millis",
        "redis.io-threads",
        "redis.computation-threads",
        "redis.namespace",
        "redis.namespace.key-prefix",
        "redis.namespace.environment",
        "cache",
        "cache.maximum-size",
        "cache.expire-after-access-seconds",
        "cache.record-stats",
        "reconciliation",
        "reconciliation.normal-interval-seconds",
        "reconciliation.degraded-interval-seconds",
        "reconciliation.batch-size",
        "integrations",
        "integrations.network-boosters-enabled",
        "integrations.placeholder-api-enabled",
        "runtime",
        "runtime.shutdown-timeout-seconds"
    );

    private final Path dataDirectory;
    private final Supplier<InputStream> defaultsSupplier;
    private final Clock clock;

    public BoostedYamlConfigurationLoader(Path dataDirectory, Supplier<InputStream> defaultsSupplier, Clock clock) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.defaultsSupplier = Objects.requireNonNull(defaultsSupplier, "defaultsSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LoadedConfiguration load(long revision) throws ConfigurationLoadException {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }

        byte[] defaults = readDefaults();
        byte[] document = readDocument(defaults);
        YamlDocument yaml = loadYaml(document, defaults);
        List<ConfigurationProblem> problems = validateUnknownRoutes(yaml);
        ProgressEngineConfig config = readConfig(yaml, problems);
        if (!problems.isEmpty()) {
            throw new ConfigurationLoadException(problems);
        }
        ConfigurationSnapshot snapshot = new ConfigurationSnapshot(revision, this.clock.instant(), config);
        return new LoadedConfiguration(snapshot, yaml.dump());
    }

    @Override
    public void persist(LoadedConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        Files.createDirectories(this.dataDirectory);
        Path target = this.dataDirectory.resolve(CONFIG_FILE_NAME);
        Path temporary = this.dataDirectory.resolve(CONFIG_FILE_NAME + ".tmp");
        Files.writeString(temporary, configuration.serializedConfig(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] readDefaults() throws ConfigurationLoadException {
        try (InputStream stream = this.defaultsSupplier.get()) {
            if (stream == null) {
                throw new ConfigurationLoadException(List.of(new ConfigurationProblem(CONFIG_FILE_NAME, "default resource is missing")));
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new ConfigurationLoadException(new ConfigurationProblem(CONFIG_FILE_NAME, "could not read default resource"), exception);
        }
    }

    private byte[] readDocument(byte[] defaults) throws ConfigurationLoadException {
        Path config = this.dataDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(config)) {
            return defaults;
        }
        try {
            return Files.readAllBytes(config);
        } catch (IOException exception) {
            throw new ConfigurationLoadException(new ConfigurationProblem(CONFIG_FILE_NAME, "could not read file"), exception);
        }
    }

    private YamlDocument loadYaml(byte[] document, byte[] defaults) throws ConfigurationLoadException {
        GeneralSettings general = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
        LoaderSettings loader = LoaderSettings.builder()
            .setCreateFileIfAbsent(false)
            .setAutoUpdate(false)
            .setAllowDuplicateKeys(false)
            .setMaxCollectionAliases(16)
            .setCodePointLimit(131_072)
            .setErrorLabel("ProgressEngine config.yml")
            .build();
        UpdaterSettings updater = UpdaterSettings.builder()
            .setAutoSave(false)
            .setEnableDowngrading(false)
            .setKeepAll(true)
            .setVersioning(new BasicVersioning("config-version"))
            .build();
        try {
            YamlDocument yaml = YamlDocument.create(
                new ByteArrayInputStream(document),
                new ByteArrayInputStream(defaults),
                general,
                loader,
                DumperSettings.DEFAULT,
                updater
            );
            yaml.update();
            return yaml;
        } catch (Exception exception) {
            throw new ConfigurationLoadException(new ConfigurationProblem(CONFIG_FILE_NAME, "could not parse or update YAML"), exception);
        }
    }

    private List<ConfigurationProblem> validateUnknownRoutes(YamlDocument yaml) {
        List<ConfigurationProblem> problems = new ArrayList<>();
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (!ALLOWED_ROUTES.contains(route)) {
                problems.add(new ConfigurationProblem(route, "unknown configuration key"));
            }
        }
        return problems;
    }

    private ProgressEngineConfig readConfig(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems);
        reader.requireVersion();
        reader.requireSection("economy");
        reader.requireSection("database");
        reader.requireSection("database.pool");
        reader.requireSection("redis");
        reader.requireSection("redis.namespace");
        reader.requireSection("cache");
        reader.requireSection("reconciliation");
        reader.requireSection("integrations");
        reader.requireSection("runtime");

        String serverId = reader.component("server-id", "lobby-1", false);
        long maximumBalance = reader.longRange("economy.maximum-balance", 1L, Long.MAX_VALUE, Long.MAX_VALUE);
        AwardRounding rounding = reader.rounding("economy.award-rounding", AwardRounding.FLOOR);

        String dbHost = reader.text("database.host", "127.0.0.1", false, false);
        int dbPort = reader.intRange("database.port", 1, 65_535, 3306);
        String dbName = reader.text("database.name", "hera_network", false, false);
        String dbUsername = reader.text("database.username", "progressengine", false, false);
        String dbPassword = reader.text("database.password", "", true, true);
        String tablePrefix = reader.tablePrefix("database.table-prefix", "");
        int poolMaximum = reader.intRange("database.pool.maximum-size", 1, 64, 10);
        int poolMinimumIdle = reader.intRange("database.pool.minimum-idle", 0, 64, 2);
        long connectionTimeout = reader.longRange("database.pool.connection-timeout-millis", 250L, 60_000L, 10_000L);
        long validationTimeout = reader.longRange("database.pool.validation-timeout-millis", 250L, 60_000L, 5_000L);
        long idleTimeout = reader.zeroOrRange("database.pool.idle-timeout-millis", 10_000L, Long.MAX_VALUE, 600_000L);
        long maxLifetime = reader.zeroOrRange("database.pool.max-lifetime-millis", 30_000L, Long.MAX_VALUE, 1_800_000L);
        long leakDetection = reader.zeroOrRange("database.pool.leak-detection-threshold-millis", 2_000L, Long.MAX_VALUE, 0L);

        if (poolMinimumIdle > poolMaximum) {
            problems.add(new ConfigurationProblem("database.pool.minimum-idle", "must not exceed database.pool.maximum-size"));
        }
        if (validationTimeout >= connectionTimeout) {
            problems.add(new ConfigurationProblem("database.pool.validation-timeout-millis", "must be lower than database.pool.connection-timeout-millis"));
        }

        String redisHost = reader.text("redis.host", "127.0.0.1", false, false);
        int redisPort = reader.intRange("redis.port", 1, 65_535, 6379);
        int redisDatabase = reader.intRange("redis.database", 0, Integer.MAX_VALUE, 0);
        String redisUsername = reader.text("redis.username", "", true, false);
        String redisPassword = reader.text("redis.password", "", true, true);
        boolean redisSsl = reader.bool("redis.ssl", false);
        boolean redisVerifyPeer = reader.bool("redis.verify-peer", true);
        long commandTimeout = reader.longRange("redis.command-timeout-millis", 1L, 60_000L, 3_000L);
        long connectTimeout = reader.longRange("redis.connect-timeout-millis", 1L, 60_000L, 3_000L);
        long shutdownTimeout = reader.longRange("redis.shutdown-timeout-millis", 1L, 60_000L, 10_000L);
        int requestQueueSize = reader.intRange("redis.request-queue-size", 1, 100_000, 10_000);
        long reconnectMinDelay = reader.longRange("redis.reconnect-min-delay-millis", 1L, 60_000L, 100L);
        long reconnectMaxDelay = reader.longRange("redis.reconnect-max-delay-millis", 1L, 60_000L, 10_000L);
        int ioThreads = reader.intRange("redis.io-threads", 1, 16, 2);
        int computationThreads = reader.intRange("redis.computation-threads", 1, 16, 2);
        String keyPrefix = reader.component("redis.namespace.key-prefix", "hera", true);
        String environment = reader.component("redis.namespace.environment", "production", true);
        if (reconnectMinDelay > reconnectMaxDelay) {
            problems.add(new ConfigurationProblem("redis.reconnect-max-delay-millis", "must be greater than or equal to redis.reconnect-min-delay-millis"));
        }

        long cacheMaximumSize = reader.longRange("cache.maximum-size", 1L, 10_000_000L, 10_000L);
        long expireAfterAccess = reader.longRange("cache.expire-after-access-seconds", 1L, 86_400L, 1_800L);
        boolean recordStats = reader.bool("cache.record-stats", true);

        long normalInterval = reader.longRange("reconciliation.normal-interval-seconds", 10L, 600L, 60L);
        long degradedInterval = reader.longRange("reconciliation.degraded-interval-seconds", 1L, 60L, 10L);
        int batchSize = reader.intRange("reconciliation.batch-size", 1, 1_000, 200);
        if (degradedInterval >= normalInterval) {
            problems.add(new ConfigurationProblem("reconciliation.degraded-interval-seconds", "must be lower than reconciliation.normal-interval-seconds"));
        }

        boolean networkBoosters = reader.bool("integrations.network-boosters-enabled", true);
        boolean placeholderApi = reader.bool("integrations.placeholder-api-enabled", true);
        long runtimeShutdown = reader.longRange("runtime.shutdown-timeout-seconds", 1L, 60L, 10L);

        return new ProgressEngineConfig(
            serverId,
            new EconomySettings(maximumBalance, rounding),
            new DatabaseSettings(
                dbHost,
                dbPort,
                dbName,
                dbUsername,
                dbPassword,
                tablePrefix,
                new DatabasePoolSettings(
                    poolMaximum,
                    poolMinimumIdle,
                    connectionTimeout,
                    validationTimeout,
                    idleTimeout,
                    maxLifetime,
                    leakDetection
                )
            ),
            new RedisSettings(
                redisHost,
                redisPort,
                redisDatabase,
                redisUsername,
                redisPassword,
                redisSsl,
                redisVerifyPeer,
                commandTimeout,
                connectTimeout,
                shutdownTimeout,
                requestQueueSize,
                reconnectMinDelay,
                reconnectMaxDelay,
                ioThreads,
                computationThreads,
                new RedisNamespace(keyPrefix, environment)
            ),
            new CacheSettings(cacheMaximumSize, expireAfterAccess, recordStats),
            new ReconciliationSettings(normalInterval, degradedInterval, batchSize),
            new IntegrationSettings(networkBoosters, placeholderApi),
            new RuntimeSettings(runtimeShutdown)
        );
    }

    private static final class Reader {
        private final YamlDocument yaml;
        private final List<ConfigurationProblem> problems;

        private Reader(YamlDocument yaml, List<ConfigurationProblem> problems) {
            this.yaml = yaml;
            this.problems = problems;
        }

        private void requireVersion() {
            int version = intRange("config-version", CONFIG_VERSION, CONFIG_VERSION, CONFIG_VERSION);
            if (version != CONFIG_VERSION) {
                this.problems.add(new ConfigurationProblem("config-version", "unsupported version"));
            }
        }

        private void requireSection(String path) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof Section)) {
                this.problems.add(new ConfigurationProblem(path, "must be a section"));
            }
        }

        private String text(String path, String fallback, boolean allowEmpty, boolean allowWhitespace) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof String text)) {
                this.problems.add(new ConfigurationProblem(path, "must be a string"));
                return fallback;
            }
            if (!allowWhitespace && !text.equals(text.trim())) {
                this.problems.add(new ConfigurationProblem(path, "must not contain leading or trailing whitespace"));
            }
            if (!allowEmpty && text.isBlank()) {
                this.problems.add(new ConfigurationProblem(path, "must not be empty"));
                return fallback;
            }
            return text;
        }

        private String component(String path, String fallback, boolean allowDefaultUnknown) {
            String value = text(path, fallback, false, false);
            if (!REDIS_COMPONENT.matcher(value).matches() || value.length() > 256) {
                this.problems.add(new ConfigurationProblem(path, "must match [a-zA-Z0-9._:-]+ and be at most 256 characters"));
                return fallback;
            }
            if (!allowDefaultUnknown && value.equals("unknown")) {
                this.problems.add(new ConfigurationProblem(path, "must not use the reserved value \"unknown\""));
                return fallback;
            }
            return value;
        }

        private String tablePrefix(String path, String fallback) {
            String value = text(path, fallback, true, false);
            if (!TABLE_PREFIX.matcher(value).matches()) {
                this.problems.add(new ConfigurationProblem(path, "must contain only letters, numbers or underscores"));
                return fallback;
            }
            return value;
        }

        private AwardRounding rounding(String path, AwardRounding fallback) {
            String value = text(path, fallback.name(), false, false);
            try {
                return AwardRounding.valueOf(value);
            } catch (IllegalArgumentException exception) {
                this.problems.add(new ConfigurationProblem(path, "must be FLOOR"));
                return fallback;
            }
        }

        private boolean bool(String path, boolean fallback) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof Boolean bool)) {
                this.problems.add(new ConfigurationProblem(path, "must be a boolean"));
                return fallback;
            }
            return bool;
        }

        private int intRange(String path, int min, int max, int fallback) {
            BigInteger value = integer(path);
            if (value == null) {
                return fallback;
            }
            if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
                this.problems.add(new ConfigurationProblem(path, "must be between " + min + " and " + max));
                return fallback;
            }
            return value.intValue();
        }

        private long longRange(String path, long min, long max, long fallback) {
            BigInteger value = integer(path);
            if (value == null) {
                return fallback;
            }
            if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
                this.problems.add(new ConfigurationProblem(path, "must be between " + min + " and " + max));
                return fallback;
            }
            return value.longValue();
        }

        private long zeroOrRange(String path, long minNonZero, long max, long fallback) {
            BigInteger value = integer(path);
            if (value == null) {
                return fallback;
            }
            if (BigInteger.ZERO.equals(value)) {
                return 0L;
            }
            if (value.compareTo(BigInteger.valueOf(minNonZero)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
                this.problems.add(new ConfigurationProblem(path, "must be 0 or between " + minNonZero + " and " + max));
                return fallback;
            }
            return value.longValue();
        }

        private BigInteger integer(String path) {
            Object value = this.yaml.get(path, null);
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return BigInteger.valueOf(((Number) value).longValue());
            }
            if (value instanceof BigInteger bigInteger) {
                return bigInteger;
            }
            this.problems.add(new ConfigurationProblem(path, "must be an integer"));
            return null;
        }
    }
}
