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
    private static final String ES_MESSAGES_FILE_NAME = "messages/es.yml";
    private static final String EN_MESSAGES_FILE_NAME = "messages/en.yml";
    private static final int CONFIG_VERSION = 1;
    private static final int IDENTITY_VERSION = 1;
    private static final int MESSAGES_VERSION = 1;
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
        "invalid-amount",
        "unknown-target",
        "balance-self",
        "balance-other",
        "pay-confirm-required",
        "pay-success-sender",
        "pay-success-receiver",
        "pay-insufficient-funds",
        "pay-cooldown",
        "history-empty",
        "history-header",
        "history-entry",
        "admin-add-success",
        "admin-remove-success",
        "admin-set-success",
        "admin-reset-success",
        "admin-reload-success",
        "admin-reload-failure",
        "admin-status-header",
        "infrastructure-unavailable"
    );
    private static final Set<String> FEEDBACK_KEYS = Set.of("award-received", "transfer-received");
    private static final Map<String, Set<String>> PLACEHOLDERS = Map.ofEntries(
        Map.entry("points-loading", Set.of()),
        Map.entry("command-no-permission", Set.of()),
        Map.entry("command-player-only", Set.of()),
        Map.entry("command-console-only", Set.of()),
        Map.entry("invalid-amount", Set.of("input")),
        Map.entry("unknown-target", Set.of("target")),
        Map.entry("balance-self", Set.of("balance", "balance_raw", "balance_compact")),
        Map.entry("balance-other", Set.of("target", "balance", "balance_raw", "balance_compact")),
        Map.entry("pay-confirm-required", Set.of("amount", "target", "token")),
        Map.entry("pay-success-sender", Set.of("amount", "target", "balance")),
        Map.entry("pay-success-receiver", Set.of("amount", "sender", "balance")),
        Map.entry("pay-insufficient-funds", Set.of("amount", "balance")),
        Map.entry("pay-cooldown", Set.of("seconds")),
        Map.entry("history-empty", Set.of()),
        Map.entry("history-header", Set.of("page")),
        Map.entry("history-entry", Set.of("actor", "target", "amount", "balance", "reason", "date")),
        Map.entry("admin-add-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-remove-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-set-success", Set.of("amount", "target", "balance")),
        Map.entry("admin-reset-success", Set.of("target", "balance")),
        Map.entry("admin-reload-success", Set.of("revision")),
        Map.entry("admin-reload-failure", Set.of("problems")),
        Map.entry("admin-status-header", Set.of("state")),
        Map.entry("infrastructure-unavailable", Set.of()),
        Map.entry("award-received", Set.of("amount", "amount_raw", "amount_compact", "balance", "balance_raw", "balance_compact")),
        Map.entry("transfer-received", Set.of("amount", "sender", "balance"))
    );
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
        "localization",
        "localization.fallback-language",
        "localization.console-language",
        "feedback",
        "feedback.award-coalescing-window-ticks",
        "runtime",
        "runtime.shutdown-timeout-seconds"
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
            loadedMessages.catalogs()
        );
        Map<String, String> serialized = new LinkedHashMap<>();
        serialized.put(CONFIG_FILE_NAME, yaml.dump());
        serialized.put(IDENTITY_FILE_NAME, loadedIdentity.serialized());
        serialized.put(ES_MESSAGES_FILE_NAME, loadedMessages.serializedEs());
        serialized.put(EN_MESSAGES_FILE_NAME, loadedMessages.serializedEn());
        return new LoadedConfiguration(snapshot, serialized);
    }

    @Override
    public void persist(LoadedConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        for (Map.Entry<String, String> document : configuration.serializedDocuments().entrySet()) {
            Path target = this.dataDirectory.resolve(document.getKey());
            Files.createDirectories(target.getParent() == null ? this.dataDirectory : target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, document.getValue(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
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
            if (route.equals("config-version") || route.equals("number-format") || route.equals("messages") || route.equals("feedback")) {
                continue;
            }
            if (route.startsWith("number-format.")) {
                validateNumberFormatRoute(fileName, route, problems);
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
        reader.requireSection("messages");
        reader.requireSection("feedback");
        NumberFormatSettings numberFormat = readNumberFormat(fileName, yaml, reader, problems);

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
        return new MessageCatalog(language, numberFormat, messages, feedback);
    }

    private NumberFormatSettings readNumberFormat(String fileName, YamlDocument yaml, Reader reader, List<ConfigurationProblem> problems) {
        String grouping = reader.text("number-format.grouping-separator", ",", false, true);
        String decimal = reader.text("number-format.decimal-separator", ".", false, true);
        int decimals = reader.intRange("number-format.compact-decimals", 0, 2, 1);
        boolean compactSpace = reader.bool("number-format.compact-space", false);
        Map<NumberFormatSettings.CompactMagnitude, String> suffixes = new HashMap<>();
        for (NumberFormatSettings.CompactMagnitude magnitude : NumberFormatSettings.CompactMagnitude.values()) {
            suffixes.put(magnitude, reader.text("number-format.compact-suffixes." + magnitude.configKey(), defaultSuffix(magnitude), false, false));
        }
        try {
            return new NumberFormatSettings(grouping, decimal, decimals, compactSpace, suffixes);
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
        return new MessageCatalog(catalog.language(), catalog.numberFormat(), messages, feedback);
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
        ));
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

    private record LoadedCatalog(MessageCatalog catalog, String serialized) {
    }

    private record LoadedMessages(MessageCatalogs catalogs, String serializedEs, String serializedEn) {
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

        private String language(String path, String fallback) {
            String value = text(path, fallback, false, false).trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.equals("es") && !value.equals("en")) {
                this.problems.add(new ConfigurationProblem(path, "must be es or en"));
                return fallback;
            }
            return value;
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
