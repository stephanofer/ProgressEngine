package com.stephanofer.progressengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ConfigurationManagerTest {
    @Test
    void invalidReloadKeepsPreviousSnapshot() {
        QueueLoader loader = new QueueLoader();
        loader.success(snapshot(1L));
        loader.failure(new ConfigurationProblem("server-id", "must not use unknown"));
        ConfigurationManager manager = new ConfigurationManager(loader, Runnable::run);

        ConfigurationReloadResult first = manager.reloadAsync().join();
        ConfigurationReloadResult second = manager.reloadAsync().join();

        assertTrue(first.success());
        assertFalse(second.success());
        assertEquals(1L, manager.activeSnapshot().orElseThrow().revision());
        assertEquals(Optional.of(first.activeSnapshot().orElseThrow()), second.activeSnapshot());
    }

    @Test
    void reloadReportsRestartRequiredForServerIdAndPublishesEffectivePreviousValue() {
        QueueLoader loader = new QueueLoader();
        loader.success(snapshot(1L));
        loader.success(snapshot(2L));
        ConfigurationManager manager = new ConfigurationManager(loader, Runnable::run);

        ConfigurationReloadResult first = manager.reloadAsync().join();
        ConfigurationReloadResult second = manager.reloadAsync().join();

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals("server-1", second.activeSnapshot().orElseThrow().config().serverId());
        assertTrue(second.changes().restartRequired().contains("server-id"));
    }

    @Test
    void concurrentReloadsShareTheSameFuture() throws Exception {
        BlockingLoader loader = new BlockingLoader();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ConfigurationManager manager = new ConfigurationManager(loader, executor);

            CompletableFuture<ConfigurationReloadResult> first = manager.reloadAsync();
            CompletableFuture<ConfigurationReloadResult> second = manager.reloadAsync();

            assertSame(first, second);
            assertTrue(loader.started.await(5L, TimeUnit.SECONDS));
            loader.release.countDown();
            assertTrue(first.join().success());
            assertEquals(1L, manager.activeSnapshot().orElseThrow().revision());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closedManagerDoesNotPublishLateLoads() throws Exception {
        BlockingLoader loader = new BlockingLoader();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ConfigurationManager manager = new ConfigurationManager(loader, executor);

            CompletableFuture<ConfigurationReloadResult> reload = manager.reloadAsync();
            assertTrue(loader.started.await(5L, TimeUnit.SECONDS));
            manager.close();
            loader.release.countDown();

            ConfigurationReloadResult result = reload.join();
            assertFalse(result.success());
            assertTrue(manager.activeSnapshot().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    private static ConfigurationSnapshot snapshot(long revision) {
        ProgressEngineConfig config = new ProgressEngineConfig(
            "server-" + revision,
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
                "",
                "secret",
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
            new ProgressEngineConfig.RuntimeSettings(10)
        );
        return new ConfigurationSnapshot(revision, Instant.EPOCH, config);
    }

    private static final class QueueLoader implements ConfigurationLoader {
        private final Queue<Object> outcomes = new ArrayDeque<>();

        void success(ConfigurationSnapshot snapshot) {
            this.outcomes.add(new LoadedConfiguration(snapshot, "config-version: 1"));
        }

        void failure(ConfigurationProblem problem) {
            this.outcomes.add(new ConfigurationLoadException(List.of(problem)));
        }

        @Override
        public LoadedConfiguration load(long revision) throws ConfigurationLoadException {
            Object outcome = this.outcomes.remove();
            if (outcome instanceof ConfigurationLoadException exception) {
                throw exception;
            }
            return (LoadedConfiguration) outcome;
        }

        @Override
        public void persist(LoadedConfiguration configuration) {
        }
    }

    private static final class BlockingLoader implements ConfigurationLoader {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public LoadedConfiguration load(long revision) {
            this.started.countDown();
            try {
                this.release.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return new LoadedConfiguration(snapshot(revision), "config-version: 1");
        }

        @Override
        public void persist(LoadedConfiguration configuration) throws IOException {
        }
    }
}
