package com.stephanofer.progressengine.lifecycle;

import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.event.PlayerPointsReadyEvent;
import com.stephanofer.progressengine.persistence.BinaryUuid;
import com.stephanofer.progressengine.persistence.PlayerUsernames;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerLifecycleCoordinator implements PlayerReadiness, AutoCloseable {
    private static final long ABANDONED_PRELOAD_CLEANUP_TICKS = 20L * 30L;

    private final BalanceStore balanceStore;
    private final PlayerNameUpdater playerNameUpdater;
    private final PlayerSettingsReadiness playerSettings;
    private final Optional<PlayerBoostersReadiness> networkBoosters;
    private final InFlightTracker inFlightTracker;
    private final MainThreadExecutor mainThreadExecutor;
    private final DelayedTaskScheduler delayedTaskScheduler;
    private final ReadyEventDispatcher readyEventDispatcher;
    private final Logger logger;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, PendingPreload> preloads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong epochs = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlayerLifecycleCoordinator(
        BalanceStore balanceStore,
        PlayerNameUpdater playerNameUpdater,
        PlayerSettingsReadiness playerSettings,
        Optional<PlayerBoostersReadiness> networkBoosters,
        InFlightTracker inFlightTracker,
        MainThreadExecutor mainThreadExecutor,
        DelayedTaskScheduler delayedTaskScheduler,
        Logger logger,
        Clock clock
    ) {
        this(
            balanceStore,
            playerNameUpdater,
            playerSettings,
            networkBoosters,
            inFlightTracker,
            mainThreadExecutor,
            delayedTaskScheduler,
            PlayerPointsReadyEvent::callEvent,
            logger,
            clock
        );
    }

    PlayerLifecycleCoordinator(
        BalanceStore balanceStore,
        PlayerNameUpdater playerNameUpdater,
        PlayerSettingsReadiness playerSettings,
        Optional<PlayerBoostersReadiness> networkBoosters,
        InFlightTracker inFlightTracker,
        MainThreadExecutor mainThreadExecutor,
        DelayedTaskScheduler delayedTaskScheduler,
        ReadyEventDispatcher readyEventDispatcher,
        Logger logger,
        Clock clock
    ) {
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.playerNameUpdater = Objects.requireNonNull(playerNameUpdater, "playerNameUpdater");
        this.playerSettings = Objects.requireNonNull(playerSettings, "playerSettings");
        this.networkBoosters = Objects.requireNonNull(networkBoosters, "networkBoosters");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.delayedTaskScheduler = Objects.requireNonNull(delayedTaskScheduler, "delayedTaskScheduler");
        this.readyEventDispatcher = Objects.requireNonNull(readyEventDispatcher, "readyEventDispatcher");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void preload(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        if (this.closed.get()) {
            return;
        }
        CompletableFuture<BalanceSnapshot> snapshotFuture = this.balanceStore.refresh(validPlayerId);
        PendingPreload preload = new PendingPreload(snapshotFuture);
        AutoCloseable cleanup = this.delayedTaskScheduler.schedule(
            () -> this.preloads.remove(validPlayerId, preload),
            ABANDONED_PRELOAD_CLEANUP_TICKS
        );
        preload.cleanup(cleanup);
        PendingPreload previous = this.preloads.put(validPlayerId, preload);
        if (previous != null) {
            previous.close();
        }
    }

    public void startSession(UUID playerId, String username, BooleanSupplier onlineCheck) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        String validUsername = PlayerUsernames.requireValid(username);
        Objects.requireNonNull(onlineCheck, "onlineCheck");
        if (this.closed.get() || !onlineCheck.getAsBoolean()) {
            discardPreload(validPlayerId);
            return;
        }
        PlayerSession session = new PlayerSession(validPlayerId, validUsername, this.epochs.incrementAndGet(), onlineCheck);
        PlayerSession existing = this.sessions.putIfAbsent(validPlayerId, session);
        if (existing != null) {
            return;
        }
        runSession(session);
    }

    public void quit(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        discardPreload(validPlayerId);
        PlayerSession removed = this.sessions.remove(validPlayerId);
        if (removed != null) {
            removed.invalidate();
        }
    }

    @Override
    public boolean isReady(UUID playerId) {
        UUID validPlayerId = BinaryUuid.requireValid(playerId, "playerId");
        PlayerSession session = this.sessions.get(validPlayerId);
        return session != null && session.status() == PlayerSessionStatus.READY && session.isCurrent();
    }

    public Set<UUID> readyPlayerIds() {
        return this.sessions.values().stream()
            .filter(session -> session.status() == PlayerSessionStatus.READY && session.isCurrent())
            .map(PlayerSession::playerId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        for (PendingPreload preload : this.preloads.values()) {
            preload.close();
        }
        this.preloads.clear();
        for (PlayerSession session : this.sessions.values()) {
            session.invalidate();
        }
        this.sessions.clear();
    }

    private void runSession(PlayerSession session) {
        Optional<WorkPermit> permit = this.inFlightTracker.acquire(WorkKind.LOAD);
        if (permit.isEmpty()) {
            failSession(session, new IllegalStateException("ProgressEngine is not accepting player lifecycle work"));
            return;
        }
        WorkPermit acquired = permit.orElseThrow();

        CompletableFuture<BalanceSnapshot> balanceFuture;
        CompletableFuture<Void> nameFuture;
        CompletableFuture<Void> boostersFuture;
        try {
            balanceFuture = balanceFuture(session.playerId());
            nameFuture = this.playerNameUpdater.updateCurrentMapping(
                session.playerId(),
                session.username(),
                Instant.now(this.clock)
            );
            boostersFuture = boostersFuture(session.playerId());
        } catch (RuntimeException exception) {
            acquired.close();
            failSession(session, exception);
            return;
        }

        CompletableFuture.allOf(balanceFuture, nameFuture, boostersFuture)
            .whenComplete((ignored, failure) -> {
                acquired.close();
                if (failure != null) {
                    failSession(session, failure);
                    return;
                }
                BalanceSnapshot loaded = balanceFuture.join();
                this.mainThreadExecutor.execute(() -> publishReady(session, loaded));
            });
    }

    private CompletableFuture<BalanceSnapshot> balanceFuture(UUID playerId) {
        PendingPreload preload = this.preloads.remove(playerId);
        if (preload == null) {
            return this.balanceStore.refresh(playerId);
        }
        preload.close();
        return preload.snapshotFuture();
    }

    private CompletableFuture<Void> boostersFuture(UUID playerId) {
        if (this.networkBoosters.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return this.networkBoosters.orElseThrow().load(playerId);
    }

    private void publishReady(PlayerSession session, BalanceSnapshot loaded) {
        if (!session.isCurrent() || !session.online()) {
            return;
        }
        if (this.closed.get() || this.sessions.get(session.playerId()) != session) {
            return;
        }
        if (!this.playerSettings.isReady(session.playerId())) {
            failSession(session, new IllegalStateException("NetworkPlayerSettings is no longer ready for " + session.playerId()));
            return;
        }
        if (this.networkBoosters.isPresent() && !this.networkBoosters.orElseThrow().isReady(session.playerId())) {
            failSession(session, new IllegalStateException("NetworkBoosters is no longer ready for " + session.playerId()));
            return;
        }

        BalanceSnapshot effective = this.balanceStore.cached(session.playerId())
            .filter(snapshot -> snapshot.revision() >= loaded.revision())
            .orElse(loaded);
        if (!session.markReady()) {
            return;
        }
        try {
            this.readyEventDispatcher.dispatch(new PlayerPointsReadyEvent(session.playerId(), effective));
        } catch (RuntimeException exception) {
            this.logger.log(Level.SEVERE, "A listener failed while handling PlayerPointsReadyEvent", exception);
        }
    }

    private void failSession(PlayerSession session, Throwable failure) {
        if (!session.isCurrent() || this.sessions.get(session.playerId()) != session) {
            return;
        }
        session.fail();
        this.logger.log(Level.WARNING, "ProgressEngine could not make player " + session.playerId()
            + " ready in lifecycle epoch " + session.epoch(), unwrap(failure));
    }

    private void discardPreload(UUID playerId) {
        PendingPreload preload = this.preloads.remove(playerId);
        if (preload != null) {
            preload.close();
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    @FunctionalInterface
    public interface MainThreadExecutor {
        void execute(Runnable task);
    }

    @FunctionalInterface
    public interface DelayedTaskScheduler {
        AutoCloseable schedule(Runnable task, long delayTicks);
    }

    @FunctionalInterface
    public interface PlayerNameUpdater {
        CompletableFuture<Void> updateCurrentMapping(UUID playerId, String username, Instant lastSeenAt);
    }

    @FunctionalInterface
    public interface PlayerSettingsReadiness {
        boolean isReady(UUID playerId);
    }

    public interface PlayerBoostersReadiness {
        CompletableFuture<Void> load(UUID playerId);

        boolean isReady(UUID playerId);
    }

    @FunctionalInterface
    interface ReadyEventDispatcher {
        void dispatch(PlayerPointsReadyEvent event);
    }

    private final class PlayerSession {
        private final UUID playerId;
        private final String username;
        private final long epoch;
        private final BooleanSupplier onlineCheck;
        private final AtomicBoolean current = new AtomicBoolean(true);
        private volatile PlayerSessionStatus status = PlayerSessionStatus.LOADING;

        private PlayerSession(UUID playerId, String username, long epoch, BooleanSupplier onlineCheck) {
            this.playerId = playerId;
            this.username = username;
            this.epoch = epoch;
            this.onlineCheck = onlineCheck;
        }

        UUID playerId() {
            return this.playerId;
        }

        String username() {
            return this.username;
        }

        long epoch() {
            return this.epoch;
        }

        PlayerSessionStatus status() {
            return this.status;
        }

        boolean online() {
            return this.onlineCheck.getAsBoolean();
        }

        boolean isCurrent() {
            return this.current.get();
        }

        boolean markReady() {
            if (this.status != PlayerSessionStatus.LOADING || !this.current.get()) {
                return false;
            }
            this.status = PlayerSessionStatus.READY;
            return true;
        }

        void fail() {
            if (this.current.get()) {
                this.status = PlayerSessionStatus.FAILED;
            }
        }

        void invalidate() {
            this.current.set(false);
            this.status = PlayerSessionStatus.CLOSED;
        }
    }

    private enum PlayerSessionStatus {
        LOADING,
        READY,
        FAILED,
        CLOSED
    }

    private static final class PendingPreload implements AutoCloseable {
        private final CompletableFuture<BalanceSnapshot> snapshotFuture;
        private volatile AutoCloseable cleanup = () -> { };

        private PendingPreload(CompletableFuture<BalanceSnapshot> snapshotFuture) {
            this.snapshotFuture = Objects.requireNonNull(snapshotFuture, "snapshotFuture");
        }

        CompletableFuture<BalanceSnapshot> snapshotFuture() {
            return this.snapshotFuture;
        }

        void cleanup(AutoCloseable cleanup) {
            this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        }

        @Override
        public void close() {
            try {
                this.cleanup.close();
            } catch (Exception ignored) {
                // Cleanup tasks are best-effort and must not alter lifecycle state.
            }
        }
    }
}
