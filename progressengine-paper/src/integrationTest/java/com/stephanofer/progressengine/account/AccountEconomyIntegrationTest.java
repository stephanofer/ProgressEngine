package com.stephanofer.progressengine.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
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
import com.stephanofer.progressengine.persistence.LedgerPage;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceIntegrationTestSupport;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.persistence.StoredOperation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AccountEconomyIntegrationTest extends PersistenceIntegrationTestSupport {
    private static final OperationReason CREDIT_REASON = OperationReason.of("test:credit");
    private static final OperationReason DEBIT_REASON = OperationReason.of("test:debit");
    private static final OperationReason SET_REASON = OperationReason.of("test:set");
    private static final OperationReason RESET_REASON = OperationReason.of("test:reset");
    private static final OperationReason TRANSFER_REASON = OperationReason.of("test:transfer");
    private static final OperationReason AWARD_REASON = OperationReason.of("test:award");

    @Test
    void creditReplayConflictAndCrossServerRetryUseDurableResult() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId operationId = OperationId.generate();
            AccountEconomy serverA = economy(persistence, "server-a", 1_000L);
            AccountEconomy serverB = economy(persistence, "server-b", 1_000L);

            CreditRequest original = new CreditRequest(
                operationId,
                playerId,
                100L,
                CREDIT_REASON,
                com.stephanofer.progressengine.api.source.OperationActor.plugin(),
                OperationMetadata.of(Map.of("attempt", "original"))
            );

            CreditResult.Success success = assertInstanceOf(CreditResult.Success.class, serverA.credit(original).join());
            CreditResult.Success replay = assertInstanceOf(CreditResult.Success.class, serverB.credit(new CreditRequest(
                operationId,
                playerId,
                100L,
                CREDIT_REASON,
                com.stephanofer.progressengine.api.source.OperationActor.plugin(),
                OperationMetadata.of(Map.of("attempt", "retry"))
            )).join());
            CreditResult conflict = serverA.credit(new CreditRequest(OperationId.of(operationId.value()), playerId, 101L, CREDIT_REASON)).join();

            assertEquals(ReplayStatus.ORIGINAL, success.replayStatus());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertEquals("server-a", replay.receipt().source().serverId());
            assertEquals("original", replay.receipt().metadata().values().get("attempt"));
            assertInstanceOf(CreditResult.IdempotencyConflict.class, conflict);
            assertEquals(100L, persistence.accounts().find(playerId).join().orElseThrow().balance());
            assertEquals(1, persistence.ledger().findByOperation(operationId).join().size());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void concurrentDebitsFromDifferentLocalSequencersDoNotDoubleSpend() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            AccountEconomy serverA = economy(persistence, "server-a", 1_000L);
            AccountEconomy serverB = economy(persistence, "server-b", 1_000L);
            serverA.credit(new CreditRequest(OperationId.generate(), playerId, 100L, CREDIT_REASON)).join();

            CompletableFuture<DebitResult> first = serverA.debit(new DebitRequest(OperationId.generate(), playerId, 100L, DEBIT_REASON));
            CompletableFuture<DebitResult> second = serverB.debit(new DebitRequest(OperationId.generate(), playerId, 100L, DEBIT_REASON));
            CompletableFuture.allOf(first, second).join();

            long successes = java.util.stream.Stream.of(first.join(), second.join())
                .filter(DebitResult.Success.class::isInstance)
                .count();
            long insufficient = java.util.stream.Stream.of(first.join(), second.join())
                .filter(DebitResult.InsufficientFunds.class::isInstance)
                .count();

            assertEquals(1L, successes);
            assertEquals(1L, insufficient);
            assertEquals(0L, persistence.accounts().find(playerId).join().orElseThrow().balance());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void setAndResetNoOpsAreAuditedWithZeroDelta() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            AccountEconomy economy = economy(persistence, "server-a", 1_000L);

            SetBalanceResult.Success set = assertInstanceOf(SetBalanceResult.Success.class,
                economy.setBalance(new SetBalanceRequest(OperationId.generate(), playerId, 0L, SET_REASON)).join());
            ResetBalanceResult.Success reset = assertInstanceOf(ResetBalanceResult.Success.class,
                economy.resetBalance(new ResetBalanceRequest(OperationId.generate(), playerId, RESET_REASON)).join());

            assertEquals(0L, set.receipt().changes().getFirst().delta());
            assertEquals(1L, set.receipt().changes().getFirst().revision());
            assertEquals(0L, reset.receipt().changes().getFirst().delta());
            assertEquals(2L, reset.receipt().changes().getFirst().revision());
            LedgerPage history = persistence.ledger().history(playerId, 10, Optional.empty()).join();
            assertEquals(2, history.entries().size());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void durableRejectionDoesNotMutateLedgerAndReplaysAfterStateChanges() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId debitId = OperationId.generate();
            AccountEconomy economy = economy(persistence, "server-a", 1_000L);

            DebitResult.InsufficientFunds rejected = assertInstanceOf(DebitResult.InsufficientFunds.class,
                economy.debit(new DebitRequest(debitId, playerId, 100L, DEBIT_REASON)).join());
            economy.credit(new CreditRequest(OperationId.generate(), playerId, 100L, CREDIT_REASON)).join();
            DebitResult.InsufficientFunds replay = assertInstanceOf(DebitResult.InsufficientFunds.class,
                economy.debit(new DebitRequest(debitId, playerId, 100L, DEBIT_REASON)).join());

            StoredOperation operation = persistence.operations().find(debitId).join().orElseThrow();
            assertEquals(OperationStatus.INSUFFICIENT_FUNDS, operation.status());
            assertEquals(ReplayStatus.ORIGINAL, rejected.replayStatus());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertTrue(persistence.ledger().findByOperation(debitId).join().isEmpty());
            assertEquals(100L, persistence.accounts().find(playerId).join().orElseThrow().balance());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void maximumBalanceRejectionsAreDurableWithoutLedger() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId id = OperationId.generate();
            AccountEconomy economy = economy(persistence, "server-a", 50L);

            CreditResult result = economy.credit(new CreditRequest(id, playerId, 51L, CREDIT_REASON)).join();

            assertInstanceOf(CreditResult.BalanceLimitExceeded.class, result);
            assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, persistence.operations().find(id).join().orElseThrow().status());
            assertTrue(persistence.ledger().findByOperation(id).join().isEmpty());
            assertEquals(0L, persistence.accounts().find(playerId).join().orElseThrow().balance());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void replayedSuccessDoesNotPublishPostCommitAgain() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId operationId = OperationId.generate();
            AtomicInteger publications = new AtomicInteger();
            AccountEconomy economy = new AccountEconomy(
                persistence,
                new OperationSource("IntegrationTest", "server-a"),
                () -> 1_000L,
                new com.stephanofer.progressengine.transaction.AccountMutationSequencer(),
                receipt -> {
                    publications.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            );
            CreditRequest request = new CreditRequest(operationId, playerId, 100L, CREDIT_REASON);

            CreditResult.Success success = assertInstanceOf(CreditResult.Success.class, economy.credit(request).join());
            CreditResult.Success replay = assertInstanceOf(CreditResult.Success.class, economy.credit(request).join());

            assertEquals(ReplayStatus.ORIGINAL, success.replayStatus());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertEquals(1, publications.get());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void originalSuccessCompletesAfterPostCommitPublisher() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            CompletableFuture<Void> releasePublisher = new CompletableFuture<>();
            CompletableFuture<Void> publisherStarted = new CompletableFuture<>();
            AccountEconomy economy = new AccountEconomy(
                persistence,
                new OperationSource("IntegrationTest", "server-a"),
                () -> 1_000L,
                new com.stephanofer.progressengine.transaction.AccountMutationSequencer(),
                receipt -> {
                    publisherStarted.complete(null);
                    return releasePublisher;
                }
            );

            CompletableFuture<CreditResult> result = economy.credit(new CreditRequest(OperationId.generate(), playerId, 100L, CREDIT_REASON));
            publisherStarted.join();

            assertTrue(!result.isDone());
            releasePublisher.complete(null);
            assertInstanceOf(CreditResult.Success.class, result.join());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void transferPersistsTwoBalancesTwoLedgerEntriesAndReplaysDurably() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            OperationId transferId = OperationId.generate();
            AccountEconomy serverA = economy(persistence, "server-a", 1_000L);
            AccountEconomy serverB = economy(persistence, "server-b", 1_000L);
            serverA.credit(new CreditRequest(OperationId.generate(), senderId, 500L, CREDIT_REASON)).join();
            serverA.credit(new CreditRequest(OperationId.generate(), receiverId, 20L, CREDIT_REASON)).join();

            TransferRequest original = new TransferRequest(
                transferId,
                senderId,
                receiverId,
                150L,
                TRANSFER_REASON,
                com.stephanofer.progressengine.api.source.OperationActor.plugin(),
                OperationMetadata.of(Map.of("attempt", "original"))
            );
            TransferResult.Success success = assertInstanceOf(TransferResult.Success.class, serverA.transfer(original).join());
            TransferResult.Success replay = assertInstanceOf(TransferResult.Success.class, serverB.transfer(new TransferRequest(
                transferId,
                senderId,
                receiverId,
                150L,
                TRANSFER_REASON,
                com.stephanofer.progressengine.api.source.OperationActor.plugin(),
                OperationMetadata.of(Map.of("attempt", "retry"))
            )).join());

            assertEquals(ReplayStatus.ORIGINAL, success.replayStatus());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertEquals("server-a", replay.receipt().source().serverId());
            assertEquals("original", replay.receipt().metadata().values().get("attempt"));
            assertEquals(-150L, success.receipt().changes().get(0).delta());
            assertEquals(senderId, success.receipt().changes().get(0).playerId());
            assertEquals(Optional.of(receiverId), success.receipt().changes().get(0).relatedPlayerId());
            assertEquals(150L, success.receipt().changes().get(1).delta());
            assertEquals(receiverId, success.receipt().changes().get(1).playerId());
            assertEquals(Optional.of(senderId), success.receipt().changes().get(1).relatedPlayerId());
            assertEquals(350L, persistence.accounts().find(senderId).join().orElseThrow().balance());
            assertEquals(170L, persistence.accounts().find(receiverId).join().orElseThrow().balance());
            assertEquals(2, persistence.ledger().findByOperation(transferId).join().size());
            StoredOperation stored = persistence.operations().find(transferId).join().orElseThrow();
            assertEquals(OperationStatus.SUCCESS, stored.status());
            assertEquals(Optional.of(receiverId), stored.relatedPlayerId());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void transferRejectionsAreDurableWithoutLedgerOrBalanceChanges() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            AccountEconomy economy = economy(persistence, "server-a", 100L);
            OperationId insufficientId = OperationId.generate();
            OperationId limitId = OperationId.generate();
            economy.credit(new CreditRequest(OperationId.generate(), senderId, 100L, CREDIT_REASON)).join();
            economy.credit(new CreditRequest(OperationId.generate(), receiverId, 90L, CREDIT_REASON)).join();

            TransferResult.InsufficientFunds insufficient = assertInstanceOf(TransferResult.InsufficientFunds.class,
                economy.transfer(new TransferRequest(insufficientId, UUID.randomUUID(), receiverId, 1L, TRANSFER_REASON)).join());
            TransferResult.BalanceLimitExceeded limit = assertInstanceOf(TransferResult.BalanceLimitExceeded.class,
                economy.transfer(new TransferRequest(limitId, senderId, receiverId, 11L, TRANSFER_REASON)).join());

            assertEquals(ReplayStatus.ORIGINAL, insufficient.replayStatus());
            assertEquals(ReplayStatus.ORIGINAL, limit.replayStatus());
            assertTrue(persistence.ledger().findByOperation(insufficientId).join().isEmpty());
            assertTrue(persistence.ledger().findByOperation(limitId).join().isEmpty());
            assertEquals(100L, persistence.accounts().find(senderId).join().orElseThrow().balance());
            assertEquals(90L, persistence.accounts().find(receiverId).join().orElseThrow().balance());
            assertEquals(OperationStatus.INSUFFICIENT_FUNDS, persistence.operations().find(insufficientId).join().orElseThrow().status());
            assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, persistence.operations().find(limitId).join().orElseThrow().status());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void concurrentTransfersFromDifferentSequencersConserveTotalAndDoNotDoubleSpend() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID senderId = UUID.randomUUID();
            UUID firstReceiver = UUID.randomUUID();
            UUID secondReceiver = UUID.randomUUID();
            AccountEconomy serverA = economy(persistence, "server-a", 1_000L);
            AccountEconomy serverB = economy(persistence, "server-b", 1_000L);
            serverA.credit(new CreditRequest(OperationId.generate(), senderId, 100L, CREDIT_REASON)).join();

            CompletableFuture<TransferResult> first = serverA.transfer(new TransferRequest(
                OperationId.generate(), senderId, firstReceiver, 100L, TRANSFER_REASON));
            CompletableFuture<TransferResult> second = serverB.transfer(new TransferRequest(
                OperationId.generate(), senderId, secondReceiver, 100L, TRANSFER_REASON));
            CompletableFuture.allOf(first, second).join();

            long successes = java.util.stream.Stream.of(first.join(), second.join())
                .filter(TransferResult.Success.class::isInstance)
                .count();
            long insufficient = java.util.stream.Stream.of(first.join(), second.join())
                .filter(TransferResult.InsufficientFunds.class::isInstance)
                .count();
            BigInteger total = BigInteger.valueOf(persistence.accounts().find(senderId).join().orElseThrow().balance())
                .add(BigInteger.valueOf(persistence.accounts().find(firstReceiver).join().orElseThrow().balance()))
                .add(BigInteger.valueOf(persistence.accounts().find(secondReceiver).join().orElseThrow().balance()));

            assertEquals(1L, successes);
            assertEquals(1L, insufficient);
            assertEquals(BigInteger.valueOf(100L), total);
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void crossedTransfersUseCanonicalLocksAndBothCanCommit() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            AccountEconomy serverA = economy(persistence, "server-a", 1_000L);
            AccountEconomy serverB = economy(persistence, "server-b", 1_000L);
            serverA.credit(new CreditRequest(OperationId.generate(), firstId, 100L, CREDIT_REASON)).join();
            serverA.credit(new CreditRequest(OperationId.generate(), secondId, 100L, CREDIT_REASON)).join();

            CompletableFuture<TransferResult> first = serverA.transfer(new TransferRequest(
                OperationId.generate(), firstId, secondId, 10L, TRANSFER_REASON));
            CompletableFuture<TransferResult> second = serverB.transfer(new TransferRequest(
                OperationId.generate(), secondId, firstId, 20L, TRANSFER_REASON));
            CompletableFuture.allOf(first, second).join();

            assertInstanceOf(TransferResult.Success.class, first.join());
            assertInstanceOf(TransferResult.Success.class, second.join());
            BigInteger total = BigInteger.valueOf(persistence.accounts().find(firstId).join().orElseThrow().balance())
                .add(BigInteger.valueOf(persistence.accounts().find(secondId).join().orElseThrow().balance()));
            assertEquals(BigInteger.valueOf(200L), total);
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void failedTransferTransactionRollsBackReservedOperation() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            OperationId transferId = OperationId.generate();
            AccountEconomy economy = economy(persistence, "server-a", Long.MAX_VALUE);
            economy.credit(new CreditRequest(OperationId.generate(), senderId, 100L, CREDIT_REASON)).join();
            persistence.accounts().createOrLoad(receiverId).join();
            persistence.database().execute(connection -> {
                try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + persistence.tables().accounts() + " SET revision = ? WHERE player_uuid = ?")) {
                    statement.setLong(1, Long.MAX_VALUE);
                    statement.setBytes(2, com.stephanofer.progressengine.persistence.BinaryUuid.encode(receiverId));
                    statement.executeUpdate();
                }
            }).join();

            assertThrows(java.util.concurrent.CompletionException.class, () -> economy.transfer(new TransferRequest(
                transferId, senderId, receiverId, 1L, TRANSFER_REASON)).join());

            assertTrue(persistence.operations().find(transferId).join().isEmpty());
            assertEquals(100L, persistence.accounts().find(senderId).join().orElseThrow().balance());
            assertEquals(0L, persistence.accounts().find(receiverId).join().orElseThrow().balance());
            assertTrue(persistence.ledger().findByOperation(transferId).join().isEmpty());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void awardPersistsCalculationGameContextAndReplaysDurably() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId operationId = OperationId.generate();
            AccountEconomy economy = economy(persistence, "server-a", 1_000L);
            AwardRequest request = awardRequest(operationId, playerId, "skywars");
            AwardCalculation calculation = calculation(10L, 12L, "2.5", "30.0", 30L, true);

            AwardResult.Success success = assertInstanceOf(AwardResult.Success.class,
                economy.award(new OperationSource("GameplayPlugin", "server-a"), request, calculation, 1_000L).join());
            AwardResult.Success replay = assertInstanceOf(AwardResult.Success.class,
                economy.award(new OperationSource("GameplayPlugin", "server-b"), request, calculation, 1_000L).join());
            AwardResult conflict = economy.award(
                new OperationSource("GameplayPlugin", "server-a"),
                awardRequest(operationId, playerId, "bedwars"),
                calculation,
                1_000L
            ).join();

            StoredOperation stored = persistence.operations().find(operationId).join().orElseThrow();
            assertEquals(OperationStatus.SUCCESS, stored.status());
            assertEquals(Optional.of("skywars"), stored.gameId());
            assertEquals(10L, stored.requestedAmount());
            assertEquals(30L, success.receipt().changes().getFirst().delta());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertEquals("server-a", replay.receipt().source().serverId());
            assertEquals(new BigDecimal("30.0"), replay.calculation().calculatedAmount());
            assertInstanceOf(AwardResult.IdempotencyConflict.class, conflict);
            assertEquals(30L, persistence.accounts().find(playerId).join().orElseThrow().balance());
            assertEquals(1, persistence.ledger().findByOperation(operationId).join().size());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void cancelledAndNoPointsAwardsAreDurableWithoutLedger() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            AccountEconomy economy = economy(persistence, "server-a", 1_000L);
            OperationSource source = new OperationSource("GameplayPlugin", "server-a");
            OperationId cancelledId = OperationId.generate();
            OperationId noPointsId = OperationId.generate();

            AwardResult.Cancelled cancelled = assertInstanceOf(AwardResult.Cancelled.class,
                economy.cancelAward(source, awardRequest(cancelledId, playerId, "skywars")).join());
            AwardResult.Cancelled replay = assertInstanceOf(AwardResult.Cancelled.class,
                economy.cancelAward(source, awardRequest(cancelledId, playerId, "skywars")).join());
            AwardCalculation zero = calculation(10L, 1L, "0.5", "0.5", 0L, true);
            AwardResult.NoPointsAwarded noPoints = assertInstanceOf(AwardResult.NoPointsAwarded.class,
                economy.award(source, awardRequest(noPointsId, playerId, "skywars"), zero, 1_000L).join());

            assertEquals(ReplayStatus.ORIGINAL, cancelled.replayStatus());
            assertEquals(ReplayStatus.REPLAYED, replay.replayStatus());
            assertEquals(ReplayStatus.ORIGINAL, noPoints.replayStatus());
            assertEquals(OperationStatus.CANCELLED, persistence.operations().find(cancelledId).join().orElseThrow().status());
            assertEquals(OperationStatus.NO_POINTS_AWARDED, persistence.operations().find(noPointsId).join().orElseThrow().status());
            assertTrue(persistence.ledger().findByOperation(cancelledId).join().isEmpty());
            assertTrue(persistence.ledger().findByOperation(noPointsId).join().isEmpty());
            assertTrue(persistence.accounts().find(playerId).join().isEmpty());
        } finally {
            cleanup(persistence);
        }
    }

    @Test
    void awardOverflowIsDurableBalanceLimitWithoutLedger() {
        ProgressPersistence persistence = createPersistence();
        try {
            persistence.migrate().join();
            UUID playerId = UUID.randomUUID();
            OperationId operationId = OperationId.generate();
            AccountEconomy economy = economy(persistence, "server-a", Long.MAX_VALUE);
            AwardCalculation overflowing = calculation(10L, 10L, "1", "9223372036854775808", Long.MAX_VALUE, true);

            AwardResult.BalanceLimitExceeded result = assertInstanceOf(AwardResult.BalanceLimitExceeded.class,
                economy.award(new OperationSource("GameplayPlugin", "server-a"), awardRequest(operationId, playerId, "skywars"),
                    overflowing, Long.MAX_VALUE).join());

            assertEquals(ReplayStatus.ORIGINAL, result.replayStatus());
            assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, persistence.operations().find(operationId).join().orElseThrow().status());
            assertTrue(persistence.ledger().findByOperation(operationId).join().isEmpty());
            assertTrue(persistence.accounts().find(playerId).join().isEmpty());
        } finally {
            cleanup(persistence);
        }
    }

    private static AccountEconomy economy(ProgressPersistence persistence, String serverId, long maximumBalance) {
        return new AccountEconomy(
            persistence,
            new OperationSource("IntegrationTest", serverId),
            () -> maximumBalance
        );
    }

    private static AwardRequest awardRequest(OperationId operationId, UUID playerId, String gameId) {
        return new AwardRequest(
            operationId,
            playerId,
            10L,
            AWARD_REASON,
            Optional.of(gameId),
            com.stephanofer.progressengine.api.source.OperationActor.plugin(),
            OperationMetadata.empty()
        );
    }

    private static AwardCalculation calculation(long requested, long prepared, String multiplier, String calculated, long finalAmount,
                                                boolean boostersEvaluated) {
        return new AwardCalculation(
            requested,
            prepared,
            new BigDecimal(multiplier),
            new BigDecimal(calculated),
            finalAmount,
            List.of("personal_points_x2"),
            false,
            boostersEvaluated
        );
    }
}
