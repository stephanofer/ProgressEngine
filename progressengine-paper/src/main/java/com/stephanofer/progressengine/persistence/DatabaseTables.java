package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseException;
import java.util.List;
import java.util.Objects;

public final class DatabaseTables {
    public static final String ACCOUNTS = "progress_accounts";
    public static final String OPERATIONS = "progress_operations";
    public static final String LEDGER = "progress_ledger";
    public static final String PLAYER_NAMES = "progress_player_names";
    public static final String FLYWAY_HISTORY = "flyway_schema_history";
    private static final int MYSQL_IDENTIFIER_LIMIT = 64;
    private static final List<String> ALL = List.of(ACCOUNTS, OPERATIONS, LEDGER, PLAYER_NAMES, FLYWAY_HISTORY);

    private final Database database;

    public DatabaseTables(Database database) {
        this.database = Objects.requireNonNull(database, "database");
        validatePrefix(database.tablePrefix());
    }

    public String accounts() {
        return this.database.table(ACCOUNTS);
    }

    public String operations() {
        return this.database.table(OPERATIONS);
    }

    public String ledger() {
        return this.database.table(LEDGER);
    }

    public String playerNames() {
        return this.database.table(PLAYER_NAMES);
    }

    public static void validatePrefix(String tablePrefix) {
        Objects.requireNonNull(tablePrefix, "tablePrefix");
        for (String table : ALL) {
            if (tablePrefix.length() + table.length() > MYSQL_IDENTIFIER_LIMIT) {
                throw new DatabaseException("Database table prefix is too long for ProgressEngine table " + table);
            }
        }
    }
}
