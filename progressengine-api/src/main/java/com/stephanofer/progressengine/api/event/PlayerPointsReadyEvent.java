package com.stephanofer.progressengine.api.event;

import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's ProgressEngine state becomes ready for gameplay integrations.
 */
public final class PlayerPointsReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final BalanceSnapshot snapshot;

    /**
     * Creates the event.
     *
     * @param playerId ready player
     * @param snapshot ready balance snapshot
     */
    public PlayerPointsReadyEvent(UUID playerId, BalanceSnapshot snapshot) {
        super(false);
        this.playerId = ApiValidation.requireUuid(playerId, "playerId");
        if (snapshot == null) {
            throw new NullPointerException("snapshot cannot be null");
        }
        if (!snapshot.playerId().equals(this.playerId)) {
            throw new IllegalArgumentException("snapshot playerId must match event playerId");
        }
        this.snapshot = snapshot;
    }

    /** Returns the ready player UUID. */
    public UUID playerId() {
        return this.playerId;
    }

    /** Returns the ready balance snapshot. */
    public BalanceSnapshot snapshot() {
        return this.snapshot;
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
