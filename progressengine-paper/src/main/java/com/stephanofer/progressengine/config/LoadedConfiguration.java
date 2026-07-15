package com.stephanofer.progressengine.config;

import java.util.Map;
import java.util.Objects;

public record LoadedConfiguration(ConfigurationSnapshot snapshot, Map<String, String> serializedDocuments) {
    public LoadedConfiguration {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(serializedDocuments, "serializedDocuments");
        serializedDocuments = Map.copyOf(serializedDocuments);
        if (!serializedDocuments.containsKey("config.yml")) {
            throw new IllegalArgumentException("serializedDocuments must contain config.yml");
        }
    }

    public LoadedConfiguration(ConfigurationSnapshot snapshot, String serializedConfig) {
        this(snapshot, Map.of("config.yml", serializedConfig));
    }

    public String serializedConfig() {
        return this.serializedDocuments.get("config.yml");
    }
}
