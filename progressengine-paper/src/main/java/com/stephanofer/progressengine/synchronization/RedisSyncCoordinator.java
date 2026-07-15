package com.stephanofer.progressengine.synchronization;

import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisMessage;
import com.hera.craftkit.redis.RedisOperationalState;
import com.hera.craftkit.redis.RedisOperationalStatus;
import com.hera.craftkit.redis.RedisStatusRegistration;
import com.hera.craftkit.redis.RedisSubscription;
import com.stephanofer.progressengine.account.PostCommitNetworkPublisher;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.feedback.FeedbackService;
import com.stephanofer.progressengine.identity.PlayerIdentityRenderer;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import com.stephanofer.progressengine.lifecycle.RuntimeState;
import com.stephanofer.progressengine.lifecycle.WorkKind;
import com.stephanofer.progressengine.lifecycle.WorkPermit;
import com.stephanofer.progressengine.persistence.KnownPlayerName;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

public final class RedisSyncCoordinator implements PostCommitNetworkPublisher, AutoCloseable {
    private final RedisClient redis;
    private final RuntimeLifecycle lifecycle;
    private final InFlightTracker inFlightTracker;
    private final RedisMessageCodec codec;
    private final RemoteBalanceRefresher remoteRefresher;
    private final BalanceReconciler reconciler;
    private final ProgressPersistence persistence;
    private final com.stephanofer.progressengine.lifecycle.PlayerLifecycleCoordinator playerLifecycle;
    private final FeedbackService feedbackService;
    private final PlayerIdentityRenderer identityRenderer;
    private final Supplier<ProgressEngineConfig> configSupplier;
    private final DelayedTaskScheduler delayedTaskScheduler;
    private final Logger logger;
    private final Clock clock;
    private final String serverId;
    private final String invalidationChannel;
    private final String transferNoticeChannel;
    private final TransferNoticeDeduplicator transferDeduplicator = new TransferNoticeDeduplicator();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean reconciliationRunning = new AtomicBoolean();
    private final AtomicLong recoveryGeneration = new AtomicLong();
    private final AtomicLong failedPublications = new AtomicLong();
    private final AtomicLong invalidPayloads = new AtomicLong();
    private final AtomicReference<AutoCloseable> scheduledReconciliation = new AtomicReference<>();
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final RedisStatusRegistration statusRegistration;
    private final RedisSubscription invalidationSubscription;
    private final RedisSubscription transferSubscription;

    public RedisSyncCoordinator(
        RedisClient redis,
        RuntimeLifecycle lifecycle,
        InFlightTracker inFlightTracker,
        RedisMessageCodec codec,
        RemoteBalanceRefresher remoteRefresher,
        BalanceReconciler reconciler,
        ProgressPersistence persistence,
        com.stephanofer.progressengine.lifecycle.PlayerLifecycleCoordinator playerLifecycle,
        FeedbackService feedbackService,
        PlayerIdentityRenderer identityRenderer,
        Supplier<ProgressEngineConfig> configSupplier,
        DelayedTaskScheduler delayedTaskScheduler,
        Logger logger,
        Clock clock
    ) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.remoteRefresher = Objects.requireNonNull(remoteRefresher, "remoteRefresher");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.playerLifecycle = Objects.requireNonNull(playerLifecycle, "playerLifecycle");
        this.feedbackService = Objects.requireNonNull(feedbackService, "feedbackService");
        this.identityRenderer = Objects.requireNonNull(identityRenderer, "identityRenderer");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.delayedTaskScheduler = Objects.requireNonNull(delayedTaskScheduler, "delayedTaskScheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.serverId = configSupplier.get().serverId();
        this.invalidationChannel = redis.channel("progressengine", "balance-invalidated");
        this.transferNoticeChannel = redis.channel("progressengine", "transfer-received");
        this.invalidationSubscription = redis.subscriber().subscribe(this.invalidationChannel, this::handleInvalidation);
        this.transferSubscription = redis.subscriber().subscribe(this.transferNoticeChannel, this::handleTransferNotice);
        this.statusRegistration = redis.observeOperationalStatus(this::handleRedisStatus);
    }

    public void activate() {
        if (this.closed.get() || !this.active.compareAndSet(false, true)) {
            return;
        }
        RedisOperationalStatus status = this.redis.operationalStatus();
        if (status.isOperational()) {
            startRecovery(this.recoveryGeneration.get());
        } else {
            transitionToDegraded();
            scheduleNextReconciliation();
        }
    }

    public RedisSyncStatus status() {
        return new RedisSyncStatus(
            this.redis.operationalStatus(),
            this.reconciliationRunning.get(),
            effectiveIntervalSeconds(),
            Optional.ofNullable(this.lastAttempt.get()),
            Optional.ofNullable(this.lastSuccess.get()),
            this.failedPublications.get(),
            this.invalidPayloads.get()
        );
    }

    @Override
    public CompletableFuture<Void> publish(OperationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (this.closed.get() || !this.redis.operationalStatus().isOperational()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Long>> publications = new ArrayList<>();
        for (BalanceChange change : receipt.changes()) {
            BalanceInvalidationMessage message = new BalanceInvalidationMessage(
                change.playerId(),
                change.revision(),
                receipt.operationId(),
                receipt.source().serverId()
            );
            publications.add(publish(this.invalidationChannel, this.codec.encode(message)));
        }
        if (receipt.type() == OperationType.TRANSFER) {
            receiverChange(receipt).ifPresent(receiver -> publications.add(publish(this.transferNoticeChannel, this.codec.encode(
                new TransferNoticeMessage(
                    receipt.operationId(),
                    receiver.relatedPlayerId().orElseThrow(),
                    receiver.playerId(),
                    receiver.delta(),
                    receiver.revision(),
                    receipt.source().serverId()
                )
            ))));
        }
        if (publications.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(publications.toArray(CompletableFuture[]::new)).handle((ignored, failure) -> null);
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        cancelScheduledReconciliation();
        this.statusRegistration.close();
        this.invalidationSubscription.close();
        this.transferSubscription.close();
        this.transferDeduplicator.close();
        this.redis.close();
    }

    private CompletableFuture<Long> publish(String channel, String payload) {
        return this.redis.publisher().publish(channel, payload).whenComplete((subscribers, failure) -> {
            if (failure != null) {
                this.failedPublications.incrementAndGet();
                this.logger.log(Level.FINE, "ProgressEngine Redis publication failed on " + channel, failure);
            }
        });
    }

    private void handleInvalidation(RedisMessage redisMessage) {
        BalanceInvalidationMessage message;
        try {
            message = this.codec.decodeInvalidation(redisMessage.payload());
        } catch (IllegalArgumentException exception) {
            this.invalidPayloads.incrementAndGet();
            this.logger.log(Level.FINE, "Discarded invalid ProgressEngine balance invalidation", exception);
            return;
        }
        if (this.serverId.equals(message.sourceServerId())) {
            return;
        }
        this.remoteRefresher.refreshCached(message).whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.logger.log(Level.WARNING, "Failed to process remote balance invalidation for " + message.playerId(), failure);
            }
        });
    }

    private void handleTransferNotice(RedisMessage redisMessage) {
        TransferNoticeMessage message;
        try {
            message = this.codec.decodeTransferNotice(redisMessage.payload());
        } catch (IllegalArgumentException exception) {
            this.invalidPayloads.incrementAndGet();
            this.logger.log(Level.FINE, "Discarded invalid ProgressEngine transfer notice", exception);
            return;
        }
        if (this.serverId.equals(message.sourceServerId()) || !this.transferDeduplicator.markFirst(message.operationId())) {
            return;
        }
        if (!this.playerLifecycle.isReady(message.receiverId())) {
            return;
        }

        this.remoteRefresher.refreshForFeedback(message.receiverId(), message.receiverRevision())
            .thenCompose(snapshot -> snapshot
                .map(balance -> senderIdentity(message.senderId()).thenApply(sender -> new TransferFeedback(sender, balance.balance())))
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("Receiver balance is not available"))))
            .whenComplete((feedback, failure) -> {
                if (failure != null) {
                    this.transferDeduplicator.release(message.operationId());
                    this.logger.log(Level.WARNING, "Failed to deliver transfer feedback for operation " + message.operationId(), failure);
                    return;
                }
                this.feedbackService.sendTransferReceived(message.receiverId(), feedback.sender(), message.amount(), feedback.balance());
            });
    }

    private CompletableFuture<Component> senderIdentity(UUID senderId) {
        return this.persistence.playerNames().findByPlayerId(senderId)
            .thenCompose(name -> this.identityRenderer.renderOffline(senderId, name.map(KnownPlayerName::username)))
            .exceptionally(failure -> Component.text(senderId.toString()));
    }

    private void handleRedisStatus(RedisOperationalStatus status) {
        if (this.closed.get() || !this.active.get()) {
            return;
        }
        if (status.isOperational()) {
            startRecovery(this.recoveryGeneration.get());
            return;
        }
        if (status.state() != RedisOperationalState.CLOSED) {
            this.recoveryGeneration.incrementAndGet();
            transitionToDegraded();
            scheduleNextReconciliation();
        }
    }

    private void startRecovery(long generation) {
        cancelScheduledReconciliation();
        runReconciliation().whenComplete((result, failure) -> {
            if (failure == null && result != null && !this.closed.get() && this.redis.operationalStatus().isOperational()
                && this.recoveryGeneration.get() == generation) {
                transitionToReady();
            } else if (failure != null) {
                transitionToDegraded();
            }
            scheduleNextReconciliation();
        });
    }

    private CompletableFuture<BalanceReconciler.Result> runReconciliation() {
        if (!this.reconciliationRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<WorkPermit> permit = this.inFlightTracker.acquire(WorkKind.LOAD);
        if (permit.isEmpty()) {
            this.reconciliationRunning.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("ProgressEngine is not accepting reconciliation work"));
        }
        this.lastAttempt.set(Instant.now(this.clock));
        return this.reconciler.reconcile().whenComplete((result, failure) -> {
            permit.orElseThrow().close();
            this.reconciliationRunning.set(false);
            if (failure == null && result != null) {
                this.lastSuccess.set(result.completedAt());
            }
        });
    }

    private void scheduleNextReconciliation() {
        if (this.closed.get() || !this.active.get()) {
            return;
        }
        cancelScheduledReconciliation();
        long delayTicks = Math.multiplyExact(effectiveIntervalSeconds(), 20L);
        AutoCloseable scheduled = this.delayedTaskScheduler.schedule(this::runScheduledReconciliation, delayTicks);
        this.scheduledReconciliation.set(scheduled);
    }

    private void runScheduledReconciliation() {
        this.scheduledReconciliation.set(null);
        runReconciliation().whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.logger.log(Level.WARNING, "ProgressEngine reconciliation failed", failure);
            }
            scheduleNextReconciliation();
        });
    }

    private void cancelScheduledReconciliation() {
        AutoCloseable scheduled = this.scheduledReconciliation.getAndSet(null);
        if (scheduled == null) {
            return;
        }
        try {
            scheduled.close();
        } catch (Exception exception) {
            this.logger.log(Level.FINE, "Failed to cancel ProgressEngine reconciliation task", exception);
        }
    }

    private long effectiveIntervalSeconds() {
        ProgressEngineConfig.ReconciliationSettings settings = this.configSupplier.get().reconciliation();
        return this.redis.operationalStatus().isOperational() ? settings.normalIntervalSeconds() : settings.degradedIntervalSeconds();
    }

    private void transitionToReady() {
        RuntimeState state = this.lifecycle.state();
        if (state == RuntimeState.STARTING || state == RuntimeState.DEGRADED_REDIS) {
            this.lifecycle.transitionTo(RuntimeState.READY);
        }
    }

    private void transitionToDegraded() {
        RuntimeState state = this.lifecycle.state();
        if (state == RuntimeState.STARTING || state == RuntimeState.READY) {
            this.lifecycle.transitionTo(RuntimeState.DEGRADED_REDIS);
        }
    }

    private static Optional<BalanceChange> receiverChange(OperationReceipt receipt) {
        return receipt.changes().stream().filter(change -> change.delta() > 0L).findFirst();
    }

    @FunctionalInterface
    public interface DelayedTaskScheduler {
        AutoCloseable schedule(Runnable task, long delayTicks);
    }

    private record TransferFeedback(Component sender, long balance) {
    }
}
