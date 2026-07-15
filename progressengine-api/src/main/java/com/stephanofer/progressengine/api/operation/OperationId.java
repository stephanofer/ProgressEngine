package com.stephanofer.progressengine.api.operation;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.util.UUID;

/**
 * Globally unique identifier for one economic intention.
 */
public record OperationId(UUID value) {

    /**
     * Creates an operation identifier from an existing UUID.
     *
     * @param value the underlying UUID
     */
    public OperationId {
        value = ApiValidation.requireUuid(value, "value");
    }

    /**
     * Generates a new random operation identifier for a new economic intention.
     *
     * @return a new operation identifier
     */
    public static OperationId generate() {
        return new OperationId(UUID.randomUUID());
    }

    /**
     * Wraps an existing durable UUID as an operation identifier.
     *
     * @param value the UUID to wrap
     * @return the operation identifier
     */
    public static OperationId of(UUID value) {
        return new OperationId(value);
    }

    /**
     * Parses a canonical UUID string as an operation identifier.
     *
     * @param value the canonical UUID string
     * @return the parsed operation identifier
     */
    public static OperationId parse(String value) {
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("value must be a canonical UUID");
            }
            return of(parsed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("value must be a canonical UUID", exception);
        }
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
