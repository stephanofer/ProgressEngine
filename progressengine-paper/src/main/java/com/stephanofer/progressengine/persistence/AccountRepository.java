package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AccountRepository {
    private static final int MAX_BATCH_SIZE = 1_000;

    private final Database database;
    private final DatabaseTables tables;

    public AccountRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<Optional<StoredAccount>> find(UUID playerId) {
        BinaryUuid.requireValid(playerId, "playerId");
        return this.database.query(connection -> find(connection, playerId));
    }

    public CompletableFuture<StoredAccount> createOrLoad(UUID playerId) {
        BinaryUuid.requireValid(playerId, "playerId");
        return this.database.transaction(connection -> createOrLoad(connection, playerId));
    }

    public CompletableFuture<Map<UUID, Long>> loadRevisions(Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds");
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            deduplicated.add(BinaryUuid.requireValid(playerId, "playerId"));
        }
        if (deduplicated.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return this.database.query(connection -> loadRevisions(connection, List.copyOf(deduplicated)));
    }

    Optional<StoredAccount> find(Connection connection, UUID playerId) throws SQLException {
        String sql = "SELECT player_uuid, balance, revision, created_at, updated_at FROM " + this.tables.accounts() + " WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcValues.setUuid(statement, 1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                StoredAccount account = readAccount(resultSet);
                if (resultSet.next()) {
                    throw new PersistenceDataException("More than one account row found for player " + playerId);
                }
                return Optional.of(account);
            }
        }
    }

    StoredAccount createOrLoad(Connection connection, UUID playerId) throws SQLException {
        String insert = "INSERT INTO " + this.tables.accounts()
            + " (player_uuid, balance, revision, created_at, updated_at) VALUES (?, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) "
            + "ON DUPLICATE KEY UPDATE player_uuid = player_uuid";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            JdbcValues.setUuid(statement, 1, playerId);
            statement.executeUpdate();
        }
        return find(connection, playerId).orElseThrow(() -> new PersistenceDataException("Account was not created for " + playerId));
    }

    private Map<UUID, Long> loadRevisions(Connection connection, List<UUID> playerIds) throws SQLException {
        Map<UUID, Long> revisions = new HashMap<>();
        for (int offset = 0; offset < playerIds.size(); offset += MAX_BATCH_SIZE) {
            List<UUID> batch = playerIds.subList(offset, Math.min(playerIds.size(), offset + MAX_BATCH_SIZE));
            String placeholders = "?,".repeat(batch.size());
            placeholders = placeholders.substring(0, placeholders.length() - 1);
            String sql = "SELECT player_uuid, revision FROM " + this.tables.accounts() + " WHERE player_uuid IN (" + placeholders + ')';
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < batch.size(); index++) {
                    JdbcValues.setUuid(statement, index + 1, batch.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID playerId = JdbcValues.uuid(resultSet, "player_uuid");
                        long revision = resultSet.getLong("revision");
                        if (revision < 0L) {
                            throw new PersistenceDataException("Stored account revision cannot be negative");
                        }
                        revisions.put(playerId, revision);
                    }
                }
            }
        }
        return Map.copyOf(revisions);
    }

    private static StoredAccount readAccount(ResultSet resultSet) throws SQLException {
        return new StoredAccount(
            JdbcValues.uuid(resultSet, "player_uuid"),
            resultSet.getLong("balance"),
            resultSet.getLong("revision"),
            JdbcValues.instant(resultSet, "created_at"),
            JdbcValues.instant(resultSet, "updated_at")
        );
    }
}
