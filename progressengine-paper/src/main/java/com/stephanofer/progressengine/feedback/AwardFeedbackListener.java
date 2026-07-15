package com.stephanofer.progressengine.feedback;

import com.stephanofer.progressengine.api.event.PointsTransactionCommittedEvent;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import java.util.List;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class AwardFeedbackListener implements Listener {
    private final AwardFeedbackCoalescer coalescer;

    public AwardFeedbackListener(AwardFeedbackCoalescer coalescer) {
        this.coalescer = Objects.requireNonNull(coalescer, "coalescer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommitted(PointsTransactionCommittedEvent event) {
        if (event.receipt().type() != OperationType.AWARD) {
            return;
        }
        List<BalanceChange> changes = event.receipt().changes();
        if (changes.size() != 1) {
            return;
        }
        BalanceChange change = changes.getFirst();
        if (change.delta() <= 0L) {
            return;
        }
        this.coalescer.record(change.playerId(), change.delta(), change.balanceAfter());
    }
}
