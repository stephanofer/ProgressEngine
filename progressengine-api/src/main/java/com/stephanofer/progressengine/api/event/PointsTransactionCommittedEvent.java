package com.stephanofer.progressengine.api.event;

import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a local economic operation has been durably committed.
 */
public final class PointsTransactionCommittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OperationReceipt receipt;

    /**
     * Creates the event.
     *
     * @param receipt committed operation receipt
     */
    public PointsTransactionCommittedEvent(OperationReceipt receipt) {
        super(false);
        if (receipt == null) {
            throw new NullPointerException("receipt cannot be null");
        }
        this.receipt = receipt;
    }

    /** Returns the committed receipt. */
    public OperationReceipt receipt() {
        return this.receipt;
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
