package com.stephanofer.progressengine.config;

import java.util.Objects;

public record LoadedConfiguration(ConfigurationSnapshot snapshot, String serializedConfig) {
    public LoadedConfiguration {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(serializedConfig, "serializedConfig");
    }
}
