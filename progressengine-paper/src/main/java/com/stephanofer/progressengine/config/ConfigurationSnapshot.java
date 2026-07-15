package com.stephanofer.progressengine.config;

import java.time.Instant;
import java.util.Objects;

public record ConfigurationSnapshot(long revision, Instant loadedAt, ProgressEngineConfig config) {
    public ConfigurationSnapshot {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(loadedAt, "loadedAt");
        Objects.requireNonNull(config, "config");
    }
}
