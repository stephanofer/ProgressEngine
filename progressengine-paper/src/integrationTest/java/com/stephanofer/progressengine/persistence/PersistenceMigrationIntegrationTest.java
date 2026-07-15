package com.stephanofer.progressengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PersistenceMigrationIntegrationTest extends PersistenceIntegrationTestSupport {
    @Test
    void migratesEmptyAndAlreadyMigratedSchema() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            persistence.migrate().join();

            Integer migratedTables = persistence.database().query(connection -> {
                try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (?, ?, ?, ?, ?)"
                )) {
                    statement.setString(1, persistence.database().table(DatabaseTables.ACCOUNTS));
                    statement.setString(2, persistence.database().table(DatabaseTables.OPERATIONS));
                    statement.setString(3, persistence.database().table(DatabaseTables.LEDGER));
                    statement.setString(4, persistence.database().table(DatabaseTables.PLAYER_NAMES));
                    statement.setString(5, persistence.database().table(DatabaseTables.FLYWAY_HISTORY));
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        return resultSet.getInt(1);
                    }
                }
            }).join();

            assertEquals(5, migratedTables);
        } finally {
            cleanup(persistence);
        }
    }
}
