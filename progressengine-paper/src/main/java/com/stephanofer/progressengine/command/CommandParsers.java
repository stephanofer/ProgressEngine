package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.api.operation.OperationReason;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class CommandParsers {
    private static final Pattern UNSIGNED_INTEGER = Pattern.compile("[0-9]+");

    private CommandParsers() {
    }

    static long positiveAmount(String input, long maximum) {
        long value = nonNegativeAmount(input, maximum);
        if (value <= 0L) throw new IllegalArgumentException("amount must be positive");
        return value;
    }

    static long nonNegativeAmount(String input, long maximum) {
        if (input == null || !UNSIGNED_INTEGER.matcher(input).matches()) {
            throw new IllegalArgumentException("amount must be an unsigned integer");
        }
        BigInteger value = new BigInteger(input);
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || value.compareTo(BigInteger.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException("amount is too large");
        }
        return value.longValue();
    }

    static int page(String input) {
        long value = positiveAmount(input, 1_000_000L);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("page is too large");
        return (int) value;
    }

    static OperationReason reason(String input) {
        return OperationReason.of(input);
    }

    static Optional<UUID> uuid(String input) {
        try {
            UUID value = UUID.fromString(input);
            return value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L
                ? Optional.empty()
                : Optional.of(value);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
