package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ConfigurationReloadResult(
    boolean success,
    Optional<ConfigurationSnapshot> activeSnapshot,
    List<ConfigurationProblem> problems
) {
    public ConfigurationReloadResult {
        Objects.requireNonNull(activeSnapshot, "activeSnapshot");
        problems = List.copyOf(problems);
    }

    public static ConfigurationReloadResult success(ConfigurationSnapshot snapshot) {
        return new ConfigurationReloadResult(true, Optional.of(snapshot), List.of());
    }

    public static ConfigurationReloadResult failure(Optional<ConfigurationSnapshot> activeSnapshot, List<ConfigurationProblem> problems) {
        return new ConfigurationReloadResult(false, activeSnapshot, problems);
    }
}
