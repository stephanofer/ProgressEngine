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

    public record EconomySettings(long maximumBalance, AwardRounding awardRounding) {
        public EconomySettings {
            if (maximumBalance < 1L) {
                throw new IllegalArgumentException("maximumBalance must be positive");
            }
            Objects.requireNonNull(awardRounding, "awardRounding");
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

    public record RuntimeSettings(long shutdownTimeoutSeconds) {
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
