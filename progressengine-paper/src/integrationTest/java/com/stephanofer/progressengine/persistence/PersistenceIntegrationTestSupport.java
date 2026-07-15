package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.ExistingSchemaStrategy;
import com.hera.craftkit.database.ExecutorConfig;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.database.PoolConfig;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public abstract class PersistenceIntegrationTestSupport {
    private static final List<String> TABLES = List.of(
        DatabaseTables.LEDGER,
        DatabaseTables.PLAYER_NAMES,
        DatabaseTables.OPERATIONS,
        DatabaseTables.ACCOUNTS,
        DatabaseTables.FLYWAY_HISTORY
    );

    protected final ProgressPersistence createPersistence() {
        String prefix = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + '_';
        Database database = Databases.mysql(DatabaseConfig.builder()
            .host(env("PROGRESSENGINE_TEST_DB_HOST"))
            .port(Integer.parseInt(env("PROGRESSENGINE_TEST_DB_PORT")))
            .database(env("PROGRESSENGINE_TEST_DB_NAME"))
            .username(env("PROGRESSENGINE_TEST_DB_USER"))
            .password(env("PROGRESSENGINE_TEST_DB_PASSWORD"))
            .tablePrefix(prefix)
            .pool(PoolConfig.builder().poolName("progressengine-it").maximumPoolSize(4).minimumIdle(0).build())
            .executor(ExecutorConfig.builder().threadNamePrefix("progressengine-it-db").threadCount(4).build())
            .migration(MigrationConfig.builder()
                .existingSchemaStrategy(ExistingSchemaStrategy.BASELINE_AT_ZERO)
                .classLoader(getClass().getClassLoader())
                .build())
            .putJdbcProperty("connectionTimeZone", "UTC")
            .putJdbcProperty("forceConnectionTimeZoneToSession", "true")
            .putJdbcProperty("preserveInstants", "true")
            .build());
        return new ProgressPersistence(database);
    }

    protected final void cleanup(ProgressPersistence persistence) {
        Database database = persistence.database();
        String prefix = database.tablePrefix();
        try {
            database.execute(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String table : TABLES) {
                        statement.execute("DROP TABLE IF EXISTS `" + prefix + table + "`");
                    }
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }).join();
        } finally {
            persistence.close();
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration test environment variable " + name);
        }
        return value;
    }
}
