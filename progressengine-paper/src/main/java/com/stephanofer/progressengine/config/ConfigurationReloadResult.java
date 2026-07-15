package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ConfigurationReloadResult(
    boolean success,
    Optional<ConfigurationSnapshot> activeSnapshot,
    List<ConfigurationProblem> problems,
    ConfigurationChangeSet changes
) {
    public ConfigurationReloadResult {
        Objects.requireNonNull(activeSnapshot, "activeSnapshot");
        problems = List.copyOf(problems);
        Objects.requireNonNull(changes, "changes");
    }

    public static ConfigurationReloadResult success(ConfigurationSnapshot snapshot) {
        return success(snapshot, ConfigurationChangeSet.initial());
    }

    public static ConfigurationReloadResult success(ConfigurationSnapshot snapshot, ConfigurationChangeSet changes) {
        return new ConfigurationReloadResult(true, Optional.of(snapshot), List.of(), changes);
    }

    public static ConfigurationReloadResult failure(Optional<ConfigurationSnapshot> activeSnapshot, List<ConfigurationProblem> problems) {
        return new ConfigurationReloadResult(false, activeSnapshot, problems, new ConfigurationChangeSet(List.of(), List.of()));
    }
}
