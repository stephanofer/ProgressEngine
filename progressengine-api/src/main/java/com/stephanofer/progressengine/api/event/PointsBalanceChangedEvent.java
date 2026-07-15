package com.stephanofer.progressengine.api.event;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import java.util.Optional;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a committed balance change becomes observable on this server.
 */
public final class PointsBalanceChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final BalanceChange change;
    private final BalanceChangeOrigin origin;
    private final Optional<OperationId> operationId;

    /**
     * Creates the event.
     *
     * @param change observed balance change
     * @param origin origin of the observation
     * @param operationId known operation id, when available
     */
    public PointsBalanceChangedEvent(BalanceChange change, BalanceChangeOrigin origin, Optional<OperationId> operationId) {
        super(false);
        if (change == null) throw new NullPointerException("change cannot be null");
        if (origin == null) throw new NullPointerException("origin cannot be null");
        if (operationId == null) throw new NullPointerException("operationId cannot be null");
        this.change = change;
        this.origin = origin;
        this.operationId = operationId;
    }

    /** Creates the event with a known operation id. */
    public PointsBalanceChangedEvent(BalanceChange change, BalanceChangeOrigin origin, OperationId operationId) {
        this(change, origin, Optional.of(operationId));
    }

    /** Returns the observed balance change. */
    public BalanceChange change() {
        return this.change;
    }

    /** Returns the origin of the observation. */
    public BalanceChangeOrigin origin() {
        return this.origin;
    }

    /** Returns the known operation id, when available. */
    public Optional<OperationId> operationId() {
        return this.operationId;
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
