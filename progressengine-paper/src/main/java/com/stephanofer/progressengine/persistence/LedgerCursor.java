package com.stephanofer.progressengine.persistence;

import java.time.Instant;
import java.util.Objects;

public record LedgerCursor(Instant createdAt, long ledgerId) {
    public LedgerCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        if (ledgerId < 1L) throw new IllegalArgumentException("ledgerId must be positive");
    }
}
