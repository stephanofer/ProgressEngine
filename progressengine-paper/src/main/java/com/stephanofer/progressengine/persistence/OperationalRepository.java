package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class OperationalRepository {
    private final Database database;
    private final DatabaseTables tables;

    public OperationalRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<OperationalSnapshot> snapshot() {
        long started = System.nanoTime();
        return this.database.query(connection -> {
            try (PreparedStatement probe = connection.prepareStatement("SELECT 1")) {
                try (ResultSet ignored = probe.executeQuery()) {
                    while (ignored.next()) {
                        // Exhaust the tiny result set explicitly.
                    }
                }
            }
            Optional<String> version = Optional.empty();
            String sql = "SELECT version FROM " + this.tables.flywayHistory()
                + " WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) version = Optional.ofNullable(resultSet.getString("version"));
                }
            }
            return new OperationalSnapshot(true, version, Duration.ofNanos(System.nanoTime() - started), Optional.empty());
        }).exceptionally(failure -> new OperationalSnapshot(
            false,
            Optional.empty(),
            Duration.ofNanos(System.nanoTime() - started),
            Optional.ofNullable(failure.getMessage())
        ));
    }
}
