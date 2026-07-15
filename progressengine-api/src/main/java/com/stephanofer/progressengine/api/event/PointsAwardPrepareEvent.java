package com.stephanofer.progressengine.api.event;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.request.AwardRequest;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before an award is calculated with boosters and persisted.
 */
public final class PointsAwardPrepareEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OperationId operationId;
    private final UUID playerId;
    private final long requestedBaseAmount;
    private final OperationReason reason;
    private final OperationActor actor;
    private final OperationMetadata metadata;
    private long preparedBaseAmount;
    private boolean cancelled;

    /**
     * Creates the event from an award request.
     *
     * @param request award request
     */
    public PointsAwardPrepareEvent(AwardRequest request) {
        this(
            request.operationId(),
            request.playerId(),
            request.baseAmount(),
            request.reason(),
            request.actor(),
            request.metadata()
        );
    }

    /**
     * Creates the event.
     *
     * @param operationId operation id
     * @param playerId awarded player
     * @param requestedBaseAmount original base amount
     * @param reason operation reason
     * @param actor operation actor
     * @param metadata operation metadata
     */
    public PointsAwardPrepareEvent(OperationId operationId, UUID playerId, long requestedBaseAmount,
                                   OperationReason reason, OperationActor actor, OperationMetadata metadata) {
        super(false);
        if (operationId == null) throw new NullPointerException("operationId cannot be null");
        this.operationId = operationId;
        this.playerId = ApiValidation.requireUuid(playerId, "playerId");
        this.requestedBaseAmount = ApiValidation.requirePositive(requestedBaseAmount, "requestedBaseAmount");
        this.preparedBaseAmount = requestedBaseAmount;
        if (reason == null) throw new NullPointerException("reason cannot be null");
        if (actor == null) throw new NullPointerException("actor cannot be null");
        if (metadata == null) throw new NullPointerException("metadata cannot be null");
        this.reason = reason;
        this.actor = actor;
        this.metadata = metadata;
    }

    /** Returns the operation id. */
    public OperationId operationId() {
        return this.operationId;
    }

    /** Returns the awarded player UUID. */
    public UUID playerId() {
        return this.playerId;
    }

    /** Returns the base amount requested by the consumer before listeners. */
    public long requestedBaseAmount() {
        return this.requestedBaseAmount;
    }

    /** Returns the base amount that will continue into booster calculation. */
    public long preparedBaseAmount() {
        return this.preparedBaseAmount;
    }

    /** Updates the prepared base amount. */
    public void setPreparedBaseAmount(long preparedBaseAmount) {
        this.preparedBaseAmount = ApiValidation.requirePositive(preparedBaseAmount, "preparedBaseAmount");
    }

    /** Returns the operation reason. */
    public OperationReason reason() {
        return this.reason;
    }

    /** Returns the operation actor. */
    public OperationActor actor() {
        return this.actor;
    }

    /** Returns bounded metadata. */
    public OperationMetadata metadata() {
        return this.metadata;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** Returns the handler list required by Bukkit events. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
