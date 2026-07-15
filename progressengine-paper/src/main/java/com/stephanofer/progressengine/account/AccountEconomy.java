package com.stephanofer.progressengine.account;

import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryPolicy;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.request.CreditRequest;
import com.stephanofer.progressengine.api.request.DebitRequest;
import com.stephanofer.progressengine.api.request.ResetBalanceRequest;
import com.stephanofer.progressengine.api.request.SetBalanceRequest;
import com.stephanofer.progressengine.api.result.CreditResult;
import com.stephanofer.progressengine.api.result.DebitResult;
import com.stephanofer.progressengine.api.result.ResetBalanceResult;
import com.stephanofer.progressengine.api.result.SetBalanceResult;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.persistence.LedgerEntryDraft;
import com.stephanofer.progressengine.persistence.OperationCompletion;
import com.stephanofer.progressengine.persistence.OperationDraft;
import com.stephanofer.progressengine.persistence.OperationReservation;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceDataException;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.persistence.StoredAccount;
import com.stephanofer.progressengine.persistence.StoredOperation;
import com.stephanofer.progressengine.transaction.AccountMutationSequencer;
import com.stephanofer.progressengine.transaction.DecodedOperationResult;
import com.stephanofer.progressengine.transaction.OperationFingerprint;
import com.stephanofer.progressengine.transaction.OperationResultCodec;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

public final class AccountEconomy {
    private static final TransactionOptions MUTATION_OPTIONS = TransactionOptions.builder()
        .isolation(TransactionIsolation.READ_COMMITTED)
        .retryPolicy(TransactionRetryPolicy.mysqlTransient())
        .build();

    private final ProgressPersistence persistence;
    private final OperationSource source;
    private final LongSupplier maximumBalance;
    private final AccountMutationSequencer sequencer;

    public AccountEconomy(ProgressPersistence persistence, OperationSource source, LongSupplier maximumBalance) {
        this(persistence, source, maximumBalance, new AccountMutationSequencer());
    }

    public AccountEconomy(ProgressPersistence persistence, OperationSource source, LongSupplier maximumBalance,
                          AccountMutationSequencer sequencer) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.source = Objects.requireNonNull(source, "source");
        this.maximumBalance = Objects.requireNonNull(maximumBalance, "maximumBalance");
        this.sequencer = Objects.requireNonNull(sequencer, "sequencer");
    }

    public CompletableFuture<CreditResult> credit(CreditRequest request) {
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.credit(request);
        return execute(intent).thenApply(outcome -> toCreditResult(request.operationId(), outcome));
    }

    public CompletableFuture<DebitResult> debit(DebitRequest request) {
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.debit(request);
        return execute(intent).thenApply(outcome -> toDebitResult(request.operationId(), outcome));
    }

    public CompletableFuture<SetBalanceResult> setBalance(SetBalanceRequest request) {
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.set(request);
        return execute(intent).thenApply(outcome -> toSetBalanceResult(request.operationId(), outcome));
    }

    public CompletableFuture<ResetBalanceResult> resetBalance(ResetBalanceRequest request) {
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.reset(request);
        return execute(intent).thenApply(outcome -> toResetBalanceResult(request.operationId(), outcome));
    }

    public AccountMutationSequencer sequencer() {
        return this.sequencer;
    }

    private CompletableFuture<AccountMutationOutcome> execute(AccountMutationIntent intent) {
        long maximum = this.maximumBalance.getAsLong();
        if (maximum < 1L) {
            return CompletableFuture.failedFuture(new IllegalStateException("maximumBalance must be positive"));
        }
        return this.sequencer.submit(List.of(intent.playerId()), () -> this.persistence.database().transaction(
            MUTATION_OPTIONS,
            connection -> executeTransaction(connection, intent, maximum)
        ));
    }

    private AccountMutationOutcome executeTransaction(Connection connection, AccountMutationIntent intent, long maximumBalance)
        throws SQLException {
        Instant timestamp = databaseTimestamp(connection);
        OperationDraft draft = new OperationDraft(
            intent.operationId(),
            OperationFingerprint.CURRENT_VERSION,
            OperationFingerprint.current(
                intent.type(),
                intent.playerId(),
                Optional.empty(),
                intent.requestedAmount(),
                intent.reason(),
                intent.actor(),
                this.source.pluginName()
            ),
            intent.type(),
            intent.playerId(),
            Optional.empty(),
            intent.requestedAmount(),
            intent.actor(),
            this.source,
            intent.reason(),
            intent.metadata(),
            timestamp
        );

        OperationReservation reservation = this.persistence.operations().reserve(connection, draft);
        if (reservation instanceof OperationReservation.Existing existing) {
            return replayOrConflict(existing.operation(), intent);
        }

        this.persistence.accounts().createOrLoad(connection, intent.playerId(), timestamp);
        StoredAccount account = this.persistence.accounts().lock(connection, intent.playerId());
        AccountMutationDecision decision = resolve(intent, account, maximumBalance);

        if (decision.status() != OperationStatus.SUCCESS) {
            this.persistence.operations().complete(connection, new OperationCompletion(
                intent.operationId(),
                decision.status(),
                OperationResultCodec.rejectionPayload(),
                timestamp
            ));
            return new AccountMutationOutcome.Rejected(intent.operationId(), decision.status(), ReplayStatus.ORIGINAL);
        }

        BalanceChange change = BalanceChange.single(
            intent.playerId(),
            Math.subtractExact(decision.balanceAfter(), account.balance()),
            account.balance(),
            decision.balanceAfter(),
            decision.revisionAfter()
        );
        this.persistence.accounts().updateBalance(connection, intent.playerId(), change.balanceAfter(), change.revision(), timestamp);
        this.persistence.ledger().append(connection, List.of(new LedgerEntryDraft(
            intent.operationId(),
            intent.playerId(),
            Optional.empty(),
            change.delta(),
            change.balanceBefore(),
            change.balanceAfter(),
            change.revision(),
            timestamp
        )));
        this.persistence.operations().complete(connection, new OperationCompletion(
            intent.operationId(),
            OperationStatus.SUCCESS,
            OperationResultCodec.successPayload(change),
            timestamp
        ));

        OperationReceipt receipt = new OperationReceipt(
            intent.operationId(),
            intent.type(),
            intent.reason(),
            intent.actor(),
            this.source,
            intent.metadata(),
            List.of(change),
            timestamp
        );
        return new AccountMutationOutcome.Success(receipt, ReplayStatus.ORIGINAL);
    }

    private AccountMutationOutcome replayOrConflict(StoredOperation operation, AccountMutationIntent intent) {
        boolean matches = OperationFingerprint.matches(
            operation.requestFingerprint(),
            operation.fingerprintVersion(),
            intent.type(),
            intent.playerId(),
            Optional.empty(),
            intent.requestedAmount(),
            intent.reason(),
            intent.actor(),
            this.source.pluginName()
        );
        if (!matches) {
            return new AccountMutationOutcome.Conflict(intent.operationId());
        }
        DecodedOperationResult decoded = OperationResultCodec.decode(operation, ReplayStatus.REPLAYED);
        if (decoded instanceof DecodedOperationResult.Success success) {
            return new AccountMutationOutcome.Success(success.receipt(), success.replayStatus());
        }
        DecodedOperationResult.Rejected rejected = (DecodedOperationResult.Rejected) decoded;
        return new AccountMutationOutcome.Rejected(rejected.operationId(), rejected.status(), rejected.replayStatus());
    }

    private static AccountMutationDecision resolve(AccountMutationIntent intent, StoredAccount account, long maximumBalance) {
        return switch (intent.type()) {
            case CREDIT -> AccountBalanceMath.credit(account.balance(), account.revision(), intent.requestedAmount(), maximumBalance);
            case DEBIT -> AccountBalanceMath.debit(account.balance(), account.revision(), intent.requestedAmount());
            case SET_BALANCE -> AccountBalanceMath.set(account.revision(), intent.requestedAmount(), maximumBalance);
            case RESET_BALANCE -> AccountBalanceMath.reset(account.revision());
            case AWARD, TRANSFER -> throw new IllegalArgumentException("Unsupported one-account mutation type " + intent.type());
        };
    }

    private static Instant databaseTimestamp(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery("SELECT CURRENT_TIMESTAMP(6)")) {
            if (!resultSet.next()) {
                throw new PersistenceDataException("MySQL did not return CURRENT_TIMESTAMP(6)");
            }
            java.sql.Timestamp timestamp = resultSet.getTimestamp(1);
            if (timestamp == null) {
                throw new PersistenceDataException("MySQL returned a null CURRENT_TIMESTAMP(6)");
            }
            return timestamp.toInstant();
        }
    }

    private static CreditResult toCreditResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new CreditResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.Success success) {
            requireType(success.receipt(), OperationType.CREDIT);
            return new CreditResult.Success(success.receipt(), success.replayStatus());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        if (rejected.status() == OperationStatus.BALANCE_LIMIT_EXCEEDED) {
            return new CreditResult.BalanceLimitExceeded(operationId, rejected.replayStatus());
        }
        throw unexpectedOutcome(OperationType.CREDIT, rejected.status());
    }

    private static DebitResult toDebitResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new DebitResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.Success success) {
            requireType(success.receipt(), OperationType.DEBIT);
            return new DebitResult.Success(success.receipt(), success.replayStatus());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        if (rejected.status() == OperationStatus.INSUFFICIENT_FUNDS) {
            return new DebitResult.InsufficientFunds(operationId, rejected.replayStatus());
        }
        throw unexpectedOutcome(OperationType.DEBIT, rejected.status());
    }

    private static SetBalanceResult toSetBalanceResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new SetBalanceResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.Success success) {
            requireType(success.receipt(), OperationType.SET_BALANCE);
            return new SetBalanceResult.Success(success.receipt(), success.replayStatus());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        if (rejected.status() == OperationStatus.BALANCE_LIMIT_EXCEEDED) {
            return new SetBalanceResult.BalanceLimitExceeded(operationId, rejected.replayStatus());
        }
        throw unexpectedOutcome(OperationType.SET_BALANCE, rejected.status());
    }

    private static ResetBalanceResult toResetBalanceResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new ResetBalanceResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.Success success) {
            requireType(success.receipt(), OperationType.RESET_BALANCE);
            return new ResetBalanceResult.Success(success.receipt(), success.replayStatus());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        throw unexpectedOutcome(OperationType.RESET_BALANCE, rejected.status());
    }

    private static void requireType(OperationReceipt receipt, OperationType expected) {
        if (receipt.type() != expected) {
            throw new PersistenceDataException("Replayed receipt type " + receipt.type() + " does not match " + expected);
        }
    }

    private static PersistenceDataException unexpectedOutcome(OperationType type, OperationStatus status) {
        return new PersistenceDataException("Unexpected stored outcome " + status + " for " + type);
    }

    private sealed interface AccountMutationOutcome permits AccountMutationOutcome.Success, AccountMutationOutcome.Rejected,
        AccountMutationOutcome.Conflict {
        record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements AccountMutationOutcome {
            public Success {
                if (receipt == null) throw new NullPointerException("receipt cannot be null");
                if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
            }
        }

        record Rejected(OperationId operationId, OperationStatus status, ReplayStatus replayStatus) implements AccountMutationOutcome {
            public Rejected {
                if (operationId == null) throw new NullPointerException("operationId cannot be null");
                if (status == null) throw new NullPointerException("status cannot be null");
                if (status == OperationStatus.PENDING || status == OperationStatus.SUCCESS) {
                    throw new IllegalArgumentException("status must be a durable rejection");
                }
                if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
            }
        }

        record Conflict(OperationId operationId) implements AccountMutationOutcome {
            public Conflict {
                if (operationId == null) throw new NullPointerException("operationId cannot be null");
            }
        }
    }
}
