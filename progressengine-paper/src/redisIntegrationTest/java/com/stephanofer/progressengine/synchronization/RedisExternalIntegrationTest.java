package com.stephanofer.progressengine.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisClients;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisStartupMode;
import com.hera.craftkit.redis.RedisSubscription;
import com.stephanofer.progressengine.api.operation.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class RedisExternalIntegrationTest {
    @Test
    void pubSubDeliversInvalidationAfterSubscriptionAck() {
        RedisClient sender = client("sender-" + UUID.randomUUID());
        RedisClient receiver = client("receiver-" + UUID.randomUUID());
        try {
            RedisMessageCodec codec = new RedisMessageCodec();
            String channel = sender.channel("progressengine", "integration-" + UUID.randomUUID());
            CompletableFuture<BalanceInvalidationMessage> received = new CompletableFuture<>();
            RedisSubscription subscription = receiver.subscriber().subscribe(channel, message -> {
                received.complete(codec.decodeInvalidation(message.payload()));
            });
            await(() -> subscription.initialRegistration().isDone());
            subscription.initialRegistration().join();
            await(() -> receiver.operationalStatus().isOperational());

            BalanceInvalidationMessage sent = new BalanceInvalidationMessage(UUID.randomUUID(), 4L, OperationId.generate(), "sender-1");
            sender.publisher().publish(channel, codec.encode(sent)).join();

            await(received::isDone);
            assertEquals(sent, received.join());
            assertTrue(subscription.isActive());
        } finally {
            sender.close();
            receiver.close();
        }
    }

    private static RedisClient client(String serverId) {
        return RedisClients.lettuce(
            RedisConfig.builder()
                .host(env("PROGRESSENGINE_TEST_REDIS_HOST", "127.0.0.1"))
                .port(Integer.parseInt(env("PROGRESSENGINE_TEST_REDIS_PORT", "6379")))
                .database(Integer.parseInt(env("PROGRESSENGINE_TEST_REDIS_DATABASE", "0")))
                .username(env("PROGRESSENGINE_TEST_REDIS_USER", ""))
                .password(env("PROGRESSENGINE_TEST_REDIS_PASSWORD", ""))
                .ssl(Boolean.parseBoolean(env("PROGRESSENGINE_TEST_REDIS_SSL", "false")))
                .keyPrefix("progressengine-test")
                .environment("it-" + UUID.randomUUID())
                .serverId(serverId)
                .commandTimeout(Duration.ofSeconds(3L))
                .connectTimeout(Duration.ofSeconds(3L))
                .build(),
            RedisStartupMode.RECOVER
        );
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void await(Condition condition) {
        Instant deadline = Instant.now().plusSeconds(10L);
        while (!condition.completed() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for Redis integration state", exception);
            }
        }
        assertTrue(condition.completed(), "Timed out waiting for Redis integration state");
    }

    @FunctionalInterface
    private interface Condition {
        boolean completed();
    }
}
