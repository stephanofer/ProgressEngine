package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.internal.CanonicalMetadataJson;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OperationDraft {
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private final OperationId operationId;
    private final int fingerprintVersion;
    private final byte[] requestFingerprint;
    private final OperationType type;
    private final UUID playerId;
    private final Optional<UUID> relatedPlayerId;
    private final Optional<String> gameId;
    private final long requestedAmount;
    private final OperationActor actor;
    private final OperationSource source;
    private final OperationReason reason;
    private final OperationMetadata metadata;
    private final Instant createdAt;

    public OperationDraft(
        OperationId operationId,
        int fingerprintVersion,
        byte[] requestFingerprint,
        OperationType type,
        UUID playerId,
        Optional<UUID> relatedPlayerId,
        Optional<String> gameId,
        long requestedAmount,
        OperationActor actor,
        OperationSource source,
        OperationReason reason,
        OperationMetadata metadata,
        Instant createdAt
    ) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        if (fingerprintVersion < 1 || fingerprintVersion > 65_535) {
            throw new IllegalArgumentException("fingerprintVersion must be between 1 and 65535");
        }
        this.fingerprintVersion = fingerprintVersion;
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.length != 32) {
            throw new IllegalArgumentException("requestFingerprint must contain exactly 32 bytes");
        }
        this.requestFingerprint = requestFingerprint.clone();
        this.type = Objects.requireNonNull(type, "type");
        this.playerId = BinaryUuid.requireValid(playerId, "playerId");
        UUID validatedPlayerId = this.playerId;
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(related -> {
            BinaryUuid.requireValid(related, "relatedPlayerId");
            if (related.equals(validatedPlayerId)) {
                throw new IllegalArgumentException("relatedPlayerId cannot equal playerId");
            }
        });
        this.relatedPlayerId = relatedPlayerId;
        Objects.requireNonNull(gameId, "gameId");
        gameId.ifPresent(OperationDraft::requireGameId);
        if (gameId.isPresent() && type != OperationType.AWARD) {
            throw new IllegalArgumentException(type + " cannot include gameId");
        }
        this.gameId = gameId;
        if (requestedAmount < 0L) throw new IllegalArgumentException("requestedAmount cannot be negative");
        if (type == OperationType.TRANSFER) {
            if (relatedPlayerId.isEmpty()) throw new IllegalArgumentException("transfer requires relatedPlayerId");
            if (requestedAmount <= 0L) throw new IllegalArgumentException("transfer requestedAmount must be positive");
        } else if (relatedPlayerId.isPresent()) {
            throw new IllegalArgumentException(type + " cannot include relatedPlayerId");
        }
        this.requestedAmount = requestedAmount;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.source = Objects.requireNonNull(source, "source");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public OperationId operationId() {
        return this.operationId;
    }

    public int fingerprintVersion() {
        return this.fingerprintVersion;
    }

    public byte[] requestFingerprint() {
        return this.requestFingerprint.clone();
    }

    public OperationType type() {
        return this.type;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public Optional<UUID> relatedPlayerId() {
        return this.relatedPlayerId;
    }

    public Optional<String> gameId() {
        return this.gameId;
    }

    public long requestedAmount() {
        return this.requestedAmount;
    }

    public OperationActor actor() {
        return this.actor;
    }

    public OperationSource source() {
        return this.source;
    }

    public OperationReason reason() {
        return this.reason;
    }

    public OperationMetadata metadata() {
        return this.metadata;
    }

    public String metadataJson() {
        return CanonicalMetadataJson.encode(this.metadata.values());
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public boolean hasSameFingerprint(StoredOperation operation) {
        return this.fingerprintVersion == operation.fingerprintVersion()
            && Arrays.equals(this.requestFingerprint, operation.requestFingerprint());
    }

    private static String requireGameId(String gameId) {
        if (!GAME_ID_PATTERN.matcher(gameId).matches()) {
            throw new IllegalArgumentException("gameId must match " + GAME_ID_PATTERN.pattern());
        }
        return gameId;
    }
}
