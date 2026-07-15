package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.stephanofer.progressengine.api.operation.OperationId;
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

public final class LedgerRepository {
    private static final int MAX_PAGE_SIZE = 500;

    private final Database database;
    private final DatabaseTables tables;

    public LedgerRepository(Database database, DatabaseTables tables) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public CompletableFuture<LedgerPage> history(UUID playerId, int pageSize, Optional<LedgerCursor> cursor) {
        BinaryUuid.requireValid(playerId, "playerId");
        validatePageSize(pageSize);
        Objects.requireNonNull(cursor, "cursor");
        return this.database.query(connection -> history(connection, playerId, pageSize, cursor));
    }

    public CompletableFuture<List<StoredLedgerEntry>> findByOperation(OperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return this.database.query(connection -> findByOperation(connection, operationId));
    }

    public void append(Connection connection, List<LedgerEntryDraft> entries) throws SQLException {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + this.tables.ledger()
            + " (operation_id, player_uuid, related_player_uuid, delta, balance_before, balance_after, revision, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LedgerEntryDraft entry : entries) {
                int index = 1;
                JdbcValues.setUuid(statement, index++, entry.operationId().value());
                JdbcValues.setUuid(statement, index++, entry.playerId());
                JdbcValues.setOptionalUuid(statement, index++, entry.relatedPlayerId());
                statement.setLong(index++, entry.delta());
                statement.setLong(index++, entry.balanceBefore());
                statement.setLong(index++, entry.balanceAfter());
                statement.setLong(index++, entry.revision());
                JdbcValues.setInstant(statement, index, entry.createdAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private LedgerPage history(Connection connection, UUID playerId, int pageSize, Optional<LedgerCursor> cursor) throws SQLException {
        String sql;
        if (cursor.isPresent()) {
            sql = "SELECT * FROM " + this.tables.ledger()
                + " WHERE player_uuid = ? AND (created_at < ? OR (created_at = ? AND ledger_id < ?)) "
                + "ORDER BY created_at DESC, ledger_id DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM " + this.tables.ledger()
                + " WHERE player_uuid = ? ORDER BY created_at DESC, ledger_id DESC LIMIT ?";
        }
        int limit = pageSize + 1;
        List<StoredLedgerEntry> loaded = new ArrayList<>(limit);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            JdbcValues.setUuid(statement, index++, playerId);
            if (cursor.isPresent()) {
                LedgerCursor value = cursor.get();
                JdbcValues.setInstant(statement, index++, value.createdAt());
                JdbcValues.setInstant(statement, index++, value.createdAt());
                statement.setLong(index++, value.ledgerId());
            }
            statement.setInt(index, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    loaded.add(readLedger(resultSet));
                }
            }
        }
        boolean hasNext = loaded.size() > pageSize;
        List<StoredLedgerEntry> pageEntries = hasNext ? loaded.subList(0, pageSize) : loaded;
        Optional<LedgerCursor> nextCursor = Optional.empty();
        if (hasNext) {
            StoredLedgerEntry last = pageEntries.get(pageEntries.size() - 1);
            nextCursor = Optional.of(new LedgerCursor(last.createdAt(), last.ledgerId()));
        }
        return new LedgerPage(pageEntries, nextCursor);
    }

    private List<StoredLedgerEntry> findByOperation(Connection connection, OperationId operationId) throws SQLException {
        String sql = "SELECT * FROM " + this.tables.ledger() + " WHERE operation_id = ? ORDER BY ledger_id ASC";
        List<StoredLedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcValues.setUuid(statement, 1, operationId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(readLedger(resultSet));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static StoredLedgerEntry readLedger(ResultSet resultSet) throws SQLException {
        return new StoredLedgerEntry(
            resultSet.getLong("ledger_id"),
            OperationId.of(JdbcValues.uuid(resultSet, "operation_id")),
            JdbcValues.uuid(resultSet, "player_uuid"),
            JdbcValues.optionalUuid(resultSet, "related_player_uuid"),
            resultSet.getLong("delta"),
            resultSet.getLong("balance_before"),
            resultSet.getLong("balance_after"),
            resultSet.getLong("revision"),
            JdbcValues.instant(resultSet, "created_at")
        );
    }

    private static void validatePageSize(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
