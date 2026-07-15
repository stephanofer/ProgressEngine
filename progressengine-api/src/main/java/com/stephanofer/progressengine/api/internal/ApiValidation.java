package com.stephanofer.progressengine.api.internal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Shared validation helpers used by ProgressEngine public API contracts.
 */
public final class ApiValidation {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private ApiValidation() {
    }

    /**
     * Validates that a UUID is present and is not the nil UUID.
     *
     * @param value the UUID to validate
     * @param name the parameter name used in exception messages
     * @return the validated UUID
     */
    public static UUID requireUuid(UUID value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " cannot be null");
        }
        if (NIL_UUID.equals(value)) {
            throw new IllegalArgumentException(name + " cannot be the nil UUID");
        }
        return value;
    }

    /**
     * Validates that an amount is strictly positive.
     *
     * @param value the amount to validate
     * @param name the parameter name used in exception messages
     * @return the validated amount
     */
    public static long requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * Validates that a numeric value is zero or positive.
     *
     * @param value the value to validate
     * @param name the parameter name used in exception messages
     * @return the validated value
     */
    public static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    /**
     * Validates that a revision belongs to a confirmed ledger movement.
     *
     * @param value the revision to validate
     * @param name the parameter name used in exception messages
     * @return the validated revision
     */
    public static long requirePositiveRevision(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * Validates that a string is present, trimmed, and not too long in UTF-8 bytes.
     *
     * @param value the string to validate
     * @param name the parameter name used in exception messages
     * @param maxBytes the maximum UTF-8 byte length
     * @return the trimmed string
     */
    public static String requireText(String value, String name, int maxBytes) {
        if (value == null) {
            throw new NullPointerException(name + " cannot be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (utf8Bytes(trimmed) > maxBytes) {
            throw new IllegalArgumentException(name + " exceeds " + maxBytes + " UTF-8 bytes");
        }
        return trimmed;
    }

    /**
     * Returns the UTF-8 byte length of a string.
     *
     * @param value the value to measure
     * @return the UTF-8 byte length
     */
    public static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
