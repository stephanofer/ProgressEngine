package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.ActorType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class OperationRepository {
    private static final int MYSQL_DUPLICATE_KEY = 1_062;

    private final Database database;
    private final DatabaseTables tables;

    public OperationRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<Optional<StoredOperation>> find(OperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return this.database.query(connection -> find(connection, operationId));
    }

    public OperationReservation reserve(Connection connection, OperationDraft draft) throws SQLException {
        Objects.requireNonNull(draft, "draft");
        String sql = "INSERT INTO " + this.tables.operations()
            + " (operation_id, fingerprint_version, request_fingerprint, type, status, player_uuid, related_player_uuid, requested_amount, "
            + "actor_type, actor_uuid, source_plugin, reason_key, server_id, metadata_json, result_version, result_json, created_at, completed_at) "
            + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, NULL)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            JdbcValues.setUuid(statement, index++, draft.operationId().value());
            statement.setInt(index++, draft.fingerprintVersion());
            statement.setBytes(index++, draft.requestFingerprint());
            statement.setString(index++, draft.type().name());
            JdbcValues.setUuid(statement, index++, draft.playerId());
            JdbcValues.setOptionalUuid(statement, index++, draft.relatedPlayerId());
            statement.setLong(index++, draft.requestedAmount());
            statement.setString(index++, draft.actor().type().name());
            setActorUuid(statement, index++, draft.actor());
            statement.setString(index++, draft.source().pluginName());
            statement.setString(index++, draft.reason().value());
            statement.setString(index++, draft.source().serverId());
            statement.setString(index++, draft.metadataJson());
            JdbcValues.setInstant(statement, index, draft.createdAt());
            statement.executeUpdate();
            StoredOperation stored = findRequired(connection, draft.operationId());
            return new OperationReservation.Reserved(stored);
        } catch (SQLException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
            return new OperationReservation.Existing(findRequired(connection, draft.operationId()));
        }
    }

    public StoredOperation complete(Connection connection, OperationCompletion completion) throws SQLException {
        Objects.requireNonNull(completion, "completion");
        String sql = "UPDATE " + this.tables.operations()
            + " SET status = ?, result_version = ?, result_json = ?, completed_at = ? "
            + "WHERE operation_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, completion.status().name());
            if (completion.payload().version().isPresent()) {
                statement.setInt(2, completion.payload().version().get());
                statement.setString(3, completion.payload().json().get());
            } else {
                statement.setNull(2, Types.SMALLINT);
                statement.setNull(3, Types.VARCHAR);
            }
            JdbcValues.setInstant(statement, 4, completion.completedAt());
            JdbcValues.setUuid(statement, 5, completion.operationId().value());
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new PersistenceDataException("Expected to complete one pending operation but updated " + updated);
            }
        }
        return findRequired(connection, completion.operationId());
    }

    Optional<StoredOperation> find(Connection connection, OperationId operationId) throws SQLException {
        String sql = "SELECT * FROM " + this.tables.operations() + " WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcValues.setUuid(statement, 1, operationId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                StoredOperation operation = readOperation(resultSet);
                if (resultSet.next()) {
                    throw new PersistenceDataException("More than one operation row found for " + operationId);
                }
                return Optional.of(operation);
            }
        }
    }

    StoredOperation findRequired(Connection connection, OperationId operationId) throws SQLException {
        return find(connection, operationId).orElseThrow(() -> new PersistenceDataException("Operation was not found: " + operationId));
    }

    private StoredOperation readOperation(ResultSet resultSet) throws SQLException {
        OperationId operationId = OperationId.of(JdbcValues.uuid(resultSet, "operation_id"));
        OperationType type = enumValue(OperationType.class, resultSet.getString("type"), "operation type");
        OperationStatus status = enumValue(OperationStatus.class, resultSet.getString("status"), "operation status");
        ActorType actorType = enumValue(ActorType.class, resultSet.getString("actor_type"), "actor type");
        Optional<UUID> actorUuid = JdbcValues.optionalUuid(resultSet, "actor_uuid");
        OperationActor actor = actorType == ActorType.PLAYER
            ? OperationActor.player(actorUuid.orElseThrow(() -> new PersistenceDataException("Stored player actor is missing actor_uuid")))
            : new OperationActor(actorType, Optional.empty());
        Integer resultVersion = nullableInteger(resultSet, "result_version");
        String resultJson = resultSet.getString("result_json");
        OperationResultPayload payload = resultVersion == null
            ? OperationResultPayload.empty()
            : OperationResultPayload.of(resultVersion, Objects.requireNonNull(resultJson, "result_json"));
        return new StoredOperation(
            operationId,
            resultSet.getInt("fingerprint_version"),
            resultSet.getBytes("request_fingerprint"),
            type,
            status,
            JdbcValues.uuid(resultSet, "player_uuid"),
            JdbcValues.optionalUuid(resultSet, "related_player_uuid"),
            resultSet.getLong("requested_amount"),
            actor,
            new OperationSource(resultSet.getString("source_plugin"), resultSet.getString("server_id")),
            OperationReason.of(resultSet.getString("reason_key")),
            resultSet.getString("metadata_json"),
            payload,
            JdbcValues.instant(resultSet, "created_at"),
            JdbcValues.optionalInstant(resultSet, "completed_at")
        );
    }

    private static void setActorUuid(PreparedStatement statement, int index, OperationActor actor) throws SQLException {
        if (actor.playerId().isPresent()) {
            JdbcValues.setUuid(statement, index, actor.playerId().get());
        } else {
            statement.setNull(index, Types.BINARY);
        }
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                return true;
            }
        }
        return false;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw new PersistenceDataException("Unknown stored " + label + ": " + value, exception);
        }
    }
}
