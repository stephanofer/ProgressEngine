package com.stephanofer.progressengine.persistence;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PlayerUsernames {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern NORMALIZED = Pattern.compile("[a-z0-9_]{3,16}");

    private PlayerUsernames() {
    }

    public static String requireValid(String username) {
        Objects.requireNonNull(username, "username");
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("username must match [A-Za-z0-9_]{3,16}");
        }
        return username;
    }

    public static String normalize(String username) {
        Objects.requireNonNull(username, "username");
        String normalized = username.toLowerCase(Locale.ROOT);
        if (!NORMALIZED.matcher(normalized).matches()) {
            throw new IllegalArgumentException("normalized username must match [a-z0-9_]{3,16}");
        }
        return normalized;
    }
}
