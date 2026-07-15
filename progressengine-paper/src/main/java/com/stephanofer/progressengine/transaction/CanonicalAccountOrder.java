package com.stephanofer.progressengine.transaction;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CanonicalAccountOrder {
    private static final UUID NIL = new UUID(0L, 0L);

    private CanonicalAccountOrder() {
    }

    public static int compare(UUID first, UUID second) {
        requireValid(first, "first");
        requireValid(second, "second");
        int most = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        if (most != 0) {
            return most;
        }
        return Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }

    public static List<UUID> sort(Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds");
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            deduplicated.add(requireValid(playerId, "playerId"));
        }
        if (deduplicated.isEmpty()) {
            throw new IllegalArgumentException("At least one playerId is required");
        }
        return deduplicated.stream()
            .sorted(Comparator.comparing(uuid -> uuid, CanonicalAccountOrder::compare))
            .toList();
    }

    private static UUID requireValid(UUID uuid, String name) {
        Objects.requireNonNull(uuid, name);
        if (NIL.equals(uuid)) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
        return uuid;
    }
}
