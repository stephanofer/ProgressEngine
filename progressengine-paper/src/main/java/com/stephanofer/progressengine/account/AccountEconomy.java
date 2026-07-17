package com.stephanofer.progressengine.account;

import com.hera.craftkit.database.TransactionIsolation;
import com.hera.craftkit.database.TransactionOptions;
import com.hera.craftkit.database.TransactionRetryPolicy;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.request.CreditRequest;
import com.stephanofer.progressengine.api.request.DebitRequest;
import com.stephanofer.progressengine.api.request.ResetBalanceRequest;
import com.stephanofer.progressengine.api.request.SetBalanceRequest;
import com.stephanofer.progressengine.api.request.TransferRequest;
import com.stephanofer.progressengine.api.result.AwardCalculation;
import com.stephanofer.progressengine.api.result.AwardResult;
import com.stephanofer.progressengine.api.result.CreditResult;
import com.stephanofer.progressengine.api.result.DebitResult;
import com.stephanofer.progressengine.api.result.ResetBalanceResult;
import com.stephanofer.progressengine.api.result.SetBalanceResult;
import com.stephanofer.progressengine.api.result.TransferResult;
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
import com.stephanofer.progressengine.transaction.CanonicalAccountOrder;
import com.stephanofer.progressengine.transaction.DecodedOperationResult;
import com.stephanofer.progressengine.transaction.OperationFingerprint;
import com.stephanofer.progressengine.transaction.OperationResultCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AccountPostCommitPublisher postCommitPublisher;

    public AccountEconomy(ProgressPersistence persistence, OperationSource source, LongSupplier maximumBalance) {
        this(persistence, source, maximumBalance, new AccountMutationSequencer(), AccountPostCommitPublisher.noop());
    }

    public AccountEconomy(ProgressPersistence persistence, OperationSource source, LongSupplier maximumBalance,
                           AccountMutationSequencer sequencer) {
        this(persistence, source, maximumBalance, sequencer, AccountPostCommitPublisher.noop());
    }

    public AccountEconomy(ProgressPersistence persistence, OperationSource source, LongSupplier maximumBalance,
                          AccountMutationSequencer sequencer, AccountPostCommitPublisher postCommitPublisher) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.source = Objects.requireNonNull(source, "source");
        this.maximumBalance = Objects.requireNonNull(maximumBalance, "maximumBalance");
        this.sequencer = Objects.requireNonNull(sequencer, "sequencer");
        this.postCommitPublisher = AccountPostCommitPublisher.require(postCommitPublisher);
    }

    public CompletableFuture<CreditResult> credit(CreditRequest request) {
        return credit(this.source, request);
    }

    public CompletableFuture<Optional<AwardResult>> findAwardResult(OperationSource source, AwardRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        return this.persistence.operations().find(request.operationId())
            .thenApply(operation -> operation.map(stored -> toAwardResult(source, request, stored, ReplayStatus.REPLAYED)));
    }

    public CompletableFuture<AwardResult> cancelAward(OperationSource source, AwardRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        return this.sequencer.submit(List.of(request.playerId()), () -> this.persistence.database().transaction(
            MUTATION_OPTIONS,
            connection -> executeAwardCancellationTransaction(connection, source, request)
        )).thenApply(outcome -> toAwardResult(request.operationId(), outcome));
    }

    public CompletableFuture<AwardResult> award(OperationSource source, AwardRequest request, AwardCalculation calculation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(calculation, "calculation");
        return award(source, request, calculation, this.maximumBalance.getAsLong());
    }

    public CompletableFuture<AwardResult> award(OperationSource source, AwardRequest request, AwardCalculation calculation, long maximumBalance) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(calculation, "calculation");
        if (maximumBalance < 1L) {
            return CompletableFuture.failedFuture(new IllegalStateException("maximumBalance must be positive"));
        }
        return this.sequencer.submit(List.of(request.playerId()), () -> this.persistence.database().transaction(
            MUTATION_OPTIONS,
            connection -> executeAwardTransaction(connection, source, request, calculation, maximumBalance)
        ).thenCompose(this::publishOriginalSuccess)).thenApply(outcome -> toAwardResult(request.operationId(), outcome));
    }

    public CompletableFuture<CreditResult> credit(OperationSource source, CreditRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.credit(request);
        return execute(source, intent).thenApply(outcome -> toCreditResult(request.operationId(), outcome));
    }

    public CompletableFuture<DebitResult> debit(DebitRequest request) {
        return debit(this.source, request);
    }

    public CompletableFuture<DebitResult> debit(OperationSource source, DebitRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.debit(request);
        return execute(source, intent).thenApply(outcome -> toDebitResult(request.operationId(), outcome));
    }

    public CompletableFuture<SetBalanceResult> setBalance(SetBalanceRequest request) {
        return setBalance(this.source, request);
    }

    public CompletableFuture<SetBalanceResult> setBalance(OperationSource source, SetBalanceRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.set(request);
        return execute(source, intent).thenApply(outcome -> toSetBalanceResult(request.operationId(), outcome));
    }

    public CompletableFuture<ResetBalanceResult> resetBalance(ResetBalanceRequest request) {
        return resetBalance(this.source, request);
    }

    public CompletableFuture<ResetBalanceResult> resetBalance(OperationSource source, ResetBalanceRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        AccountMutationIntent intent = AccountMutationIntent.reset(request);
        return execute(source, intent).thenApply(outcome -> toResetBalanceResult(request.operationId(), outcome));
    }

    public CompletableFuture<TransferResult> transfer(TransferRequest request) {
        return transfer(this.source, request);
    }

    public CompletableFuture<TransferResult> transfer(OperationSource source, TransferRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        if (request.senderId().equals(request.receiverId())) {
            return CompletableFuture.completedFuture(new TransferResult.SelfTransferRejected(request.operationId()));
        }
        long maximum = this.maximumBalance.getAsLong();
        if (maximum < 1L) {
            return CompletableFuture.failedFuture(new IllegalStateException("maximumBalance must be positive"));
        }
        List<UUID> orderedIds = CanonicalAccountOrder.sort(List.of(request.senderId(), request.receiverId()));
        return this.sequencer.submit(orderedIds, () -> this.persistence.database().transaction(
            MUTATION_OPTIONS,
            connection -> executeTransferTransaction(connection, source, request, maximum, orderedIds)
        ).thenCompose(this::publishOriginalSuccess)).thenApply(outcome -> toTransferResult(request.operationId(), outcome));
    }

    public AccountMutationSequencer sequencer() {
        return this.sequencer;
    }

    private CompletableFuture<AccountMutationOutcome> execute(OperationSource source, AccountMutationIntent intent) {
        long maximum = this.maximumBalance.getAsLong();
        if (maximum < 1L) {
            return CompletableFuture.failedFuture(new IllegalStateException("maximumBalance must be positive"));
        }
        return this.sequencer.submit(List.of(intent.playerId()), () -> this.persistence.database().transaction(
            MUTATION_OPTIONS,
            connection -> executeTransaction(connection, source, intent, maximum)
        ).thenCompose(this::publishOriginalSuccess));
    }

    private AccountMutationOutcome executeTransaction(Connection connection, OperationSource source, AccountMutationIntent intent, long maximumBalance)
        throws SQLException {
        Instant timestamp = databaseTimestamp(connection);
        OperationDraft draft = new OperationDraft(
            intent.operationId(),
            OperationFingerprint.CURRENT_VERSION,
            OperationFingerprint.current(
                intent.type(),
                intent.playerId(),
                Optional.empty(),
                Optional.empty(),
                intent.requestedAmount(),
                intent.reason(),
                intent.actor(),
                source.pluginName()
            ),
            intent.type(),
            intent.playerId(),
            Optional.empty(),
            Optional.empty(),
            intent.requestedAmount(),
            intent.actor(),
            source,
            intent.reason(),
            intent.metadata(),
            timestamp
        );

        OperationReservation reservation = this.persistence.operations().reserve(connection, draft);
        if (reservation instanceof OperationReservation.Existing existing) {
            return replayOrConflict(
                existing.operation(),
                intent.type(),
                intent.playerId(),
                Optional.empty(),
                intent.requestedAmount(),
                intent.reason(),
                intent.actor(),
                source
            );
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
            source,
            intent.metadata(),
            List.of(change),
            timestamp
        );
        return new AccountMutationOutcome.Success(receipt, ReplayStatus.ORIGINAL);
    }

    private AccountMutationOutcome executeAwardCancellationTransaction(Connection connection, OperationSource source, AwardRequest request)
        throws SQLException {
        Instant timestamp = databaseTimestamp(connection);
        OperationDraft draft = awardDraft(source, request, timestamp);
        OperationReservation reservation = this.persistence.operations().reserve(connection, draft);
        if (reservation instanceof OperationReservation.Existing existing) {
            return awardReplayOrConflict(source, request, existing.operation(), ReplayStatus.REPLAYED);
        }
        this.persistence.operations().complete(connection, new OperationCompletion(
            request.operationId(),
            OperationStatus.CANCELLED,
            OperationResultCodec.rejectionPayload(),
            timestamp
        ));
        return new AccountMutationOutcome.Rejected(request.operationId(), OperationStatus.CANCELLED, ReplayStatus.ORIGINAL);
    }

    private AccountMutationOutcome executeAwardTransaction(Connection connection, OperationSource source, AwardRequest request,
                                                          AwardCalculation calculation, long maximumBalance) throws SQLException {
        Instant timestamp = databaseTimestamp(connection);
        OperationDraft draft = awardDraft(source, request, timestamp);
        OperationReservation reservation = this.persistence.operations().reserve(connection, draft);
        if (reservation instanceof OperationReservation.Existing existing) {
            return awardReplayOrConflict(source, request, existing.operation(), ReplayStatus.REPLAYED);
        }

        if (calculation.finalAmount() == 0L) {
            this.persistence.operations().complete(connection, new OperationCompletion(
                request.operationId(),
                OperationStatus.NO_POINTS_AWARDED,
                OperationResultCodec.awardCalculationPayload(calculation),
                timestamp
            ));
            return new AccountMutationOutcome.AwardNoMovement(request.operationId(), OperationStatus.NO_POINTS_AWARDED, calculation, ReplayStatus.ORIGINAL);
        }

        if (exceedsLong(calculation) || calculation.finalAmount() > maximumBalance) {
            return completeAwardLimitExceeded(connection, request.operationId(), calculation, timestamp);
        }

        this.persistence.accounts().createOrLoad(connection, request.playerId(), timestamp);
        StoredAccount account = this.persistence.accounts().lock(connection, request.playerId());
        long balanceAfter;
        try {
            balanceAfter = Math.addExact(account.balance(), calculation.finalAmount());
        } catch (ArithmeticException exception) {
            return completeAwardLimitExceeded(connection, request.operationId(), calculation, timestamp);
        }
        if (balanceAfter > maximumBalance) {
            return completeAwardLimitExceeded(connection, request.operationId(), calculation, timestamp);
        }

        BalanceChange change = BalanceChange.single(
            request.playerId(),
            Math.subtractExact(balanceAfter, account.balance()),
            account.balance(),
            balanceAfter,
            Math.addExact(account.revision(), 1L)
        );
        this.persistence.accounts().updateBalance(connection, request.playerId(), change.balanceAfter(), change.revision(), timestamp);
        this.persistence.ledger().append(connection, List.of(new LedgerEntryDraft(
            request.operationId(),
            request.playerId(),
            Optional.empty(),
            change.delta(),
            change.balanceBefore(),
            change.balanceAfter(),
            change.revision(),
            timestamp
        )));
        this.persistence.operations().complete(connection, new OperationCompletion(
            request.operationId(),
            OperationStatus.SUCCESS,
            OperationResultCodec.awardSuccessPayload(change, calculation),
            timestamp
        ));

        OperationReceipt receipt = new OperationReceipt(
            request.operationId(),
            OperationType.AWARD,
            request.reason(),
            request.actor(),
            source,
            request.metadata(),
            List.of(change),
            timestamp
        );
        return new AccountMutationOutcome.AwardSuccess(receipt, calculation, ReplayStatus.ORIGINAL);
    }

    private OperationDraft awardDraft(OperationSource source, AwardRequest request, Instant timestamp) {
        return new OperationDraft(
            request.operationId(),
            OperationFingerprint.CURRENT_VERSION,
            OperationFingerprint.current(
                OperationType.AWARD,
                request.playerId(),
                Optional.empty(),
                request.gameId(),
                request.baseAmount(),
                request.reason(),
                request.actor(),
                source.pluginName()
            ),
            OperationType.AWARD,
            request.playerId(),
            Optional.empty(),
            request.gameId(),
            request.baseAmount(),
            request.actor(),
            source,
            request.reason(),
            request.metadata(),
            timestamp
        );
    }

    private AccountMutationOutcome executeTransferTransaction(Connection connection, OperationSource source, TransferRequest request, long maximumBalance,
                                                             List<UUID> orderedIds) throws SQLException {
        Instant timestamp = databaseTimestamp(connection);
        OperationDraft draft = new OperationDraft(
            request.operationId(),
            OperationFingerprint.CURRENT_VERSION,
            OperationFingerprint.current(
                OperationType.TRANSFER,
                request.senderId(),
                Optional.of(request.receiverId()),
                Optional.empty(),
                request.amount(),
                request.reason(),
                request.actor(),
                source.pluginName()
            ),
            OperationType.TRANSFER,
            request.senderId(),
            Optional.of(request.receiverId()),
            Optional.empty(),
            request.amount(),
            request.actor(),
            source,
            request.reason(),
            request.metadata(),
            timestamp
        );

        OperationReservation reservation = this.persistence.operations().reserve(connection, draft);
        if (reservation instanceof OperationReservation.Existing existing) {
            return replayOrConflict(
                existing.operation(),
                OperationType.TRANSFER,
                request.senderId(),
                Optional.of(request.receiverId()),
                request.amount(),
                request.reason(),
                request.actor(),
                source
            );
        }

        for (UUID playerId : orderedIds) {
            this.persistence.accounts().createOrLoad(connection, playerId, timestamp);
        }
        StoredAccount first = this.persistence.accounts().lock(connection, orderedIds.get(0));
        StoredAccount second = this.persistence.accounts().lock(connection, orderedIds.get(1));
        StoredAccount sender = first.playerId().equals(request.senderId()) ? first : second;
        StoredAccount receiver = first.playerId().equals(request.receiverId()) ? first : second;

        if (request.expectedSenderRevision().isPresent()
            && sender.revision() != request.expectedSenderRevision().getAsLong()) {
            this.persistence.operations().complete(connection, new OperationCompletion(
                request.operationId(), OperationStatus.STALE_CONFIRMATION, OperationResultCodec.rejectionPayload(), timestamp
            ));
            return new AccountMutationOutcome.Rejected(request.operationId(), OperationStatus.STALE_CONFIRMATION, ReplayStatus.ORIGINAL);
        }

        if (sender.balance() < request.amount()) {
            this.persistence.operations().complete(connection, new OperationCompletion(
                request.operationId(),
                OperationStatus.INSUFFICIENT_FUNDS,
                OperationResultCodec.rejectionPayload(),
                timestamp
            ));
            return new AccountMutationOutcome.Rejected(request.operationId(), OperationStatus.INSUFFICIENT_FUNDS, ReplayStatus.ORIGINAL);
        }

        long senderAfter = Math.subtractExact(sender.balance(), request.amount());
        long receiverAfter;
        try {
            receiverAfter = Math.addExact(receiver.balance(), request.amount());
        } catch (ArithmeticException exception) {
            return completeTransferLimitExceeded(connection, request.operationId(), timestamp);
        }
        if (receiverAfter > maximumBalance) {
            return completeTransferLimitExceeded(connection, request.operationId(), timestamp);
        }

        BalanceChange senderChange = BalanceChange.related(
            request.senderId(),
            request.receiverId(),
            Math.subtractExact(senderAfter, sender.balance()),
            sender.balance(),
            senderAfter,
            Math.addExact(sender.revision(), 1L)
        );
        BalanceChange receiverChange = BalanceChange.related(
            request.receiverId(),
            request.senderId(),
            Math.subtractExact(receiverAfter, receiver.balance()),
            receiver.balance(),
            receiverAfter,
            Math.addExact(receiver.revision(), 1L)
        );

        if (orderedIds.get(0).equals(request.senderId())) {
            updateAccount(connection, senderChange, timestamp);
            updateAccount(connection, receiverChange, timestamp);
        } else {
            updateAccount(connection, receiverChange, timestamp);
            updateAccount(connection, senderChange, timestamp);
        }
        this.persistence.ledger().append(connection, List.of(
            ledgerEntry(request.operationId(), senderChange, timestamp),
            ledgerEntry(request.operationId(), receiverChange, timestamp)
        ));
        this.persistence.operations().complete(connection, new OperationCompletion(
            request.operationId(),
            OperationStatus.SUCCESS,
            OperationResultCodec.transferSuccessPayload(senderChange, receiverChange),
            timestamp
        ));

        OperationReceipt receipt = new OperationReceipt(
            request.operationId(),
            OperationType.TRANSFER,
            request.reason(),
            request.actor(),
            source,
            request.metadata(),
            List.of(senderChange, receiverChange),
            timestamp
        );
        return new AccountMutationOutcome.Success(receipt, ReplayStatus.ORIGINAL);
    }

    private AccountMutationOutcome replayOrConflict(StoredOperation operation, OperationType type, UUID playerId,
                                                    Optional<UUID> relatedPlayerId, long amount,
                                                    com.stephanofer.progressengine.api.operation.OperationReason reason,
                                                    com.stephanofer.progressengine.api.source.OperationActor actor,
                                                    OperationSource source) {
        boolean matches = OperationFingerprint.matches(
            operation.requestFingerprint(),
            operation.fingerprintVersion(),
            type,
            playerId,
            relatedPlayerId,
            amount,
            reason,
            actor,
            source.pluginName()
        );
        if (!matches) {
            return new AccountMutationOutcome.Conflict(operation.operationId());
        }
        DecodedOperationResult decoded = OperationResultCodec.decode(operation, ReplayStatus.REPLAYED);
        if (decoded instanceof DecodedOperationResult.Success success) {
            return new AccountMutationOutcome.Success(success.receipt(), success.replayStatus());
        }
        DecodedOperationResult.Rejected rejected = (DecodedOperationResult.Rejected) decoded;
        return new AccountMutationOutcome.Rejected(rejected.operationId(), rejected.status(), rejected.replayStatus());
    }

    private AccountMutationOutcome awardReplayOrConflict(OperationSource source, AwardRequest request, StoredOperation operation,
                                                         ReplayStatus replayStatus) {
        boolean matches = OperationFingerprint.matches(
            operation.requestFingerprint(),
            operation.fingerprintVersion(),
            OperationType.AWARD,
            request.playerId(),
            Optional.empty(),
            request.gameId(),
            request.baseAmount(),
            request.reason(),
            request.actor(),
            source.pluginName()
        );
        if (!matches) {
            return new AccountMutationOutcome.Conflict(operation.operationId());
        }
        if (operation.status() == OperationStatus.PENDING) {
            throw new PersistenceDataException("Cannot replay pending award operation " + operation.operationId());
        }
        if (operation.status() == OperationStatus.SUCCESS) {
            DecodedOperationResult decoded = OperationResultCodec.decode(operation, replayStatus);
            DecodedOperationResult.Success success = (DecodedOperationResult.Success) decoded;
            return new AccountMutationOutcome.AwardSuccess(
                success.receipt(),
                OperationResultCodec.awardCalculation(operation),
                success.replayStatus()
            );
        }
        if (operation.status() == OperationStatus.NO_POINTS_AWARDED || operation.status() == OperationStatus.BALANCE_LIMIT_EXCEEDED) {
            return new AccountMutationOutcome.AwardNoMovement(
                operation.operationId(),
                operation.status(),
                OperationResultCodec.awardCalculation(operation),
                replayStatus
            );
        }
        if (operation.status() == OperationStatus.CANCELLED) {
            return new AccountMutationOutcome.Rejected(operation.operationId(), OperationStatus.CANCELLED, replayStatus);
        }
        throw new PersistenceDataException("Unexpected stored award outcome " + operation.status());
    }

    private CompletableFuture<AccountMutationOutcome> publishOriginalSuccess(AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Success success && success.replayStatus() == ReplayStatus.ORIGINAL) {
            return this.postCommitPublisher.publish(success.receipt()).thenApply(ignored -> outcome);
        }
        if (outcome instanceof AccountMutationOutcome.AwardSuccess success && success.replayStatus() == ReplayStatus.ORIGINAL) {
            return this.postCommitPublisher.publish(success.receipt()).thenApply(ignored -> outcome);
        }
        return CompletableFuture.completedFuture(outcome);
    }

    private AccountMutationOutcome completeTransferLimitExceeded(Connection connection, OperationId operationId, Instant timestamp)
        throws SQLException {
        this.persistence.operations().complete(connection, new OperationCompletion(
            operationId,
            OperationStatus.BALANCE_LIMIT_EXCEEDED,
            OperationResultCodec.rejectionPayload(),
            timestamp
        ));
        return new AccountMutationOutcome.Rejected(operationId, OperationStatus.BALANCE_LIMIT_EXCEEDED, ReplayStatus.ORIGINAL);
    }

    private AccountMutationOutcome completeAwardLimitExceeded(Connection connection, OperationId operationId,
                                                             AwardCalculation calculation, Instant timestamp) throws SQLException {
        this.persistence.operations().complete(connection, new OperationCompletion(
            operationId,
            OperationStatus.BALANCE_LIMIT_EXCEEDED,
            OperationResultCodec.awardCalculationPayload(calculation),
            timestamp
        ));
        return new AccountMutationOutcome.AwardNoMovement(
            operationId,
            OperationStatus.BALANCE_LIMIT_EXCEEDED,
            calculation,
            ReplayStatus.ORIGINAL
        );
    }

    private void updateAccount(Connection connection, BalanceChange change, Instant timestamp) throws SQLException {
        this.persistence.accounts().updateBalance(connection, change.playerId(), change.balanceAfter(), change.revision(), timestamp);
    }

    private static LedgerEntryDraft ledgerEntry(OperationId operationId, BalanceChange change, Instant timestamp) {
        return new LedgerEntryDraft(
            operationId,
            change.playerId(),
            change.relatedPlayerId(),
            change.delta(),
            change.balanceBefore(),
            change.balanceAfter(),
            change.revision(),
            timestamp
        );
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

    private static boolean exceedsLong(AwardCalculation calculation) {
        return calculation.calculatedAmount().setScale(0, RoundingMode.FLOOR).compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0;
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

    private static AwardResult toAwardResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new AwardResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.AwardSuccess success) {
            requireType(success.receipt(), OperationType.AWARD);
            return new AwardResult.Success(success.receipt(), success.calculation(), success.replayStatus());
        }
        if (outcome instanceof AccountMutationOutcome.AwardNoMovement noMovement) {
            if (noMovement.status() == OperationStatus.NO_POINTS_AWARDED) {
                return new AwardResult.NoPointsAwarded(operationId, noMovement.calculation(), noMovement.replayStatus());
            }
            if (noMovement.status() == OperationStatus.BALANCE_LIMIT_EXCEEDED) {
                return new AwardResult.BalanceLimitExceeded(operationId, noMovement.calculation(), noMovement.replayStatus());
            }
            throw unexpectedOutcome(OperationType.AWARD, noMovement.status());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        if (rejected.status() == OperationStatus.CANCELLED) {
            return new AwardResult.Cancelled(operationId, rejected.replayStatus());
        }
        throw unexpectedOutcome(OperationType.AWARD, rejected.status());
    }

    private AwardResult toAwardResult(OperationSource source, AwardRequest request, StoredOperation operation, ReplayStatus replayStatus) {
        AccountMutationOutcome outcome = awardReplayOrConflict(source, request, operation, replayStatus);
        return toAwardResult(request.operationId(), outcome);
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

    private static TransferResult toTransferResult(OperationId operationId, AccountMutationOutcome outcome) {
        if (outcome instanceof AccountMutationOutcome.Conflict) {
            return new TransferResult.IdempotencyConflict(operationId);
        }
        if (outcome instanceof AccountMutationOutcome.Success success) {
            requireType(success.receipt(), OperationType.TRANSFER);
            return new TransferResult.Success(success.receipt(), success.replayStatus());
        }
        AccountMutationOutcome.Rejected rejected = (AccountMutationOutcome.Rejected) outcome;
        if (rejected.status() == OperationStatus.INSUFFICIENT_FUNDS) {
            return new TransferResult.InsufficientFunds(operationId, rejected.replayStatus());
        }
        if (rejected.status() == OperationStatus.BALANCE_LIMIT_EXCEEDED) {
            return new TransferResult.BalanceLimitExceeded(operationId, rejected.replayStatus());
        }
        if (rejected.status() == OperationStatus.STALE_CONFIRMATION) {
            return new TransferResult.StaleConfirmation(operationId, rejected.replayStatus());
        }
        throw unexpectedOutcome(OperationType.TRANSFER, rejected.status());
    }

    private static void requireType(OperationReceipt receipt, OperationType expected) {
        if (receipt.type() != expected) {
            throw new PersistenceDataException("Replayed receipt type " + receipt.type() + " does not match " + expected);
        }
    }

    private static PersistenceDataException unexpectedOutcome(OperationType type, OperationStatus status) {
        return new PersistenceDataException("Unexpected stored outcome " + status + " for " + type);
    }

    private sealed interface AccountMutationOutcome permits AccountMutationOutcome.Success, AccountMutationOutcome.AwardSuccess,
        AccountMutationOutcome.AwardNoMovement, AccountMutationOutcome.Rejected, AccountMutationOutcome.Conflict {
        record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements AccountMutationOutcome {
            public Success {
                if (receipt == null) throw new NullPointerException("receipt cannot be null");
                if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
            }
        }

        record AwardSuccess(OperationReceipt receipt, AwardCalculation calculation, ReplayStatus replayStatus) implements AccountMutationOutcome {
            public AwardSuccess {
                if (receipt == null) throw new NullPointerException("receipt cannot be null");
                if (receipt.type() != OperationType.AWARD) throw new IllegalArgumentException("receipt must be AWARD");
                if (calculation == null) throw new NullPointerException("calculation cannot be null");
                if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
            }
        }

        record AwardNoMovement(OperationId operationId, OperationStatus status, AwardCalculation calculation,
                               ReplayStatus replayStatus) implements AccountMutationOutcome {
            public AwardNoMovement {
                if (operationId == null) throw new NullPointerException("operationId cannot be null");
                if (status != OperationStatus.NO_POINTS_AWARDED && status != OperationStatus.BALANCE_LIMIT_EXCEEDED) {
                    throw new IllegalArgumentException("status must be an award no-movement outcome");
                }
                if (calculation == null) throw new NullPointerException("calculation cannot be null");
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
