package com.stephanofer.progressengine.synchronization;

import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisClients;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisStartupMode;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.time.Duration;
import java.util.Objects;

public final class ProgressRedisFactory {
    private ProgressRedisFactory() {
    }

    public static RedisClient create(ProgressEngineConfig config) {
        Objects.requireNonNull(config, "config");
        ProgressEngineConfig.RedisSettings redis = config.redis();
        RedisConfig craftKitConfig = RedisConfig.builder()
            .host(redis.host())
            .port(redis.port())
            .database(redis.database())
            .username(redis.username())
            .password(redis.password())
            .ssl(redis.ssl())
            .verifyPeer(redis.verifyPeer())
            .commandTimeout(Duration.ofMillis(redis.commandTimeoutMillis()))
            .connectTimeout(Duration.ofMillis(redis.connectTimeoutMillis()))
            .shutdownTimeout(Duration.ofMillis(redis.shutdownTimeoutMillis()))
            .autoReconnect(true)
            .requestQueueSize(redis.requestQueueSize())
            .reconnectMinDelay(Duration.ofMillis(redis.reconnectMinDelayMillis()))
            .reconnectMaxDelay(Duration.ofMillis(redis.reconnectMaxDelayMillis()))
            .ioThreads(redis.ioThreads())
            .computationThreads(redis.computationThreads())
            .keyPrefix(redis.namespace().keyPrefix())
            .environment(redis.namespace().environment())
            .serverId(config.serverId())
            .build();
        return RedisClients.lettuce(craftKitConfig, RedisStartupMode.RECOVER);
    }
}
