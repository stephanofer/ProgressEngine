package com.stephanofer.progressengine.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WorkPermit implements AutoCloseable {
    private final InFlightTracker tracker;
    private final WorkKind kind;
    private final AtomicBoolean closed = new AtomicBoolean();

    WorkPermit(InFlightTracker tracker, WorkKind kind) {
        this.tracker = tracker;
        this.kind = kind;
    }

    public WorkKind kind() {
        return this.kind;
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.tracker.release(this.kind);
        }
    }
}
