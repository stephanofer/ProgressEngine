package com.stephanofer.progressengine.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record HistoryPage(List<HistoryEntry> entries, Optional<LedgerCursor> nextCursor) {
    public HistoryPage {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(nextCursor, "nextCursor");
        entries = List.copyOf(entries);
    }
}
