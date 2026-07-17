package com.stephanofer.progressengine.config;

import java.util.Objects;

public record ProgressEngineConfig(
    String serverId,
    EconomySettings economy,
    DatabaseSettings database,
    RedisSettings redis,
    CacheSettings cache,
    ReconciliationSettings reconciliation,
    IntegrationSettings integrations,
    RuntimeSettings runtime
) {
    public ProgressEngineConfig {
        serverId = requireText(serverId, "serverId");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(reconciliation, "reconciliation");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(runtime, "runtime");
    }

    public record EconomySettings(long maximumBalance, AwardRounding awardRounding, AmountColors amountColors) {
        public EconomySettings {
            if (maximumBalance < 1L) {
                throw new IllegalArgumentException("maximumBalance must be positive");
            }
            Objects.requireNonNull(awardRounding, "awardRounding");
            Objects.requireNonNull(amountColors, "amountColors");
        }

        public EconomySettings(long maximumBalance, AwardRounding awardRounding) {
            this(maximumBalance, awardRounding, AmountColors.defaults());
        }
    }

    public record AmountColors(boolean enabled, java.util.List<AmountColorTier> tiers) {
        public AmountColors {
            Objects.requireNonNull(tiers, "tiers");
            tiers = java.util.List.copyOf(tiers);
            if (tiers.isEmpty() || tiers.getFirst().minimum() != 0L) {
                throw new IllegalArgumentException("amount color tiers must start at zero");
            }
            long previous = -1L;
            for (AmountColorTier tier : tiers) {
                if (tier.minimum() <= previous) throw new IllegalArgumentException("amount color tier minimums must be strictly increasing");
                previous = tier.minimum();
            }
        }

        public static AmountColors defaults() {
            return new AmountColors(true, java.util.List.of(
                new AmountColorTier(0L, "#2bd66f"), new AmountColorTier(1_000L, "#a3d14d"),
                new AmountColorTier(1_000_000L, "#ebbc23"), new AmountColorTier(1_000_000_000L, "#eb7b23"),
                new AmountColorTier(1_000_000_000_000L, "#ff9999"), new AmountColorTier(1_000_000_000_000_000L, "#ff5353"),
                new AmountColorTier(1_000_000_000_000_000_000L, "#d92f45")
            ));
        }
    }

    public record AmountColorTier(long minimum, String color) {
        public AmountColorTier {
            if (minimum < 0L) throw new IllegalArgumentException("amount color minimum cannot be negative");
            color = requireText(color, "color");
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("amount color must be a six-digit hex color");
        }
    }

    public record DatabaseSettings(
        String host,
        int port,
        String name,
        String username,
        String password,
        String tablePrefix,
        DatabasePoolSettings pool
    ) {
        public DatabaseSettings {
            host = requireText(host, "host");
            name = requireText(name, "name");
            username = requireText(username, "username");
            password = Objects.requireNonNull(password, "password");
            tablePrefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
            Objects.requireNonNull(pool, "pool");
        }

        @Override
        public String toString() {
            return "DatabaseSettings[host=" + this.host + ", port=" + this.port + ", name=" + this.name
                + ", username=" + this.username + ", password=<hidden>, tablePrefix=" + this.tablePrefix
                + ", pool=" + this.pool + ']';
        }
    }

    public record DatabasePoolSettings(
        int maximumSize,
        int minimumIdle,
        long connectionTimeoutMillis,
        long validationTimeoutMillis,
        long idleTimeoutMillis,
        long maxLifetimeMillis,
        long leakDetectionThresholdMillis
    ) {
    }

    public record RedisSettings(
        String host,
        int port,
        int database,
        String username,
        String password,
        boolean ssl,
        boolean verifyPeer,
        long commandTimeoutMillis,
        long connectTimeoutMillis,
        long shutdownTimeoutMillis,
        int requestQueueSize,
        long reconnectMinDelayMillis,
        long reconnectMaxDelayMillis,
        int ioThreads,
        int computationThreads,
        RedisNamespace namespace
    ) {
        public RedisSettings {
            host = requireText(host, "host");
            username = Objects.requireNonNull(username, "username");
            password = Objects.requireNonNull(password, "password");
            Objects.requireNonNull(namespace, "namespace");
        }

        @Override
        public String toString() {
            return "RedisSettings[host=" + this.host + ", port=" + this.port + ", database=" + this.database
                + ", username=" + this.username + ", password=<hidden>, ssl=" + this.ssl
                + ", verifyPeer=" + this.verifyPeer + ", commandTimeoutMillis=" + this.commandTimeoutMillis
                + ", connectTimeoutMillis=" + this.connectTimeoutMillis + ", shutdownTimeoutMillis="
                + this.shutdownTimeoutMillis + ", requestQueueSize=" + this.requestQueueSize
                + ", reconnectMinDelayMillis=" + this.reconnectMinDelayMillis
                + ", reconnectMaxDelayMillis=" + this.reconnectMaxDelayMillis + ", ioThreads="
                + this.ioThreads + ", computationThreads=" + this.computationThreads + ", namespace="
                + this.namespace + ']';
        }
    }

    public record RedisNamespace(String keyPrefix, String environment) {
        public RedisNamespace {
            keyPrefix = requireText(keyPrefix, "keyPrefix");
            environment = requireText(environment, "environment");
        }
    }

    public record CacheSettings(long maximumSize, long expireAfterAccessSeconds, boolean recordStats) {
    }

    public record ReconciliationSettings(long normalIntervalSeconds, long degradedIntervalSeconds, int batchSize) {
    }

    public record IntegrationSettings(boolean networkBoostersEnabled, boolean placeholderApiEnabled) {
    }

    public record RuntimeSettings(long shutdownTimeoutSeconds, long databaseHealthIntervalSeconds) {
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
