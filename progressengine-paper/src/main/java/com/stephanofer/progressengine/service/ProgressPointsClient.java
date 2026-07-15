package com.stephanofer.progressengine.service;

import com.stephanofer.progressengine.account.AccountEconomy;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.PointsClient;
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
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.award.AwardCoordinator;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.PlayerReadiness;
import com.stephanofer.progressengine.lifecycle.WorkKind;
import com.stephanofer.progressengine.lifecycle.WorkPermit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ProgressPointsClient implements PointsClient {
    private final String pluginName;
    private final BalanceStore balanceStore;
    private final AccountEconomy accountEconomy;
    private final AwardCoordinator awardCoordinator;
    private final InFlightTracker inFlightTracker;
    private final PlayerReadiness playerReadiness;
    private final Supplier<ProgressEngineConfig> configSupplier;

    ProgressPointsClient(String pluginName, BalanceStore balanceStore, AccountEconomy accountEconomy,
                          AwardCoordinator awardCoordinator, InFlightTracker inFlightTracker,
                          PlayerReadiness playerReadiness,
                          Supplier<ProgressEngineConfig> configSupplier) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.accountEconomy = Objects.requireNonNull(accountEconomy, "accountEconomy");
        this.awardCoordinator = Objects.requireNonNull(awardCoordinator, "awardCoordinator");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.playerReadiness = Objects.requireNonNull(playerReadiness, "playerReadiness");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    @Override
    public Optional<BalanceSnapshot> cached(UUID playerId) {
        return this.balanceStore.cached(playerId);
    }

    @Override
    public CompletableFuture<BalanceSnapshot> load(UUID playerId) {
        return this.balanceStore.load(playerId);
    }

    @Override
    public CompletableFuture<BalanceSnapshot> refresh(UUID playerId) {
        return this.balanceStore.refresh(playerId);
    }

    @Override
    public boolean isReady(UUID playerId) {
        return this.playerReadiness.isReady(playerId);
    }

    @Override
    public CompletableFuture<AwardResult> award(AwardRequest request) {
        return mutate(() -> this.awardCoordinator.award(source(), request));
    }

    @Override
    public CompletableFuture<CreditResult> credit(CreditRequest request) {
        return mutate(() -> this.accountEconomy.credit(source(), request));
    }

    @Override
    public CompletableFuture<DebitResult> debit(DebitRequest request) {
        return mutate(() -> this.accountEconomy.debit(source(), request));
    }

    @Override
    public CompletableFuture<TransferResult> transfer(TransferRequest request) {
        return mutate(() -> this.accountEconomy.transfer(source(), request));
    }

    @Override
    public CompletableFuture<SetBalanceResult> setBalance(SetBalanceRequest request) {
        return mutate(() -> this.accountEconomy.setBalance(source(), request));
    }

    @Override
    public CompletableFuture<ResetBalanceResult> resetBalance(ResetBalanceRequest request) {
        return mutate(() -> this.accountEconomy.resetBalance(source(), request));
    }

    private OperationSource source() {
        return new OperationSource(this.pluginName, this.configSupplier.get().serverId());
    }

    private <T> CompletableFuture<T> mutate(Supplier<CompletableFuture<T>> operation) {
        Optional<WorkPermit> permit = this.inFlightTracker.acquire(WorkKind.MUTATION);
        if (permit.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ProgressEngine is not accepting mutations"));
        }
        WorkPermit acquired = permit.orElseThrow();
        try {
            return operation.get().whenComplete((ignored, failure) -> acquired.close());
        } catch (RuntimeException exception) {
            acquired.close();
            return CompletableFuture.failedFuture(exception);
        }
    }
}
