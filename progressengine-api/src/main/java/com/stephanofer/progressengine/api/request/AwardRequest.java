package com.stephanofer.progressengine.api.request;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request to award gameplay points to one player.
 */
public record AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason,
                           Optional<String> gameId, OperationActor actor, OperationMetadata metadata) implements EconomicRequest {
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    /**
     * Creates an award request with plugin actor and empty metadata.
     */
    public AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason) {
        this(operationId, playerId, baseAmount, reason, Optional.empty(), OperationActor.plugin(), OperationMetadata.empty());
    }

    /**
     * Creates an award request scoped to one game with plugin actor and empty metadata.
     */
    public AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason, String gameId) {
        this(operationId, playerId, baseAmount, reason, Optional.ofNullable(gameId), OperationActor.plugin(), OperationMetadata.empty());
    }

    /**
     * Creates an award request.
     */
    public AwardRequest(OperationId operationId, UUID playerId, long baseAmount, OperationReason reason,
                        OperationActor actor, OperationMetadata metadata) {
        this(operationId, playerId, baseAmount, reason, Optional.empty(), actor, metadata);
    }

    /**
     * Creates an award request.
     */
    public AwardRequest {
        if (operationId == null) {
            throw new NullPointerException("operationId cannot be null");
        }
        playerId = ApiValidation.requireUuid(playerId, "playerId");
        baseAmount = ApiValidation.requirePositive(baseAmount, "baseAmount");
        if (reason == null) {
            throw new NullPointerException("reason cannot be null");
        }
        gameId = normalizeGameId(gameId);
        if (actor == null) {
            throw new NullPointerException("actor cannot be null");
        }
        if (metadata == null) {
            throw new NullPointerException("metadata cannot be null");
        }
    }

    private static Optional<String> normalizeGameId(Optional<String> gameId) {
        if (gameId == null) {
            throw new NullPointerException("gameId cannot be null");
        }
        return gameId.map(raw -> {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (!GAME_ID_PATTERN.matcher(normalized).matches()) {
                throw new IllegalArgumentException("gameId must match " + GAME_ID_PATTERN.pattern());
            }
            return normalized;
        });
    }
}
