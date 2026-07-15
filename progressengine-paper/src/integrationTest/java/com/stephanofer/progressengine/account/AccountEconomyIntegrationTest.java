package com.stephanofer.progressengine.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
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
import com.stephanofer.progressengine.persistence.LedgerPage;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceIntegrationTestSupport;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.persistence.StoredOperation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class AccountEconomyIntegrationTest extends PersistenceIntegrationTestSupport {
    private static final OperationReason CREDIT_REASON = OperationReason.of("test:credit");
    private static final OperationReason DEBIT_REASON = OperationReason.of("test:debit");
    private static final OperationReason SET_REASON = OperationReason.of("test:set");
    private static final OperationReason RESET_REASON = OperationReason.of("test:reset");

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

    private static AccountEconomy economy(ProgressPersistence persistence, String serverId, long maximumBalance) {
        return new AccountEconomy(
            persistence,
            new OperationSource("IntegrationTest", serverId),
            () -> maximumBalance
        );
    }
}
