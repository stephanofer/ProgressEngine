package com.stephanofer.progressengine.config;

import java.util.Objects;

public record ConfigurationProblem(String path, String message) {
    public ConfigurationProblem {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }
}
