package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Objects;

public record ConfigurationChangeSet(List<String> applied, List<String> restartRequired) {
    public ConfigurationChangeSet {
        Objects.requireNonNull(applied, "applied");
        Objects.requireNonNull(restartRequired, "restartRequired");
        applied = List.copyOf(applied);
        restartRequired = List.copyOf(restartRequired);
    }

    public static ConfigurationChangeSet initial() {
        return new ConfigurationChangeSet(List.of("initial"), List.of());
    }
}
