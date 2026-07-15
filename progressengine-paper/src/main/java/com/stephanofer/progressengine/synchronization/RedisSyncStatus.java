package com.stephanofer.progressengine.synchronization;

import com.hera.craftkit.redis.RedisOperationalStatus;
import java.time.Instant;
import java.util.Optional;

public record RedisSyncStatus(
    RedisOperationalStatus redis,
    boolean reconciliationRunning,
    long effectiveIntervalSeconds,
    Optional<Instant> lastReconciliationAttempt,
    Optional<Instant> lastSuccessfulReconciliation,
    long failedPublications,
    long invalidPayloads
) {
}
