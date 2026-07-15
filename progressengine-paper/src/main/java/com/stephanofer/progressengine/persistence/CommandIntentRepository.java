package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.ActorType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CommandIntentRepository {
    private final Database database;
    private final DatabaseTables tables;

    public CommandIntentRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<Void> insert(CommandIntentDraft draft) {
        Objects.requireNonNull(draft, "draft");
        return this.database.execute(connection -> insert(connection, draft));
    }

    public CompletableFuture<Optional<CommandIntent>> find(byte[] tokenHash) {
        byte[] validHash = CommandIntentDraft.validateHash(tokenHash);
        return this.database.query(connection -> find(connection, validHash, false));
    }

    public CompletableFuture<Optional<CommandIntent>> markSubmitted(byte[] tokenHash, Instant submittedAt, Instant newExpiry) {
        byte[] validHash = CommandIntentDraft.validateHash(tokenHash);
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(newExpiry, "newExpiry");
        TransactionOptions options = TransactionOptions.builder().isolation(TransactionIsolation.READ_COMMITTED).build();
        return this.database.transaction(options, connection -> {
            Optional<CommandIntent> locked = find(connection, validHash, true);
            if (locked.isEmpty()) return Optional.empty();
            CommandIntent intent = locked.orElseThrow();
            if (intent.state() == CommandIntentState.RESOLVED) return Optional.of(intent);
            if (intent.state() == CommandIntentState.SUBMITTED) return Optional.of(intent);
            String sql = "UPDATE " + this.tables.commandIntents()
                + " SET state = 'SUBMITTED', submitted_at = ?, expires_at = ? WHERE token_hash = ? AND state = 'AWAITING_CONFIRMATION'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                JdbcValues.setInstant(statement, 1, submittedAt);
                JdbcValues.setInstant(statement, 2, newExpiry);
                statement.setBytes(3, validHash);
                statement.executeUpdate();
            }
            return find(connection, validHash, false);
        });
    }

    public CompletableFuture<Void> markResolved(byte[] tokenHash, Instant resolvedAt) {
        byte[] validHash = CommandIntentDraft.validateHash(tokenHash);
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        String sql = "UPDATE " + this.tables.commandIntents()
            + " SET state = 'RESOLVED', resolved_at = ? WHERE token_hash = ? AND state = 'SUBMITTED'";
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                JdbcValues.setInstant(statement, 1, resolvedAt);
                statement.setBytes(2, validHash);
                return statement.executeUpdate();
            }
        }).thenApply(ignored -> null);
    }

    public CompletableFuture<Integer> deleteExpired(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit must be between 1 and 10000");
        String sql = "DELETE FROM " + this.tables.commandIntents() + " WHERE expires_at <= ? LIMIT ?";
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                JdbcValues.setInstant(statement, 1, now);
                statement.setInt(2, limit);
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<Instant>> lastSuccessfulPlayerPay(UUID playerId) {
        BinaryUuid.requireValid(playerId, "playerId");
        String sql = "SELECT completed_at FROM " + this.tables.operations()
            + " WHERE actor_uuid = ? AND type = 'TRANSFER' AND status = 'SUCCESS' ORDER BY completed_at DESC LIMIT 1";
        return this.database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                JdbcValues.setUuid(statement, 1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? JdbcValues.optionalInstant(resultSet, "completed_at") : Optional.empty();
                }
            }
        });
    }

    private void insert(Connection connection, CommandIntentDraft draft) throws SQLException {
        String sql = "INSERT INTO " + this.tables.commandIntents()
            + " (token_hash, operation_id, intent_type, state, owner_uuid, actor_type, actor_uuid, player_uuid, target_uuid, amount, "
            + "reason_key, observed_revision, source_server_id, created_at, expires_at, submitted_at, resolved_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, draft.tokenHash());
            JdbcValues.setUuid(statement, 2, draft.operationId().value());
            statement.setString(3, draft.type().name());
            statement.setString(4, draft.state().name());
            JdbcValues.setOptionalUuid(statement, 5, draft.ownerId());
            statement.setString(6, draft.actorType().name());
            JdbcValues.setOptionalUuid(statement, 7, draft.actorId());
            JdbcValues.setUuid(statement, 8, draft.playerId());
            JdbcValues.setOptionalUuid(statement, 9, draft.targetId());
            statement.setLong(10, draft.amount());
            statement.setString(11, draft.reason().value());
            if (draft.observedRevision().isPresent()) statement.setLong(12, draft.observedRevision().get()); else statement.setNull(12, Types.BIGINT);
            statement.setString(13, draft.sourceServerId());
            JdbcValues.setInstant(statement, 14, draft.createdAt());
            JdbcValues.setInstant(statement, 15, draft.expiresAt());
            if (draft.state() == CommandIntentState.SUBMITTED) JdbcValues.setInstant(statement, 16, draft.createdAt()); else statement.setNull(16, Types.TIMESTAMP);
            statement.executeUpdate();
        }
    }

    private Optional<CommandIntent> find(Connection connection, byte[] tokenHash, boolean lock) throws SQLException {
        String sql = "SELECT * FROM " + this.tables.commandIntents() + " WHERE token_hash = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, tokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                CommandIntent intent = read(resultSet);
                if (resultSet.next()) throw new PersistenceDataException("More than one command intent found for token hash");
                return Optional.of(intent);
            }
        }
    }

    private static CommandIntent read(ResultSet resultSet) throws SQLException {
        return new CommandIntent(
            resultSet.getBytes("token_hash"),
            OperationId.of(JdbcValues.uuid(resultSet, "operation_id")),
            enumValue(CommandIntentType.class, resultSet.getString("intent_type"), "intent type"),
            enumValue(CommandIntentState.class, resultSet.getString("state"), "intent state"),
            JdbcValues.optionalUuid(resultSet, "owner_uuid"),
            enumValue(ActorType.class, resultSet.getString("actor_type"), "actor type"),
            JdbcValues.optionalUuid(resultSet, "actor_uuid"),
            JdbcValues.uuid(resultSet, "player_uuid"),
            JdbcValues.optionalUuid(resultSet, "target_uuid"),
            resultSet.getLong("amount"),
            OperationReason.of(resultSet.getString("reason_key")),
            optionalLong(resultSet, "observed_revision"),
            resultSet.getString("source_server_id"),
            JdbcValues.instant(resultSet, "created_at"),
            JdbcValues.instant(resultSet, "expires_at"),
            JdbcValues.optionalInstant(resultSet, "submitted_at"),
            JdbcValues.optionalInstant(resultSet, "resolved_at")
        );
    }

    private static Optional<Long> optionalLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? Optional.empty() : Optional.of(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw new PersistenceDataException("Unknown stored " + label + ": " + value, exception);
        }
    }
}
