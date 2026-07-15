package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.ExistingSchemaStrategy;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.database.PoolConfig;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.util.Objects;

public final class ProgressDatabaseFactory {
    private ProgressDatabaseFactory() {
    }

    public static ProgressPersistence create(ProgressEngineConfig config, ClassLoader classLoader) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(classLoader, "classLoader");
        ProgressEngineConfig.DatabaseSettings databaseSettings = config.database();
        DatabaseTables.validatePrefix(databaseSettings.tablePrefix());

        ProgressEngineConfig.DatabasePoolSettings poolSettings = databaseSettings.pool();
        PoolConfig pool = PoolConfig.builder()
            .poolName("progressengine-mysql")
            .maximumPoolSize(poolSettings.maximumSize())
            .minimumIdle(poolSettings.minimumIdle())
            .connectionTimeoutMillis(poolSettings.connectionTimeoutMillis())
            .validationTimeoutMillis(poolSettings.validationTimeoutMillis())
            .idleTimeoutMillis(poolSettings.idleTimeoutMillis())
            .maxLifetimeMillis(poolSettings.maxLifetimeMillis())
            .autoCommit(true)
            .leakDetectionThresholdMillis(poolSettings.leakDetectionThresholdMillis())
            .build();

        ExecutorConfig executor = ExecutorConfig.builder()
            .threadCount(poolSettings.maximumSize())
            .threadNamePrefix("progressengine-db")
            .daemon(true)
            .shutdownTimeoutMillis(config.runtime().shutdownTimeoutSeconds() * 1_000L)
            .build();

        MigrationConfig migration = MigrationConfig.builder()
            .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
            .classLoader(classLoader)
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .build();

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .host(databaseSettings.host())
            .port(databaseSettings.port())
            .database(databaseSettings.name())
            .username(databaseSettings.username())
            .password(databaseSettings.password())
            .tablePrefix(databaseSettings.tablePrefix())
            .pool(pool)
            .executor(executor)
            .migration(migration)
            .putJdbcProperty("connectionTimeZone", "UTC")
            .putJdbcProperty("forceConnectionTimeZoneToSession", "true")
            .putJdbcProperty("preserveInstants", "true")
            .build();

        Database database = Databases.mysql(databaseConfig);
        try {
            return new ProgressPersistence(database);
        } catch (RuntimeException exception) {
            database.close();
            throw exception;
        }
    }
}
