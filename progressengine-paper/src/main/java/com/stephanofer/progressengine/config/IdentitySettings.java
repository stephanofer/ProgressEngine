package com.stephanofer.progressengine.config;

import java.util.List;
import java.util.Objects;

public record IdentitySettings(List<IdentityPart> parts, String separator, long offlineCacheMaximumSize,
                               long offlineCacheExpireAfterWriteSeconds) {
    public IdentitySettings {
        Objects.requireNonNull(parts, "parts");
        separator = Objects.requireNonNull(separator, "separator");
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("identity parts cannot be empty");
        }
        if (parts.stream().filter(part -> part == IdentityPart.NICK).count() != 1L) {
            throw new IllegalArgumentException("identity parts must contain nick exactly once");
        }
        if (parts.stream().distinct().count() != parts.size()) {
            throw new IllegalArgumentException("identity parts cannot contain duplicates");
        }
        if (offlineCacheMaximumSize < 1L || offlineCacheMaximumSize > 1_000_000L) {
            throw new IllegalArgumentException("offlineCacheMaximumSize must be between 1 and 1000000");
        }
        if (offlineCacheExpireAfterWriteSeconds < 1L || offlineCacheExpireAfterWriteSeconds > 86_400L) {
            throw new IllegalArgumentException("offlineCacheExpireAfterWriteSeconds must be between 1 and 86400");
        }
    }

    public enum IdentityPart {
        PREFIX,
        NICK,
        COUNTRY_FLAG;

        public static IdentityPart parse(String value) {
            return switch (Objects.requireNonNull(value, "value").trim().toLowerCase(java.util.Locale.ROOT)) {
                case "prefix" -> PREFIX;
                case "nick" -> NICK;
                case "country_flag" -> COUNTRY_FLAG;
                default -> throw new IllegalArgumentException("unknown identity part: " + value);
            };
        }
    }
}
