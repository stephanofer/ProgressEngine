package com.stephanofer.progressengine.persistence;

import java.util.Optional;

public record OperationResultPayload(Optional<Integer> version, Optional<String> json) {
    public OperationResultPayload {
        if (version == null) throw new NullPointerException("version cannot be null");
        if (json == null) throw new NullPointerException("json cannot be null");
        if (version.isPresent() != json.isPresent()) {
            throw new IllegalArgumentException("version and json must be both present or both empty");
        }
        version.ifPresent(value -> {
            if (value < 1 || value > 65_535) {
                throw new IllegalArgumentException("version must be between 1 and 65535");
            }
        });
        json.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("json cannot be blank");
            }
        });
    }

    public static OperationResultPayload empty() {
        return new OperationResultPayload(Optional.empty(), Optional.empty());
    }

    public static OperationResultPayload of(int version, String json) {
        return new OperationResultPayload(Optional.of(version), Optional.of(json));
    }
}
