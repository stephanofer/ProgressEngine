package com.stephanofer.progressengine.service;

import com.stephanofer.progressengine.account.AccountEconomy;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.PointsClient;
import com.stephanofer.progressengine.api.PointsService;
import com.stephanofer.progressengine.award.AwardCoordinator;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.PlayerReadiness;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

public final class ProgressPointsService implements PointsService {
    private final BalanceStore balanceStore;
    private final AccountEconomy accountEconomy;
    private final AwardCoordinator awardCoordinator;
    private final InFlightTracker inFlightTracker;
    private final PlayerReadiness playerReadiness;
    private final Supplier<ProgressEngineConfig> configSupplier;

    public ProgressPointsService(BalanceStore balanceStore, AccountEconomy accountEconomy, AwardCoordinator awardCoordinator,
                                  InFlightTracker inFlightTracker, PlayerReadiness playerReadiness,
                                  Supplier<ProgressEngineConfig> configSupplier) {
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.accountEconomy = Objects.requireNonNull(accountEconomy, "accountEconomy");
        this.awardCoordinator = Objects.requireNonNull(awardCoordinator, "awardCoordinator");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.playerReadiness = Objects.requireNonNull(playerReadiness, "playerReadiness");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    @Override
    public PointsClient client(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new ProgressPointsClient(
            plugin.getName(),
            this.balanceStore,
            this.accountEconomy,
            this.awardCoordinator,
            this.inFlightTracker,
            this.playerReadiness,
            this.configSupplier
        );
    }
}
