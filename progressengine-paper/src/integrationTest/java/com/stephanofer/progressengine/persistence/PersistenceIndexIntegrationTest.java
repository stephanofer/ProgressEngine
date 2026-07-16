package com.stephanofer.progressengine.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PersistenceIndexIntegrationTest extends PersistenceIntegrationTestSupport {
    @Test
    void criticalReleaseIndexesExistForHistoryCooldownAndIntentCleanup() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();

            Set<String> ledgerIndexes = indexes(persistence, persistence.tables().ledger());
            Set<String> operationIndexes = indexes(persistence, persistence.tables().operations());
            Set<String> intentIndexes = indexes(persistence, persistence.tables().commandIntents());

            assertTrue(ledgerIndexes.contains("idx_progress_ledger_player_history"));
            assertTrue(ledgerIndexes.contains("uk_progress_ledger_operation_player"));
            assertTrue(operationIndexes.contains("PRIMARY"));
            assertTrue(operationIndexes.contains("idx_progress_operations_actor_pay_cooldown"));
            assertTrue(intentIndexes.contains("PRIMARY"));
            assertTrue(intentIndexes.contains("uk_progress_command_intents_operation"));
            assertTrue(intentIndexes.contains("idx_progress_command_intents_expiry"));
            assertTrue(intentIndexes.contains("idx_progress_command_intents_owner"));
        } finally {
            cleanup(persistence);
        }
    }

    private static Set<String> indexes(ProgressPersistence persistence, String table) {
        return persistence.database().query(connection -> {
            Set<String> names = new HashSet<>();
            try (var statement = connection.prepareStatement("SHOW INDEX FROM " + table);
                 var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("Key_name"));
                }
            }
            return names;
        }).join();
    }
}
