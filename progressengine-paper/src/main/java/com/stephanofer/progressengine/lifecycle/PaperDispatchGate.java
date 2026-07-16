package com.stephanofer.progressengine.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PaperDispatchGate implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();

    public boolean acceptsDispatch() {
        return !this.closed.get();
    }

    @Override
    public void close() {
        this.closed.set(true);
    }
}
