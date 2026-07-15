package com.stephanofer.progressengine.persistence;

import java.util.List;
import java.util.Optional;

public record LedgerPage(List<StoredLedgerEntry> entries, Optional<LedgerCursor> nextCursor) {
    public LedgerPage {
        if (entries == null) throw new NullPointerException("entries cannot be null");
        if (nextCursor == null) throw new NullPointerException("nextCursor cannot be null");
        entries = List.copyOf(entries);
    }
}
