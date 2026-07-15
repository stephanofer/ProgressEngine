package com.stephanofer.progressengine.award;

import com.stephanofer.progressengine.account.AccountEconomy;
import com.stephanofer.progressengine.api.event.PointsAwardPrepareEvent;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.result.AwardCalculation;
import com.stephanofer.progressengine.api.result.AwardResult;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.booster.AwardBoosterCalculator;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AwardCoordinator {
    private final AccountEconomy economy;
    private final AwardPrepareEventDispatcher prepareDispatcher;
    private final AwardBoosterCalculator boosters;
    private final Supplier<ProgressEngineConfig> configSupplier;

    public AwardCoordinator(AccountEconomy economy, AwardPrepareEventDispatcher prepareDispatcher,
                            AwardBoosterCalculator boosters, Supplier<ProgressEngineConfig> configSupplier) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.prepareDispatcher = Objects.requireNonNull(prepareDispatcher, "prepareDispatcher");
        this.boosters = Objects.requireNonNull(boosters, "boosters");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    public CompletableFuture<AwardResult> award(OperationSource source, AwardRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        return this.economy.findAwardResult(source, request).thenCompose(existing -> existing
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> prepareAndAward(source, request)));
    }

    private CompletableFuture<AwardResult> prepareAndAward(OperationSource source, AwardRequest request) {
        return this.prepareDispatcher.dispatch(request, source).thenCompose(event -> {
            if (event.isCancelled()) {
                return this.economy.cancelAward(source, request);
            }
            long preparedBaseAmount = event.preparedBaseAmount();
            ProgressEngineConfig config = this.configSupplier.get();
            return this.boosters.calculate(request.playerId(), preparedBaseAmount, request.gameId(), config.serverId())
                .thenCompose(boost -> {
                    AwardCalculation calculation = AwardAmountCalculator.calculate(
                        request.baseAmount(),
                        preparedBaseAmount,
                        boost,
                        config.economy().awardRounding()
                    );
                    return this.economy.award(source, request, calculation, config.economy().maximumBalance());
                });
        });
    }
}
