package com.stephanofer.progressengine.service;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MutationFailureLogger {
    private final Logger logger;

    MutationFailureLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void log(OperationSource source, OperationId operationId, OperationType type, List<UUID> playerIds, Throwable failure) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerIds, "playerIds");
        Objects.requireNonNull(failure, "failure");

        SQLException sql = findSqlException(failure);
        String sqlDetails = sql == null
            ? "sqlState=none sqlErrorCode=none"
            : "sqlState=" + sql.getSQLState() + " sqlErrorCode=" + sql.getErrorCode();
        this.logger.log(
            Level.WARNING,
            "ProgressEngine mutation failed operationId=" + operationId
                + " type=" + type
                + " players=" + playerIds
                + " sourcePlugin=" + source.pluginName()
                + " serverId=" + source.serverId()
                + ' ' + sqlDetails,
            failure
        );
    }

    private static SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }
}
