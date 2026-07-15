package com.stephanofer.progressengine.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class InFlightTracker {
    private final RuntimeLifecycle lifecycle;
    private int loads;
    private int mutations;

    public InFlightTracker(RuntimeLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public synchronized Optional<WorkPermit> acquire(WorkKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!this.lifecycle.acceptsNewWork(kind)) {
            return Optional.empty();
        }
        if (kind == WorkKind.LOAD) {
            this.loads++;
        } else {
            this.mutations++;
        }
        return Optional.of(new WorkPermit(this, kind));
    }

    public synchronized InFlightCounts counts() {
        return new InFlightCounts(this.loads, this.mutations);
    }

    public synchronized boolean awaitDrained(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            return counts().total() == 0;
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        while (counts().total() > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return false;
            }
            long millis = Math.max(1L, remaining / 1_000_000L);
            wait(millis);
        }
        return true;
    }

    synchronized void release(WorkKind kind) {
        if (kind == WorkKind.LOAD) {
            if (this.loads <= 0) {
                throw new IllegalStateException("No load work is in flight");
            }
            this.loads--;
        } else {
            if (this.mutations <= 0) {
                throw new IllegalStateException("No mutation work is in flight");
            }
            this.mutations--;
        }
        notifyAll();
    }
}
