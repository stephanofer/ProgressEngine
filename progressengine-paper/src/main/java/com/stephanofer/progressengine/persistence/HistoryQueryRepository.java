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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HistoryQueryRepository {
    private static final int MAX_PAGE_SIZE = 100;
    private final Database database;
    private final DatabaseTables tables;

    public HistoryQueryRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<HistoryPage> history(UUID playerId, int pageSize, Optional<LedgerCursor> cursor) {
        BinaryUuid.requireValid(playerId, "playerId");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        Objects.requireNonNull(cursor, "cursor");
        return this.database.query(connection -> history(connection, playerId, pageSize, cursor));
    }

    private HistoryPage history(Connection connection, UUID playerId, int pageSize, Optional<LedgerCursor> cursor) throws SQLException {
        String base = "SELECT l.ledger_id, l.operation_id, l.player_uuid, l.related_player_uuid, l.delta, l.balance_before, "
            + "l.balance_after, l.revision, l.created_at, o.type, o.actor_type, o.actor_uuid, o.source_plugin, o.server_id, o.reason_key "
            + "FROM " + this.tables.ledger() + " l JOIN " + this.tables.operations() + " o ON o.operation_id = l.operation_id "
            + "WHERE l.player_uuid = ? ";
        String sql = cursor.isPresent()
            ? base + "AND (l.created_at < ? OR (l.created_at = ? AND l.ledger_id < ?)) ORDER BY l.created_at DESC, l.ledger_id DESC LIMIT ?"
            : base + "ORDER BY l.created_at DESC, l.ledger_id DESC LIMIT ?";
        int limit = pageSize + 1;
        List<HistoryEntry> loaded = new ArrayList<>(limit);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            JdbcValues.setUuid(statement, index++, playerId);
            if (cursor.isPresent()) {
                LedgerCursor value = cursor.orElseThrow();
                JdbcValues.setInstant(statement, index++, value.createdAt());
                JdbcValues.setInstant(statement, index++, value.createdAt());
                statement.setLong(index++, value.ledgerId());
            }
            statement.setInt(index, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    loaded.add(read(resultSet));
                }
            }
        }
        boolean hasNext = loaded.size() > pageSize;
        List<HistoryEntry> entries = hasNext ? loaded.subList(0, pageSize) : loaded;
        Optional<LedgerCursor> nextCursor = Optional.empty();
        if (hasNext) {
            HistoryEntry last = entries.get(entries.size() - 1);
            nextCursor = Optional.of(new LedgerCursor(last.createdAt(), last.ledgerId()));
        }
        return new HistoryPage(entries, nextCursor);
    }

    private static HistoryEntry read(ResultSet resultSet) throws SQLException {
        ActorType actorType = enumValue(ActorType.class, resultSet.getString("actor_type"), "actor type");
        Optional<UUID> actorUuid = JdbcValues.optionalUuid(resultSet, "actor_uuid");
        OperationActor actor = actorType == ActorType.PLAYER
            ? OperationActor.player(actorUuid.orElseThrow(() -> new PersistenceDataException("Stored player actor is missing UUID")))
            : new OperationActor(actorType, Optional.empty());
        return new HistoryEntry(
            resultSet.getLong("ledger_id"),
            OperationId.of(JdbcValues.uuid(resultSet, "operation_id")),
            enumValue(OperationType.class, resultSet.getString("type"), "operation type"),
            JdbcValues.uuid(resultSet, "player_uuid"),
            JdbcValues.optionalUuid(resultSet, "related_player_uuid"),
            resultSet.getLong("delta"),
            resultSet.getLong("balance_before"),
            resultSet.getLong("balance_after"),
            resultSet.getLong("revision"),
            OperationReason.of(resultSet.getString("reason_key")),
            actor,
            new OperationSource(resultSet.getString("source_plugin"), resultSet.getString("server_id")),
            JdbcValues.instant(resultSet, "created_at")
        );
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw new PersistenceDataException("Unknown stored " + label + ": " + value, exception);
        }
    }
}
