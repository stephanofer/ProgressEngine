package com.stephanofer.progressengine.synchronization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stephanofer.progressengine.api.operation.OperationId;
import java.time.Duration;
import java.util.Objects;

public final class TransferNoticeDeduplicator implements AutoCloseable {
    private final Cache<OperationId, Boolean> seen = Caffeine.newBuilder()
        .maximumSize(10_000L)
        .expireAfterWrite(Duration.ofMinutes(10L))
        .build();

    public boolean markFirst(OperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return this.seen.asMap().putIfAbsent(operationId, Boolean.TRUE) == null;
    }

    public void release(OperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        this.seen.invalidate(operationId);
    }

    @Override
    public void close() {
        this.seen.invalidateAll();
        this.seen.cleanUp();
    }
}
