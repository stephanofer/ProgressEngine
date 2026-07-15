package com.stephanofer.progressengine.api;

import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.request.CreditRequest;
import com.stephanofer.progressengine.api.request.DebitRequest;
import com.stephanofer.progressengine.api.request.ResetBalanceRequest;
import com.stephanofer.progressengine.api.request.SetBalanceRequest;
import com.stephanofer.progressengine.api.request.TransferRequest;
import com.stephanofer.progressengine.api.result.AwardResult;
import com.stephanofer.progressengine.api.result.CreditResult;
import com.stephanofer.progressengine.api.result.DebitResult;
import com.stephanofer.progressengine.api.result.ResetBalanceResult;
import com.stephanofer.progressengine.api.result.SetBalanceResult;
import com.stephanofer.progressengine.api.result.TransferResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Plugin-bound client used to read and mutate point balances.
 */
public interface PointsClient {
    /** Returns a local snapshot without performing I/O. */
    Optional<BalanceSnapshot> cached(UUID playerId);

    /** Loads an account snapshot asynchronously. */
    CompletableFuture<BalanceSnapshot> load(UUID playerId);

    /** Refreshes an account snapshot asynchronously from the durable source. */
    CompletableFuture<BalanceSnapshot> refresh(UUID playerId);

    /** Returns whether the player lifecycle is ready for gameplay integrations. */
    boolean isReady(UUID playerId);

    /** Submits an award request. */
    CompletableFuture<AwardResult> award(AwardRequest request);

    /** Submits a direct credit request. */
    CompletableFuture<CreditResult> credit(CreditRequest request);

    /** Submits a debit request. */
    CompletableFuture<DebitResult> debit(DebitRequest request);

    /** Submits an atomic transfer request. */
    CompletableFuture<TransferResult> transfer(TransferRequest request);

    /** Submits a set-balance request. */
    CompletableFuture<SetBalanceResult> setBalance(SetBalanceRequest request);

    /** Submits a reset-balance request. */
    CompletableFuture<ResetBalanceResult> resetBalance(ResetBalanceRequest request);
}
