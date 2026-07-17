package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.api.operation.OperationReason;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

final class CommandParsers {
    private static final Pattern UNSIGNED_INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern ABBREVIATED_AMOUNT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([a-zA-Z0-9]*)");

    private CommandParsers() {
    }

    static long positiveAmount(String input, long maximum) {
        long value = nonNegativeAmount(input, maximum);
        if (value <= 0L) throw new IllegalArgumentException("amount must be positive");
        return value;
    }

    static long positiveAmount(String input, long maximum, Map<String, Long> multipliers) {
        long value = nonNegativeAmount(input, maximum, multipliers);
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

    static long nonNegativeAmount(String input, long maximum, Map<String, Long> multipliers) {
        if (input == null || input.length() > 128 || multipliers == null) {
            throw new IllegalArgumentException("amount is invalid");
        }
        java.util.regex.Matcher matcher = ABBREVIATED_AMOUNT.matcher(input);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("amount must be an unsigned decimal number with an optional suffix");
        }
        String numericPart = matcher.group(1);
        String suffix = matcher.group(2).toLowerCase(Locale.ROOT);
        long multiplier;
        if (suffix.isEmpty()) {
            if (!UNSIGNED_INTEGER.matcher(numericPart).matches()) {
                throw new IllegalArgumentException("amount without a suffix must be an unsigned integer");
            }
            multiplier = 1L;
        } else {
            Long configured = multipliers.get(suffix);
            if (configured == null) {
                throw new IllegalArgumentException("unknown amount suffix");
            }
            multiplier = configured;
        }
        try {
            BigDecimal expanded = new BigDecimal(numericPart).multiply(BigDecimal.valueOf(multiplier));
            BigInteger value = expanded.setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact();
            if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || value.compareTo(BigInteger.valueOf(maximum)) > 0) {
                throw new IllegalArgumentException("amount is too large");
            }
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("amount must resolve to an integer", exception);
        }
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
