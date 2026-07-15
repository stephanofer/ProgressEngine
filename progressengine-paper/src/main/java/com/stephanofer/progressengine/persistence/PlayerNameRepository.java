package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerNameRepository {
    private static final int MAX_SUGGESTIONS = 10_000;

    private final Database database;
    private final DatabaseTables tables;
    private final AccountRepository accounts;

    public PlayerNameRepository(Database database, DatabaseTables tables, AccountRepository accounts) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    public CompletableFuture<Void> updateCurrentMapping(UUID playerId, String username, Instant lastSeenAt) {
        BinaryUuid.requireValid(playerId, "playerId");
        String validUsername = PlayerUsernames.requireValid(username);
        String normalized = PlayerUsernames.normalize(validUsername);
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        TransactionOptions options = TransactionOptions.builder()
            .isolation(TransactionIsolation.REPEATABLE_READ)
            .retryPolicy(TransactionRetryPolicy.mysqlTransient())
            .build();
        return this.database.transaction(options, connection -> {
            updateCurrentMapping(connection, playerId, normalized, validUsername, lastSeenAt);
            return null;
        });
    }

    public CompletableFuture<Optional<KnownPlayerName>> findByUsername(String username) {
        String normalized = PlayerUsernames.normalize(username);
        return this.database.query(connection -> findByUsername(connection, normalized));
    }

    public CompletableFuture<Optional<KnownPlayerName>> findByPlayerId(UUID playerId) {
        BinaryUuid.requireValid(playerId, "playerId");
        return this.database.query(connection -> findByPlayerId(connection, playerId));
    }

    public CompletableFuture<List<KnownPlayerName>> loadRecentSuggestions(int limit) {
        validateSuggestionLimit(limit);
        return this.database.query(connection -> loadRecentSuggestions(connection, limit));
    }

    void updateCurrentMapping(Connection connection, UUID playerId, String normalizedUsername, String username, Instant lastSeenAt) throws SQLException {
        this.accounts.createOrLoad(connection, playerId);
        lockAccount(connection, playerId);

        List<String> namesToLock = new ArrayList<>();
        namesToLock.add(normalizedUsername);
        findByPlayerId(connection, playerId).map(KnownPlayerName::normalizedUsername).ifPresent(namesToLock::add);
        for (String name : namesToLock.stream().distinct().sorted(Comparator.naturalOrder()).toList()) {
            lockName(connection, name);
        }

        String deleteByUuid = "DELETE FROM " + this.tables.playerNames() + " WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteByUuid)) {
            JdbcValues.setUuid(statement, 1, playerId);
            statement.executeUpdate();
        }

        String deleteByName = "DELETE FROM " + this.tables.playerNames() + " WHERE normalized_username = ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteByName)) {
            statement.setString(1, normalizedUsername);
            statement.executeUpdate();
        }

        String insert = "INSERT INTO " + this.tables.playerNames()
            + " (normalized_username, player_uuid, username, last_seen_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, normalizedUsername);
            JdbcValues.setUuid(statement, 2, playerId);
            statement.setString(3, username);
            JdbcValues.setInstant(statement, 4, lastSeenAt);
            statement.executeUpdate();
        }
    }

    private Optional<KnownPlayerName> findByUsername(Connection connection, String normalizedUsername) throws SQLException {
        String sql = "SELECT normalized_username, player_uuid, username, last_seen_at FROM " + this.tables.playerNames()
            + " WHERE normalized_username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                KnownPlayerName name = readName(resultSet);
                if (resultSet.next()) {
                    throw new PersistenceDataException("More than one player name row found for " + normalizedUsername);
                }
                return Optional.of(name);
            }
        }
    }

    private Optional<KnownPlayerName> findByPlayerId(Connection connection, UUID playerId) throws SQLException {
        String sql = "SELECT normalized_username, player_uuid, username, last_seen_at FROM " + this.tables.playerNames()
            + " WHERE player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcValues.setUuid(statement, 1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                KnownPlayerName name = readName(resultSet);
                if (resultSet.next()) {
                    throw new PersistenceDataException("More than one player name row found for " + playerId);
                }
                return Optional.of(name);
            }
        }
    }

    private List<KnownPlayerName> loadRecentSuggestions(Connection connection, int limit) throws SQLException {
        String sql = "SELECT normalized_username, player_uuid, username, last_seen_at FROM " + this.tables.playerNames()
            + " ORDER BY last_seen_at DESC, normalized_username ASC LIMIT ?";
        List<KnownPlayerName> names = new ArrayList<>(limit);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(readName(resultSet));
                }
            }
        }
        return List.copyOf(names);
    }

    private void lockAccount(Connection connection, UUID playerId) throws SQLException {
        String sql = "SELECT player_uuid FROM " + this.tables.accounts() + " WHERE player_uuid = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcValues.setUuid(statement, 1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new PersistenceDataException("Account disappeared while updating player name: " + playerId);
                }
            }
        }
    }

    private void lockName(Connection connection, String normalizedUsername) throws SQLException {
        String sql = "SELECT normalized_username FROM " + this.tables.playerNames()
            + " WHERE normalized_username = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUsername);
            try (ResultSet ignored = statement.executeQuery()) {
                while (ignored.next()) {
                    // Iteration exhausts the cursor and keeps the lock semantics explicit.
                }
            }
        }
    }

    private static KnownPlayerName readName(ResultSet resultSet) throws SQLException {
        return new KnownPlayerName(
            resultSet.getString("normalized_username"),
            JdbcValues.uuid(resultSet, "player_uuid"),
            resultSet.getString("username"),
            JdbcValues.instant(resultSet, "last_seen_at")
        );
    }

    private static void validateSuggestionLimit(int limit) {
        if (limit < 1 || limit > MAX_SUGGESTIONS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_SUGGESTIONS);
        }
    }
}
