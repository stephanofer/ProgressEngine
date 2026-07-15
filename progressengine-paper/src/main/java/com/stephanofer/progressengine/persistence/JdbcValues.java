package com.stephanofer.progressengine.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class JdbcValues {
    private JdbcValues() {
    }

    static void setUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
        statement.setBytes(index, BinaryUuid.encode(uuid));
    }

    static void setOptionalUuid(PreparedStatement statement, int index, Optional<UUID> uuid) throws SQLException {
        if (uuid.isPresent()) {
            setUuid(statement, index, uuid.get());
        } else {
            statement.setNull(index, Types.BINARY);
        }
    }

    static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return BinaryUuid.decode(resultSet.getBytes(column));
    }

    static Optional<UUID> optionalUuid(ResultSet resultSet, String column) throws SQLException {
        byte[] bytes = resultSet.getBytes(column);
        if (resultSet.wasNull()) {
            return Optional.empty();
        }
        return Optional.of(BinaryUuid.decode(bytes));
    }

    static void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(instant));
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        if (timestamp == null) {
            throw new PersistenceDataException("Stored timestamp is null for column " + column);
        }
        return timestamp.toInstant();
    }

    static Optional<Instant> optionalInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        if (timestamp == null || resultSet.wasNull()) {
            return Optional.empty();
        }
        return Optional.of(timestamp.toInstant());
    }
}
