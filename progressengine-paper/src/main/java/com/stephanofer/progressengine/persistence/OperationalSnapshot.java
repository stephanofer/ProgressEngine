package com.stephanofer.progressengine.persistence;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record OperationalSnapshot(boolean databaseHealthy, Optional<String> schemaVersion, Duration probeLatency,
                                  Optional<String> failureMessage) {
    public OperationalSnapshot {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(probeLatency, "probeLatency");
        Objects.requireNonNull(failureMessage, "failureMessage");
    }
}
