package com.stephanofer.progressengine.persistence;

import com.hera.craftkit.database.Database;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ProgressPersistence implements AutoCloseable {
    private final Database database;
    private final DatabaseTables tables;
    private final AccountRepository accounts;
    private final OperationRepository operations;
    private final LedgerRepository ledger;
    private final PlayerNameRepository playerNames;

    public ProgressPersistence(Database database) {
        this.database = Objects.requireNonNull(database, "database");
        this.tables = new DatabaseTables(database);
        this.accounts = new AccountRepository(database, this.tables);
        this.operations = new OperationRepository(database, this.tables);
        this.ledger = new LedgerRepository(database, this.tables);
        this.playerNames = new PlayerNameRepository(database, this.tables, this.accounts);
    }

    public CompletableFuture<Void> migrate() {
        return this.database.migrate();
    }

    public Database database() {
        return this.database;
    }

    public DatabaseTables tables() {
        return this.tables;
    }

    public AccountRepository accounts() {
        return this.accounts;
    }

    public OperationRepository operations() {
        return this.operations;
    }

    public LedgerRepository ledger() {
        return this.ledger;
    }

    public PlayerNameRepository playerNames() {
        return this.playerNames;
    }

    @Override
    public void close() {
        this.database.close();
    }
}
