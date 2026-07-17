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
import java.io.ByteArrayOutputStream;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class BoostedYamlConfigurationLoader implements ConfigurationLoader {
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String IDENTITY_FILE_NAME = "identity.yml";
    private static final String COMMANDS_FILE_NAME = "commands.yml";
    private static final String PAY_CONFIRMATION_DIALOG_FILE_NAME = "dialogs/pay-confirmation.yml";
    private static final String ES_MESSAGES_FILE_NAME = "messages/es.yml";
    private static final String EN_MESSAGES_FILE_NAME = "messages/en.yml";
    private static final int CONFIG_VERSION = 2;
    private static final int IDENTITY_VERSION = 1;
    private static final int COMMANDS_VERSION = 2;
    private static final int PAY_CONFIRMATION_DIALOG_VERSION = 1;
    private static final int MESSAGES_VERSION = 2;
    private static final Pattern REDIS_COMPONENT = Pattern.compile("[a-zA-Z0-9._:-]+");
    private static final Pattern TABLE_PREFIX = Pattern.compile("[a-zA-Z0-9_]*");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<\\/?([a-zA-Z0-9_:-]+)(?:[\\s:>/]|>)");
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final Set<String> KNOWN_MINI_MESSAGE_TAGS = Set.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
        "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "color", "colour", "decorations",
        "bold", "b", "italic", "i", "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset",
        "newline", "br", "rainbow", "gradient", "transition", "hover", "click", "insertion", "keybind",
        "translatable", "lang", "selector", "score", "nbt", "font", "shadow"
    );
    private static final Set<String> MESSAGE_KEYS = Set.of(
        "points-loading",
        "command-no-permission",
        "command-player-only",
        "command-console-only",
        "command-player-or-console-only",
        "command-disabled",
        "invalid-amount",
        "invalid-page",
        "invalid-reason",
        "unknown-target",
        "target-self",
        "balance-self",
        "balance-other",
        "pay-range",
        "pay-confirm-required",
        "pay-retry-available",
        "pay-token-invalid",
        "pay-token-expired",
        "pay-token-stale",
        "pay-success-sender",
        "pay-success-receiver",
        "pay-insufficient-funds",
        "pay-balance-limit",
        "pay-cooldown",
        "history-empty",
        "history-header",
        "history-entry",
        "history-session-expired",
        "history-next-page",
        "history-previous-page",
        "admin-add-success",
        "admin-remove-success",
        "admin-set-success",
        "admin-reset-success",
        "admin-retry-available",
        "admin-reload-success",
        "admin-reload-restart-required",
        "admin-reload-failure",
        "admin-status-header",
        "admin-status-line",
        "infrastructure-unavailable"
    );
    private static final Set<String> FEEDBACK_KEYS = Set.of("award-received", "transfer-received");
    private static final Set<String> PAY_DIALOG_SAFE_PLACEHOLDERS = Set.of("amount_exact", "balance_after", "expires_in");
    private static final Set<String> PAY_DIALOG_COMPONENT_PLACEHOLDERS = Set.of("receiver", "amount");
    private static final Map<String, Set<String>> PLACEHOLDERS = Map.ofEntries(
        Map.entry("points-loading", Set.of()),
        Map.entry("command-no-permission", Set.of()),
        Map.entry("command-player-only", Set.of()),
        Map.entry("command-console-only", Set.of()),
        Map.entry("command-player-or-console-only", Set.of()),
        Map.entry("command-disabled", Set.of("command")),
        Map.entry("invalid-amount", Set.of("input")),
        Map.entry("invalid-page", Set.of("input")),
        Map.entry("invalid-reason", Set.of("input")),
        Map.entry("unknown-target", Set.of("target")),
        Map.entry("target-self", Set.of()),
        Map.entry("balance-self", Set.of("balance", "balance_raw", "balance_compact")),
        Map.entry("balance-other", Set.of("target", "balance", "balance_raw", "balance_compact")),
        Map.entry("pay-range", Set.of("minimum", "maximum")),
        Map.entry("pay-confirm-required", Set.of("amount", "target", "token")),
        Map.entry("pay-retry-available", Set.of("token")),
        Map.entry("pay-token-invalid", Set.of("token")),
        Map.entry("pay-token-expired", Set.of("token")),
        Map.entry("pay-token-stale", Set.of()),
        Map.entry("pay-success-sender", Set.of("amount", "target", "balance")),
        Map.entry("pay-success-receiver", Set.of("amount", "sender", "balance")),
        Map.entry("pay-insufficient-funds", Set.of("amount", "balance")),
        Map.entry("pay-balance-limit", Set.of("target", "amount")),
        Map.entry("pay-cooldown", Set.of("seconds")),
        Map.entry("history-empty", Set.of()),
        Map.entry("history-header", Set.of("page")),
        Map.entry("history-entry", Set.of("operation", "actor", "target", "amount", "balance", "reason", "date", "type")),
        Map.entry("history-session-expired", Set.of()),
        Map.entry("history-next-page", Set.of("page")),
        Map.entry("history-previous-page", Set.of("page")),
        Map.entry("admin-add-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-remove-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-set-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-reset-success", Set.of("target", "balance")),
        Map.entry("admin-retry-available", Set.of("token")),
        Map.entry("admin-reload-success", Set.of("revision", "applied")),
        Map.entry("admin-reload-restart-required", Set.of("changes")),
        Map.entry("admin-reload-failure", Set.of("problems")),
        Map.entry("admin-status-header", Set.of("state")),
        Map.entry("admin-status-line", Set.of("label", "value")),
        Map.entry("infrastructure-unavailable", Set.of()),
        Map.entry("award-received", Set.of("amount", "amount_raw", "amount_compact", "balance", "balance_raw", "balance_compact")),
        Map.entry("transfer-received", Set.of("amount", "amount_raw", "amount_compact", "sender", "balance", "balance_raw", "balance_compact"))
    );
    private static final Set<String> ALLOWED_ROUTES = Set.of(
        "config-version",
        "server-id",
        "economy",
        "economy.maximum-balance",
        "economy.award-rounding",
        "economy.amount-colors",
        "economy.amount-colors.enabled",
        "economy.amount-colors.tiers",
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
        "localization",
        "localization.fallback-language",
        "localization.console-language",
        "feedback",
        "feedback.award-coalescing-window-ticks",
        "runtime",
        "runtime.shutdown-timeout-seconds",
        "runtime.database-health-interval-seconds"
    );

    private final Path dataDirectory;
    private final Function<String, InputStream> defaultsSupplier;
    private final Clock clock;

    public BoostedYamlConfigurationLoader(Path dataDirectory, Function<String, InputStream> defaultsSupplier, Clock clock) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.defaultsSupplier = Objects.requireNonNull(defaultsSupplier, "defaultsSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BoostedYamlConfigurationLoader(Path dataDirectory, Supplier<InputStream> defaultsSupplier, Clock clock) {
        this(dataDirectory, name -> CONFIG_FILE_NAME.equals(name) ? defaultsSupplier.get() : null, clock);
    }

    @Override
    public LoadedConfiguration load(long revision) throws ConfigurationLoadException {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }

        byte[] defaults = readDefaults(CONFIG_FILE_NAME);
        byte[] document = readDocument(CONFIG_FILE_NAME, defaults);
        YamlDocument yaml = loadYaml(CONFIG_FILE_NAME, document, defaults, CONFIG_VERSION);
        List<ConfigurationProblem> problems = validateUnknownRoutes(yaml);
        ProgressEngineConfig config = readConfig(yaml, problems);
        LocalizationSettings localization = readLocalization(yaml, problems);
        LoadedIdentity loadedIdentity = loadIdentity(problems);
        LoadedCommands loadedCommands = loadCommands(config, problems);
        LoadedPayDialog loadedPayDialog = loadPayDialog(problems);
        LoadedMessages loadedMessages = loadMessages(localization, problems);
        if (!problems.isEmpty()) {
            throw new ConfigurationLoadException(problems);
        }
        ConfigurationSnapshot snapshot = new ConfigurationSnapshot(
            revision,
            this.clock.instant(),
            config,
            localization,
            loadedIdentity.settings(),
            loadedMessages.catalogs(),
            loadedCommands.settings(),
            loadedPayDialog.settings()
        );
        Map<String, String> serialized = new LinkedHashMap<>();
        serialized.put(CONFIG_FILE_NAME, yaml.dump());
        serialized.put(IDENTITY_FILE_NAME, loadedIdentity.serialized());
        serialized.put(COMMANDS_FILE_NAME, loadedCommands.serialized());
        serialized.put(PAY_CONFIRMATION_DIALOG_FILE_NAME, loadedPayDialog.serialized());
        serialized.put(ES_MESSAGES_FILE_NAME, loadedMessages.serializedEs());
        serialized.put(EN_MESSAGES_FILE_NAME, loadedMessages.serializedEn());
        return new LoadedConfiguration(snapshot, serialized);
    }

    @Override
    public void persist(LoadedConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        for (Map.Entry<String, String> document : configuration.serializedDocuments().entrySet()) {
            Path target = this.dataDirectory.resolve(document.getKey());
            Path parent = target.getParent() == null ? this.dataDirectory : target.getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, document.getValue(), StandardCharsets.UTF_8);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    throw new IOException("could not persist " + document.getKey() + " to " + target + ": " + exception.getMessage(), exception);
                }
            } catch (IOException exception) {
                throw new IOException("could not persist " + document.getKey() + " to " + target + ": " + exception.getMessage(), exception);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private byte[] readDefaults(String fileName) throws ConfigurationLoadException {
        try (InputStream stream = this.defaultsSupplier.apply(fileName)) {
            if (stream == null) {
                throw new ConfigurationLoadException(List.of(new ConfigurationProblem(fileName, "default resource is missing")));
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new ConfigurationLoadException(new ConfigurationProblem(fileName, "could not read default resource"), exception);
        }
    }

    private byte[] readDocument(String fileName, byte[] defaults) throws ConfigurationLoadException {
        Path file = this.dataDirectory.resolve(fileName);
        if (!Files.exists(file)) {
            return defaults;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new ConfigurationLoadException(new ConfigurationProblem(fileName, "could not read file"), exception);
        }
    }

    private YamlDocument loadYaml(String fileName, byte[] document, byte[] defaults, int version) throws ConfigurationLoadException {
        GeneralSettings general = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
        LoaderSettings loader = LoaderSettings.builder()
            .setCreateFileIfAbsent(false)
            .setAutoUpdate(false)
            .setAllowDuplicateKeys(false)
            .setMaxCollectionAliases(16)
            .setCodePointLimit(131_072)
            .setErrorLabel("ProgressEngine " + fileName)
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
            throw new ConfigurationLoadException(new ConfigurationProblem(fileName, "could not parse or update YAML"), exception);
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
        reader.requireSection("economy.amount-colors");
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
        ProgressEngineConfig.AmountColors amountColors = readAmountColors(yaml, reader, problems, maximumBalance);

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
        long databaseHealthInterval = reader.longRange("runtime.database-health-interval-seconds", 1L, 300L, 10L);

        return new ProgressEngineConfig(
            serverId,
            new EconomySettings(maximumBalance, rounding, amountColors),
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
            new RuntimeSettings(runtimeShutdown, databaseHealthInterval)
        );
    }

    private ProgressEngineConfig.AmountColors readAmountColors(YamlDocument yaml, Reader reader, List<ConfigurationProblem> problems, long maximumBalance) {
        boolean enabled = reader.bool("economy.amount-colors.enabled", true);
        Object raw = yaml.get("economy.amount-colors.tiers", null);
        if (!(raw instanceof List<?> list)) {
            problems.add(new ConfigurationProblem("economy.amount-colors.tiers", "must be a list"));
            return ProgressEngineConfig.AmountColors.defaults();
        }
        List<ProgressEngineConfig.AmountColorTier> tiers = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            if (!(entry instanceof Map<?, ?> values) || !(values.get("minimum") instanceof Number number) || !(values.get("color") instanceof String color)) {
                problems.add(new ConfigurationProblem("economy.amount-colors.tiers[" + index + ']', "must contain integer minimum and hex color"));
                continue;
            }
            long minimum = number.longValue();
            if (number.doubleValue() != minimum || minimum > maximumBalance) {
                problems.add(new ConfigurationProblem("economy.amount-colors.tiers[" + index + "].minimum", "must be an integer inside the economy range"));
                continue;
            }
            try {
                tiers.add(new ProgressEngineConfig.AmountColorTier(minimum, color));
            } catch (IllegalArgumentException exception) {
                problems.add(new ConfigurationProblem("economy.amount-colors.tiers[" + index + ']', exception.getMessage()));
            }
        }
        try {
            return new ProgressEngineConfig.AmountColors(enabled, tiers);
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem("economy.amount-colors", exception.getMessage()));
            return ProgressEngineConfig.AmountColors.defaults();
        }
    }

    private LocalizationSettings readLocalization(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems);
        reader.requireSection("localization");
        reader.requireSection("feedback");
        String fallback = reader.language("localization.fallback-language", "en");
        String console = reader.language("localization.console-language", "es");
        long coalescing = reader.longRange("feedback.award-coalescing-window-ticks", 0L, 200L, 10L);
        try {
            return new LocalizationSettings(fallback, console, coalescing);
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem("localization", exception.getMessage()));
            return new LocalizationSettings("en", "es", 10L);
        }
    }

    private LoadedIdentity loadIdentity(List<ConfigurationProblem> problems) throws ConfigurationLoadException {
        byte[] defaults = readDefaults(IDENTITY_FILE_NAME);
        byte[] document = readDocument(IDENTITY_FILE_NAME, defaults);
        YamlDocument yaml = loadYaml(IDENTITY_FILE_NAME, document, defaults, IDENTITY_VERSION);
        validateIdentityRoutes(yaml, problems);
        IdentitySettings settings = readIdentity(yaml, problems);
        return new LoadedIdentity(settings, yaml.dump());
    }

    private void validateIdentityRoutes(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Set<String> allowed = Set.of(
            "config-version",
            "player-identity",
            "player-identity.parts",
            "player-identity.separator",
            "player-identity.offline-cache",
            "player-identity.offline-cache.maximum-size",
            "player-identity.offline-cache.expire-after-write-seconds"
        );
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (!allowed.contains(route)) {
                problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ':' + route, "unknown configuration key"));
            }
        }
    }

    private IdentitySettings readIdentity(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems);
        int version = reader.intRange("config-version", IDENTITY_VERSION, IDENTITY_VERSION, IDENTITY_VERSION);
        if (version != IDENTITY_VERSION) {
            problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ":config-version", "unsupported version"));
        }
        reader.requireSection("player-identity");
        reader.requireSection("player-identity.offline-cache");

        List<IdentitySettings.IdentityPart> parts = new ArrayList<>();
        Object rawParts = yaml.get("player-identity.parts", null);
        if (rawParts instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof String text)) {
                    problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ":player-identity.parts", "all parts must be strings"));
                    continue;
                }
                try {
                    parts.add(IdentitySettings.IdentityPart.parse(text));
                } catch (IllegalArgumentException exception) {
                    problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ":player-identity.parts", exception.getMessage()));
                }
            }
        } else {
            problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ":player-identity.parts", "must be a list"));
            parts = List.of(IdentitySettings.IdentityPart.PREFIX, IdentitySettings.IdentityPart.NICK, IdentitySettings.IdentityPart.COUNTRY_FLAG);
        }

        String separator = reader.text("player-identity.separator", " ", true, true);
        validateTemplate(IDENTITY_FILE_NAME + ":player-identity.separator", separator, Set.of(), problems);
        long maximumSize = reader.longRange("player-identity.offline-cache.maximum-size", 1L, 1_000_000L, 5_000L);
        long expireAfterWrite = reader.longRange("player-identity.offline-cache.expire-after-write-seconds", 1L, 86_400L, 300L);
        try {
            return new IdentitySettings(parts, separator, maximumSize, expireAfterWrite);
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem(IDENTITY_FILE_NAME + ":player-identity", exception.getMessage()));
            return new IdentitySettings(
                List.of(IdentitySettings.IdentityPart.PREFIX, IdentitySettings.IdentityPart.NICK, IdentitySettings.IdentityPart.COUNTRY_FLAG),
                " ",
                5_000L,
                300L
            );
        }
    }

    private LoadedMessages loadMessages(LocalizationSettings localization, List<ConfigurationProblem> problems) throws ConfigurationLoadException {
        LoadedCatalog es = loadCatalog("es", ES_MESSAGES_FILE_NAME, problems);
        LoadedCatalog en = loadCatalog("en", EN_MESSAGES_FILE_NAME, problems);
        Map<String, MessageCatalog> catalogs = new HashMap<>();
        catalogs.put("es", es.catalog());
        catalogs.put("en", en.catalog());

        String fallbackLanguage = localization.fallbackLanguage();
        MessageCatalog fallback = catalogs.get(fallbackLanguage);
        if (fallback == null) {
            problems.add(new ConfigurationProblem("localization.fallback-language", "fallback catalog is not loaded"));
        } else {
            for (String key : MESSAGE_KEYS) {
                if (!fallback.messages().containsKey(key)) {
                    problems.add(new ConfigurationProblem("messages/" + fallbackLanguage + ".yml:messages." + key, "fallback catalog is missing required message"));
                }
            }
            for (String key : FEEDBACK_KEYS) {
                if (!fallback.feedback().containsKey(key)) {
                    problems.add(new ConfigurationProblem("messages/" + fallbackLanguage + ".yml:feedback." + key, "fallback catalog is missing required feedback"));
                }
            }
        }

        if (fallback != null) {
            catalogs.replaceAll((language, catalog) -> applyFallback(catalog, fallback));
        }
        return new LoadedMessages(new MessageCatalogs(catalogs), es.serialized(), en.serialized());
    }

    private LoadedCommands loadCommands(ProgressEngineConfig config, List<ConfigurationProblem> problems) throws ConfigurationLoadException {
        byte[] defaults = readDefaults(COMMANDS_FILE_NAME);
        byte[] document = readDocument(COMMANDS_FILE_NAME, defaults);
        YamlDocument yaml = loadYaml(COMMANDS_FILE_NAME, document, defaults, COMMANDS_VERSION);
        validateCommandRoutes(yaml, problems);
        CommandSettings settings = readCommands(config, yaml, problems);
        return new LoadedCommands(settings, yaml.dump());
    }

    private LoadedPayDialog loadPayDialog(List<ConfigurationProblem> problems) throws ConfigurationLoadException {
        byte[] defaults = readDefaults(PAY_CONFIRMATION_DIALOG_FILE_NAME);
        byte[] document = readDocument(PAY_CONFIRMATION_DIALOG_FILE_NAME, defaults);
        YamlDocument yaml = loadYaml(PAY_CONFIRMATION_DIALOG_FILE_NAME, document, defaults, PAY_CONFIRMATION_DIALOG_VERSION);
        validatePayDialogRoutes(yaml, problems);
        PayConfirmationDialogSettings settings = readPayDialog(yaml, problems);
        return new LoadedPayDialog(settings, yaml.dump());
    }

    private void validatePayDialogRoutes(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Set<String> exact = Set.of(
            "config-version",
            "behavior",
            "behavior.can-close-with-escape",
            "behavior.pause",
            "behavior.body-width",
            "behavior.confirm-button-width",
            "behavior.cancel-button-width",
            "locales"
        );
        Set<String> localeRoutes = Set.of("title", "external-title", "body", "confirm", "confirm.label", "confirm.tooltip", "cancel", "cancel.label", "cancel.tooltip");
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (exact.contains(route)) {
                continue;
            }
            if (route.startsWith("locales.")) {
                String remaining = route.substring("locales.".length());
                int separator = remaining.indexOf('.');
                String language = separator < 0 ? remaining : remaining.substring(0, separator);
                if (!language.equals("en") && !language.equals("es")) {
                    problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + route, "unknown dialog locale"));
                    continue;
                }
                if (separator < 0 || localeRoutes.contains(remaining.substring(separator + 1))) {
                    continue;
                }
            }
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + route, "unknown dialog configuration key"));
        }
    }

    private PayConfirmationDialogSettings readPayDialog(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems, PAY_CONFIRMATION_DIALOG_FILE_NAME + ':');
        int version = reader.intRange("config-version", PAY_CONFIRMATION_DIALOG_VERSION, PAY_CONFIRMATION_DIALOG_VERSION, PAY_CONFIRMATION_DIALOG_VERSION);
        if (version != PAY_CONFIRMATION_DIALOG_VERSION) {
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ":config-version", "unsupported version"));
        }
        reader.requireSection("behavior");
        reader.requireSection("locales");
        reader.requireSection("locales.en");
        reader.requireSection("locales.es");
        reader.requireSection("locales.en.confirm");
        reader.requireSection("locales.en.cancel");
        reader.requireSection("locales.es.confirm");
        reader.requireSection("locales.es.cancel");

        boolean canCloseWithEscape = reader.bool("behavior.can-close-with-escape", true);
        boolean pause = reader.bool("behavior.pause", false);
        int bodyWidth = reader.intRange("behavior.body-width", 1, 1024, 420);
        int confirmButtonWidth = reader.intRange("behavior.confirm-button-width", 1, 1024, 150);
        int cancelButtonWidth = reader.intRange("behavior.cancel-button-width", 1, 1024, 150);
        Map<String, PayConfirmationDialogSettings.Locale> locales = new LinkedHashMap<>();
        locales.put("en", readPayDialogLocale(yaml, reader, "en", problems));
        locales.put("es", readPayDialogLocale(yaml, reader, "es", problems));
        try {
            return new PayConfirmationDialogSettings(canCloseWithEscape, pause, bodyWidth, confirmButtonWidth, cancelButtonWidth, locales);
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME, exception.getMessage()));
            return new PayConfirmationDialogSettings(true, false, 420, 150, 150, Map.of(
                "en", new PayConfirmationDialogSettings.Locale("Confirm payment", "Confirm Points payment", List.of("<receiver>", "<amount>"), "Confirm payment", "Send exactly <amount_exact>", "Cancel", "No transfer will be made."),
                "es", new PayConfirmationDialogSettings.Locale("Confirmar pago", "Confirmar pago de Points", List.of("<receiver>", "<amount>"), "Confirmar pago", "Enviar exactamente <amount_exact>", "Cancelar", "No se realizará ninguna transferencia.")
            ));
        }
    }

    private PayConfirmationDialogSettings.Locale readPayDialogLocale(YamlDocument yaml, Reader reader, String language, List<ConfigurationProblem> problems) {
        String base = "locales." + language;
        String title = reader.text(base + ".title", language.equals("en") ? "Confirm payment" : "Confirmar pago", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".title", title, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        String externalTitle = reader.text(base + ".external-title", language.equals("en") ? "Confirm Points payment" : "Confirmar pago de Points", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".external-title", externalTitle, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        List<String> body = readPayDialogBody(yaml, base + ".body", problems);
        String confirmLabel = reader.text(base + ".confirm.label", language.equals("en") ? "Confirm payment" : "Confirmar pago", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".confirm.label", confirmLabel, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        String confirmTooltip = reader.text(base + ".confirm.tooltip", language.equals("en") ? "Send exactly <amount_exact>" : "Enviar exactamente <amount_exact>", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".confirm.tooltip", confirmTooltip, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        String cancelLabel = reader.text(base + ".cancel.label", language.equals("en") ? "Cancel" : "Cancelar", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".cancel.label", cancelLabel, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        String cancelTooltip = reader.text(base + ".cancel.tooltip", language.equals("en") ? "No transfer will be made." : "No se realizará ninguna transferencia.", false, false);
        validatePayDialogText(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + base + ".cancel.tooltip", cancelTooltip, PAY_DIALOG_SAFE_PLACEHOLDERS, problems);
        return new PayConfirmationDialogSettings.Locale(title, externalTitle, body, confirmLabel, confirmTooltip, cancelLabel, cancelTooltip);
    }

    private List<String> readPayDialogBody(YamlDocument yaml, String path, List<ConfigurationProblem> problems) {
        Object raw = yaml.get(path, null);
        if (!(raw instanceof List<?> list)) {
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + path, "must be a list"));
            return List.of("<receiver>", "<amount>");
        }
        if (list.isEmpty()) {
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + path, "must not be empty"));
            return List.of("<receiver>", "<amount>");
        }
        List<String> body = new ArrayList<>();
        boolean hasContent = false;
        for (int index = 0; index < list.size(); index++) {
            Object value = list.get(index);
            String itemPath = PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + path + '[' + index + ']';
            if (!(value instanceof String line)) {
                problems.add(new ConfigurationProblem(itemPath, "must be a string"));
                continue;
            }
            if (line.length() > 512) {
                problems.add(new ConfigurationProblem(itemPath, "must be at most 512 characters"));
                continue;
            }
            if (!line.isBlank()) {
                hasContent = true;
            }
            Set<String> placeholders = PAY_DIALOG_SAFE_PLACEHOLDERS;
            if (line.equals("<receiver>") || line.equals("<amount>")) {
                placeholders = PAY_DIALOG_COMPONENT_PLACEHOLDERS;
            }
            validatePayDialogText(itemPath, line, placeholders, problems);
            body.add(line);
        }
        if (!hasContent) {
            problems.add(new ConfigurationProblem(PAY_CONFIRMATION_DIALOG_FILE_NAME + ':' + path, "must contain visible text"));
        }
        return body.isEmpty() ? List.of("<receiver>", "<amount>") : body;
    }

    private void validatePayDialogText(String path, String text, Set<String> placeholders, List<ConfigurationProblem> problems) {
        java.util.regex.Matcher matcher = MINI_MESSAGE_TAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!placeholders.contains(tag)) {
                problems.add(new ConfigurationProblem(path, "unknown dialog placeholder or MiniMessage tag: " + tag));
            }
        }
    }

    private void validateCommandRoutes(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Set<String> allowedTop = Set.of("config-version", "registration", "availability", "permissions", "amount-input", "pay", "history", "suggestions", "reasons");
        Set<String> allowedStatic = Set.of(
             "registration.root", "registration.aliases",
             "amount-input.multipliers",
            "pay.minimum", "pay.maximum", "pay.cooldown-seconds", "pay.confirmation", "pay.confirmation.enabled",
            "pay.confirmation.threshold", "pay.confirmation.expiry-seconds", "pay.retry-retention-seconds",
            "history.page-size", "history.session-expiry-seconds", "history.time-zone",
            "suggestions.maximum-size", "suggestions.refresh-seconds",
            "reasons.player-transfer", "reasons.admin-add", "reasons.admin-remove", "reasons.admin-set", "reasons.admin-reset"
        );
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (allowedTop.contains(route) || allowedStatic.contains(route)) continue;
            if (route.startsWith("availability.")) {
                try {
                    CommandSettings.CommandFeature.fromConfigKey(route.substring("availability.".length()));
                    continue;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (route.startsWith("permissions.")) {
                try {
                    CommandSettings.CommandPermission.fromConfigKey(route.substring("permissions.".length()));
                    continue;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (route.startsWith("amount-input.multipliers.")) {
                continue;
            }
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ':' + route, "unknown configuration key"));
        }
    }

    private CommandSettings readCommands(ProgressEngineConfig config, YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems, COMMANDS_FILE_NAME + ':');
        int version = reader.intRange("config-version", COMMANDS_VERSION, COMMANDS_VERSION, COMMANDS_VERSION);
        if (version != COMMANDS_VERSION) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":config-version", "unsupported version"));
        }
        reader.requireSection("registration");
        reader.requireSection("availability");
        reader.requireSection("permissions");
        reader.requireSection("amount-input");
        reader.requireSection("pay");
        reader.requireSection("pay.confirmation");
        reader.requireSection("history");
        reader.requireSection("suggestions");
        reader.requireSection("reasons");

        CommandSettings defaults = CommandSettings.defaults();
        String root = reader.commandLabel("registration.root", defaults.registration().root());
        List<String> aliases = readCommandAliases(yaml, problems);

        java.util.EnumMap<CommandSettings.CommandFeature, Boolean> availability = new java.util.EnumMap<>(CommandSettings.CommandFeature.class);
        for (CommandSettings.CommandFeature feature : CommandSettings.CommandFeature.values()) {
            availability.put(feature, reader.bool("availability." + feature.configKey(), true));
        }

        java.util.EnumMap<CommandSettings.CommandPermission, String> permissions = new java.util.EnumMap<>(CommandSettings.CommandPermission.class);
        for (CommandSettings.CommandPermission permission : CommandSettings.CommandPermission.values()) {
            permissions.put(permission, reader.permission("permissions." + permission.configKey(), defaults.permissions().require(permission)));
        }
        Map<String, Long> multipliers = readAmountMultipliers(yaml, problems);

        long minimum = reader.longRange("pay.minimum", 1L, Long.MAX_VALUE, defaults.pay().minimum());
        long maximum = reader.longRange("pay.maximum", 1L, Long.MAX_VALUE, defaults.pay().maximum());
        long cooldown = reader.longRange("pay.cooldown-seconds", 0L, 86_400L, defaults.pay().cooldownSeconds());
        boolean confirmationEnabled = reader.bool("pay.confirmation.enabled", true);
        long threshold = reader.longRange("pay.confirmation.threshold", 1L, Long.MAX_VALUE, defaults.pay().confirmation().threshold());
        long expiry = reader.longRange("pay.confirmation.expiry-seconds", 5L, 3_600L, defaults.pay().confirmation().expirySeconds());
        long retention = reader.longRange("pay.retry-retention-seconds", 60L, 604_800L, defaults.pay().retryRetentionSeconds());

        if (maximum > config.economy().maximumBalance()) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":pay.maximum", "must not exceed economy.maximum-balance"));
        }
        if (threshold > config.economy().maximumBalance()) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":pay.confirmation.threshold", "must not exceed economy.maximum-balance"));
        }

        int pageSize = reader.intRange("history.page-size", 1, 100, defaults.history().pageSize());
        long sessionExpiry = reader.longRange("history.session-expiry-seconds", 10L, 3_600L, defaults.history().sessionExpirySeconds());
        ZoneId timeZone = reader.timeZone("history.time-zone", defaults.history().timeZone());
        int suggestionsSize = reader.intRange("suggestions.maximum-size", 1, 10_000, defaults.suggestions().maximumSize());
        long suggestionsRefresh = reader.longRange("suggestions.refresh-seconds", 30L, 86_400L, defaults.suggestions().refreshSeconds());

        try {
            return new CommandSettings(
                new CommandSettings.Registration(root, aliases),
                new CommandSettings.Availability(availability),
                new CommandSettings.Permissions(permissions),
                new CommandSettings.AmountInput(multipliers),
                new CommandSettings.Pay(minimum, maximum, cooldown,
                    new CommandSettings.Confirmation(confirmationEnabled, threshold, expiry), retention),
                new CommandSettings.History(pageSize, sessionExpiry, timeZone),
                new CommandSettings.Suggestions(suggestionsSize, suggestionsRefresh),
                new CommandSettings.Reasons(
                    reader.reason("reasons.player-transfer", defaults.reasons().playerTransfer()),
                    reader.reason("reasons.admin-add", defaults.reasons().adminAdd()),
                    reader.reason("reasons.admin-remove", defaults.reasons().adminRemove()),
                    reader.reason("reasons.admin-set", defaults.reasons().adminSet()),
                    reader.reason("reasons.admin-reset", defaults.reasons().adminReset())
                )
            );
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME, exception.getMessage()));
            return defaults;
        }
    }

    private Map<String, Long> readAmountMultipliers(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Map<String, Long> multipliers = new LinkedHashMap<>();
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (!route.startsWith("amount-input.multipliers.")) continue;
            String suffix = route.substring("amount-input.multipliers.".length());
            Object raw = yaml.get(route, null);
            if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()) {
                problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ':' + route, "must be an integer"));
                continue;
            }
            multipliers.put(suffix, number.longValue());
        }
        if (multipliers.isEmpty()) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":amount-input.multipliers", "must define at least one suffix"));
            return CommandSettings.AmountInput.defaults().multipliers();
        }
        return multipliers;
    }

    private List<String> readCommandAliases(YamlDocument yaml, List<ConfigurationProblem> problems) {
        Object raw = yaml.get("registration.aliases", List.of());
        if (!(raw instanceof List<?> list)) {
            problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":registration.aliases", "must be a list"));
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        Reader reader = new Reader(yaml, problems, COMMANDS_FILE_NAME + ':');
        for (int index = 0; index < list.size(); index++) {
            Object value = list.get(index);
            if (!(value instanceof String text)) {
                problems.add(new ConfigurationProblem(COMMANDS_FILE_NAME + ":registration.aliases[" + index + ']', "must be a string"));
                continue;
            }
            aliases.add(reader.validateCommandLabel("registration.aliases[" + index + ']', text, ""));
        }
        return aliases;
    }

    private LoadedCatalog loadCatalog(String language, String fileName, List<ConfigurationProblem> problems) throws ConfigurationLoadException {
        byte[] defaults = readDefaults(fileName);
        byte[] document = readDocument(fileName, defaults);
        YamlDocument yaml = loadYaml(fileName, document, defaults, MESSAGES_VERSION);
        validateMessageRoutes(fileName, yaml, problems);
        MessageCatalog catalog = readCatalog(language, fileName, yaml, problems);
        return new LoadedCatalog(catalog, yaml.dump());
    }

    private void validateMessageRoutes(String fileName, YamlDocument yaml, List<ConfigurationProblem> problems) {
        for (String route : yaml.getRoutesAsStrings(true)) {
            if (route.equals("config-version") || route.equals("number-format") || route.equals("currency") || route.equals("messages") || route.equals("feedback")) {
                continue;
            }
            if (route.startsWith("number-format.")) {
                validateNumberFormatRoute(fileName, route, problems);
                continue;
            }
            if (Set.of("currency.display-name", "currency.symbol", "currency.format", "currency.price-format").contains(route)) {
                continue;
            }
            if (route.startsWith("messages.")) {
                String key = route.substring("messages.".length());
                if (!MESSAGE_KEYS.contains(key)) {
                    problems.add(new ConfigurationProblem(fileName + ':' + route, "unknown message key"));
                }
                continue;
            }
            if (route.startsWith("feedback.")) {
                validateFeedbackRoute(fileName, route, problems);
                continue;
            }
            problems.add(new ConfigurationProblem(fileName + ':' + route, "unknown configuration key"));
        }
    }

    private void validateNumberFormatRoute(String fileName, String route, List<ConfigurationProblem> problems) {
        if (Set.of(
            "number-format.grouping-separator",
            "number-format.decimal-separator",
            "number-format.compact-decimals",
            "number-format.compact-space",
            "number-format.loading-text",
            "number-format.compact-suffixes"
        ).contains(route)) {
            return;
        }
        if (route.startsWith("number-format.compact-suffixes.")) {
            try {
                NumberFormatSettings.CompactMagnitude.fromConfigKey(route.substring("number-format.compact-suffixes.".length()));
                return;
            } catch (IllegalArgumentException ignored) {
                // handled below
            }
        }
        problems.add(new ConfigurationProblem(fileName + ':' + route, "unknown number format key"));
    }

    private void validateFeedbackRoute(String fileName, String route, List<ConfigurationProblem> problems) {
        String remaining = route.substring("feedback.".length());
        String key = remaining.contains(".") ? remaining.substring(0, remaining.indexOf('.')) : remaining;
        if (!FEEDBACK_KEYS.contains(key)) {
            problems.add(new ConfigurationProblem(fileName + ':' + route, "unknown feedback key"));
        }
    }

    private MessageCatalog readCatalog(String language, String fileName, YamlDocument yaml, List<ConfigurationProblem> problems) {
        Reader reader = new Reader(yaml, problems);
        int version = reader.intRange("config-version", MESSAGES_VERSION, MESSAGES_VERSION, MESSAGES_VERSION);
        if (version != MESSAGES_VERSION) {
            problems.add(new ConfigurationProblem(fileName + ":config-version", "unsupported version"));
        }
        reader.requireSection("number-format");
        reader.requireSection("number-format.compact-suffixes");
        reader.requireSection("currency");
        reader.requireSection("messages");
        reader.requireSection("feedback");
        NumberFormatSettings numberFormat = readNumberFormat(fileName, yaml, reader, problems);
        CurrencySettings currency = readCurrency(fileName, reader, problems);

        Map<String, String> messages = new LinkedHashMap<>();
        for (String key : MESSAGE_KEYS) {
            Object value = yaml.get("messages." + key, null);
            if (value == null) {
                continue;
            }
            if (!(value instanceof String text)) {
                problems.add(new ConfigurationProblem(fileName + ":messages." + key, "must be a string"));
                continue;
            }
            validateTemplate(fileName + ":messages." + key, text, PLACEHOLDERS.getOrDefault(key, Set.of()), problems);
            messages.put(key, text);
        }

        Map<String, List<FeedbackActionConfig>> feedback = new LinkedHashMap<>();
        for (String key : FEEDBACK_KEYS) {
            Object value = yaml.get("feedback." + key, null);
            if (value == null) {
                continue;
            }
            if (!(value instanceof List<?> list)) {
                problems.add(new ConfigurationProblem(fileName + ":feedback." + key, "must be a list"));
                continue;
            }
            List<FeedbackActionConfig> actions = readFeedbackActions(fileName, key, list, problems);
            if (!actions.isEmpty()) {
                feedback.put(key, actions);
            }
        }
        return new MessageCatalog(language, numberFormat, currency, messages, feedback);
    }

    private CurrencySettings readCurrency(String fileName, Reader reader, List<ConfigurationProblem> problems) {
        CurrencySettings defaults = CurrencySettings.defaults();
        String displayName = reader.text("currency.display-name", defaults.displayName(), false, false);
        String symbol = reader.text("currency.symbol", defaults.symbol(), true, false);
        String format = reader.text("currency.format", defaults.format(), false, false);
        String rawFormat = reader.text("currency.price-format", defaults.priceFormat().name(), false, false);
        try {
            return new CurrencySettings(displayName, symbol, format, PriceFormat.valueOf(rawFormat.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem(fileName + ":currency", exception.getMessage()));
            return defaults;
        }
    }

    private NumberFormatSettings readNumberFormat(String fileName, YamlDocument yaml, Reader reader, List<ConfigurationProblem> problems) {
        String grouping = reader.text("number-format.grouping-separator", ",", false, true);
        String decimal = reader.text("number-format.decimal-separator", ".", false, true);
        int decimals = reader.intRange("number-format.compact-decimals", 0, 2, 1);
        boolean compactSpace = reader.bool("number-format.compact-space", false);
        String loadingText = reader.text("number-format.loading-text", "Loading...", false, false);
        Map<NumberFormatSettings.CompactMagnitude, String> suffixes = new HashMap<>();
        for (NumberFormatSettings.CompactMagnitude magnitude : NumberFormatSettings.CompactMagnitude.values()) {
            suffixes.put(magnitude, reader.text("number-format.compact-suffixes." + magnitude.configKey(), defaultSuffix(magnitude), false, false));
        }
        try {
            return new NumberFormatSettings(grouping, decimal, decimals, compactSpace, suffixes, loadingText);
        } catch (IllegalArgumentException exception) {
            problems.add(new ConfigurationProblem(fileName + ":number-format", exception.getMessage()));
            return defaultNumberFormat();
        }
    }

    private List<FeedbackActionConfig> readFeedbackActions(String fileName, String key, List<?> list, List<ConfigurationProblem> problems) {
        List<FeedbackActionConfig> actions = new ArrayList<>();
        if (list.size() > 8) {
            problems.add(new ConfigurationProblem(fileName + ":feedback." + key, "must contain at most 8 actions"));
            return actions;
        }
        for (int index = 0; index < list.size(); index++) {
            Object value = list.get(index);
            if (!(value instanceof Map<?, ?> map)) {
                problems.add(new ConfigurationProblem(fileName + ":feedback." + key + '[' + index + ']', "action must be a map"));
                continue;
            }
            FeedbackActionConfig action = readFeedbackAction(fileName, key, index, map, problems);
            if (action != null) {
                actions.add(action);
            }
        }
        return actions;
    }

    private FeedbackActionConfig readFeedbackAction(String fileName, String key, int index, Map<?, ?> map, List<ConfigurationProblem> problems) {
        String base = fileName + ":feedback." + key + '[' + index + ']';
        String type = string(map, "type", base, problems, false);
        if (type == null) {
            return null;
        }
        try {
            return switch (type.trim().toLowerCase(Locale.ROOT)) {
                case "chat" -> {
                    String message = requiredTemplate(map, "message", key, base, problems);
                    yield message == null ? null : new FeedbackActionConfig.Chat(message);
                }
                case "action_bar" -> {
                    String message = requiredTemplate(map, "message", key, base, problems);
                    yield message == null ? null : new FeedbackActionConfig.ActionBar(message);
                }
                case "title" -> {
                    String title = requiredTemplate(map, "title", key, base, problems);
                    String subtitle = optionalTemplate(map, "subtitle", key, base, problems);
                    yield title == null ? null : new FeedbackActionConfig.Title(
                        title,
                        subtitle,
                        longValue(map, "fade-in", 10L, base, problems),
                        longValue(map, "stay", 40L, base, problems),
                        longValue(map, "fade-out", 10L, base, problems)
                    );
                }
                case "sound" -> new FeedbackActionConfig.Sound(
                    string(map, "sound", base, problems, false),
                    stringOrDefault(map, "source", "master", base, problems),
                    floatValue(map, "volume", 1.0F, base, problems),
                    floatValue(map, "pitch", 1.0F, base, problems)
                );
                case "boss_bar" -> {
                    String message = requiredTemplate(map, "message", key, base, problems);
                    yield message == null ? null : new FeedbackActionConfig.BossBar(
                        stringOrDefault(map, "channel", "progressengine:default", base, problems),
                        message,
                        stringOrDefault(map, "color", "green", base, problems),
                        stringOrDefault(map, "overlay", "progress", base, problems),
                        floatValue(map, "progress", 1.0F, base, problems),
                        longValue(map, "duration", 40L, base, problems)
                    );
                }
                default -> {
                    problems.add(new ConfigurationProblem(base + ".type", "unknown feedback action type"));
                    yield null;
                }
            };
        } catch (RuntimeException exception) {
            problems.add(new ConfigurationProblem(base, exception.getMessage()));
            return null;
        }
    }

    private MessageCatalog applyFallback(MessageCatalog catalog, MessageCatalog fallback) {
        Map<String, String> messages = new LinkedHashMap<>(fallback.messages());
        messages.putAll(catalog.messages());
        Map<String, List<FeedbackActionConfig>> feedback = new LinkedHashMap<>(fallback.feedback());
        feedback.putAll(catalog.feedback());
        return new MessageCatalog(catalog.language(), catalog.numberFormat(), catalog.currency(), messages, feedback);
    }

    private void validateTemplate(String path, String template, Set<String> placeholders, List<ConfigurationProblem> problems) {
        try {
            TagResolver.Builder builder = TagResolver.builder();
            for (String placeholder : placeholders) {
                builder.resolver(Placeholder.unparsed(placeholder, "0"));
            }
            STRICT_MINI_MESSAGE.deserialize(template, builder.build());
            validateUnknownTags(path, template, placeholders, problems);
        } catch (RuntimeException exception) {
            problems.add(new ConfigurationProblem(path, "invalid MiniMessage template"));
        }
    }

    private void validateUnknownTags(String path, String template, Set<String> placeholders, List<ConfigurationProblem> problems) {
        java.util.regex.Matcher matcher = MINI_MESSAGE_TAG.matcher(template);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!KNOWN_MINI_MESSAGE_TAGS.contains(tag) && !placeholders.contains(tag)) {
                problems.add(new ConfigurationProblem(path, "unknown MiniMessage tag or placeholder: " + tag));
            }
        }
    }

    private String requiredTemplate(Map<?, ?> map, String key, String messageKey, String base, List<ConfigurationProblem> problems) {
        String value = string(map, key, base, problems, false);
        if (value != null) {
            validateTemplate(base + '.' + key, value, PLACEHOLDERS.getOrDefault(messageKey, Set.of()), problems);
        }
        return value;
    }

    private String optionalTemplate(Map<?, ?> map, String key, String messageKey, String base, List<ConfigurationProblem> problems) {
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        String text = string(map, key, base, problems, true);
        if (text != null && !text.isBlank()) {
            validateTemplate(base + '.' + key, text, PLACEHOLDERS.getOrDefault(messageKey, Set.of()), problems);
        }
        return text == null ? "" : text;
    }

    private String string(Map<?, ?> map, String key, String base, List<ConfigurationProblem> problems, boolean allowBlank) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            problems.add(new ConfigurationProblem(base + '.' + key, "must be a string"));
            return null;
        }
        if (!allowBlank && text.isBlank()) {
            problems.add(new ConfigurationProblem(base + '.' + key, "must not be blank"));
            return null;
        }
        return text;
    }

    private String stringOrDefault(Map<?, ?> map, String key, String fallback, String base, List<ConfigurationProblem> problems) {
        return map.containsKey(key) ? Objects.requireNonNullElse(string(map, key, base, problems, false), fallback) : fallback;
    }

    private long longValue(Map<?, ?> map, String key, long fallback, String base, List<ConfigurationProblem> problems) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        problems.add(new ConfigurationProblem(base + '.' + key, "must be an integer"));
        return fallback;
    }

    private float floatValue(Map<?, ?> map, String key, float fallback, String base, List<ConfigurationProblem> problems) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        problems.add(new ConfigurationProblem(base + '.' + key, "must be a number"));
        return fallback;
    }

    private static NumberFormatSettings defaultNumberFormat() {
        return new NumberFormatSettings(",", ".", 1, false, Map.of(
            NumberFormatSettings.CompactMagnitude.THOUSAND, "K",
            NumberFormatSettings.CompactMagnitude.MILLION, "M",
            NumberFormatSettings.CompactMagnitude.BILLION, "B",
            NumberFormatSettings.CompactMagnitude.TRILLION, "T",
            NumberFormatSettings.CompactMagnitude.QUADRILLION, "Qa",
            NumberFormatSettings.CompactMagnitude.QUINTILLION, "Qi"
        ), "Loading...");
    }

    private static String defaultSuffix(NumberFormatSettings.CompactMagnitude magnitude) {
        return switch (magnitude) {
            case THOUSAND -> "K";
            case MILLION -> "M";
            case BILLION -> "B";
            case TRILLION -> "T";
            case QUADRILLION -> "Qa";
            case QUINTILLION -> "Qi";
        };
    }

    private record LoadedIdentity(IdentitySettings settings, String serialized) {
    }

    private record LoadedCommands(CommandSettings settings, String serialized) {
    }

    private record LoadedPayDialog(PayConfirmationDialogSettings settings, String serialized) {
    }

    private record LoadedCatalog(MessageCatalog catalog, String serialized) {
    }

    private record LoadedMessages(MessageCatalogs catalogs, String serializedEs, String serializedEn) {
    }

    private static final class Reader {
        private final YamlDocument yaml;
        private final List<ConfigurationProblem> problems;
        private final String pathPrefix;

        private Reader(YamlDocument yaml, List<ConfigurationProblem> problems) {
            this(yaml, problems, "");
        }

        private Reader(YamlDocument yaml, List<ConfigurationProblem> problems, String pathPrefix) {
            this.yaml = yaml;
            this.problems = problems;
            this.pathPrefix = pathPrefix;
        }

        private ConfigurationProblem problem(String path, String message) {
            return new ConfigurationProblem(this.pathPrefix + path, message);
        }

        private void requireVersion() {
            int version = intRange("config-version", CONFIG_VERSION, CONFIG_VERSION, CONFIG_VERSION);
            if (version != CONFIG_VERSION) {
                this.problems.add(problem("config-version", "unsupported version"));
            }
        }

        private void requireSection(String path) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof Section)) {
                this.problems.add(problem(path, "must be a section"));
            }
        }

        private String text(String path, String fallback, boolean allowEmpty, boolean allowWhitespace) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof String text)) {
                this.problems.add(problem(path, "must be a string"));
                return fallback;
            }
            if (!allowWhitespace && !text.equals(text.trim())) {
                this.problems.add(problem(path, "must not contain leading or trailing whitespace"));
            }
            if (!allowEmpty && text.isBlank()) {
                this.problems.add(problem(path, "must not be empty"));
                return fallback;
            }
            return text;
        }

        private String component(String path, String fallback, boolean allowDefaultUnknown) {
            String value = text(path, fallback, false, false);
            if (!REDIS_COMPONENT.matcher(value).matches() || value.length() > 256) {
                this.problems.add(problem(path, "must match [a-zA-Z0-9._:-]+ and be at most 256 characters"));
                return fallback;
            }
            if (!allowDefaultUnknown && value.equals("unknown")) {
                this.problems.add(problem(path, "must not use the reserved value \"unknown\""));
                return fallback;
            }
            return value;
        }

        private String tablePrefix(String path, String fallback) {
            String value = text(path, fallback, true, false);
            if (!TABLE_PREFIX.matcher(value).matches()) {
                this.problems.add(problem(path, "must contain only letters, numbers or underscores"));
                return fallback;
            }
            return value;
        }

        private AwardRounding rounding(String path, AwardRounding fallback) {
            String value = text(path, fallback.name(), false, false);
            try {
                return AwardRounding.valueOf(value);
            } catch (IllegalArgumentException exception) {
                this.problems.add(problem(path, "must be FLOOR"));
                return fallback;
            }
        }

        private String language(String path, String fallback) {
            String value = text(path, fallback, false, false).trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.equals("es") && !value.equals("en")) {
                this.problems.add(problem(path, "must be es or en"));
                return fallback;
            }
            return value;
        }

        private boolean bool(String path, boolean fallback) {
            Object value = this.yaml.get(path, null);
            if (!(value instanceof Boolean bool)) {
                this.problems.add(problem(path, "must be a boolean"));
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
                this.problems.add(problem(path, "must be between " + min + " and " + max));
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
                this.problems.add(problem(path, "must be between " + min + " and " + max));
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
                this.problems.add(problem(path, "must be 0 or between " + minNonZero + " and " + max));
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
            this.problems.add(problem(path, "must be an integer"));
            return null;
        }

        private String commandLabel(String path, String fallback) {
            return validateCommandLabel(path, text(path, fallback, false, false), fallback);
        }

        private String validateCommandLabel(String path, String value, String fallback) {
            if (!value.matches("[a-z][a-z0-9_-]{0,31}")) {
                this.problems.add(problem(path, "must match [a-z][a-z0-9_-]{0,31}"));
                return fallback;
            }
            return value;
        }

        private String permission(String path, String fallback) {
            String value = text(path, fallback, false, false);
            if (!value.matches("[a-z0-9._-]+(\\.[a-z0-9._-]+)*") || value.length() > 128) {
                this.problems.add(problem(path, "must be a valid permission node"));
                return fallback;
            }
            return value;
        }

        private com.stephanofer.progressengine.api.operation.OperationReason reason(String path,
                                                                                    com.stephanofer.progressengine.api.operation.OperationReason fallback) {
            String value = text(path, fallback.value(), false, false);
            try {
                return com.stephanofer.progressengine.api.operation.OperationReason.of(value);
            } catch (IllegalArgumentException exception) {
                this.problems.add(problem(path, exception.getMessage()));
                return fallback;
            }
        }

        private ZoneId timeZone(String path, ZoneId fallback) {
            String value = text(path, fallback.getId(), false, false);
            try {
                return ZoneId.of(value);
            } catch (RuntimeException exception) {
                this.problems.add(problem(path, "must be a valid time zone"));
                return fallback;
            }
        }
    }
}
