package com.stephanofer.progressengine.persistence;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.api.source.OperationSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class StoredOperation {
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private final OperationId operationId;
    private final int fingerprintVersion;
    private final byte[] requestFingerprint;
    private final OperationType type;
    private final OperationStatus status;
    private final UUID playerId;
    private final Optional<UUID> relatedPlayerId;
    private final Optional<String> gameId;
    private final long requestedAmount;
    private final OperationActor actor;
    private final OperationSource source;
    private final OperationReason reason;
    private final String metadataJson;
    private final OperationResultPayload resultPayload;
    private final Instant createdAt;
    private final Optional<Instant> completedAt;

    public StoredOperation(
        OperationId operationId,
        int fingerprintVersion,
        byte[] requestFingerprint,
        OperationType type,
        OperationStatus status,
        UUID playerId,
        Optional<UUID> relatedPlayerId,
        Optional<String> gameId,
        long requestedAmount,
        OperationActor actor,
        OperationSource source,
        OperationReason reason,
        String metadataJson,
        OperationResultPayload resultPayload,
        Instant createdAt,
        Optional<Instant> completedAt
    ) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        if (fingerprintVersion < 1 || fingerprintVersion > 65_535) {
            throw new PersistenceDataException("Stored fingerprint version is invalid");
        }
        this.fingerprintVersion = fingerprintVersion;
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        if (requestFingerprint.length != 32) {
            throw new PersistenceDataException("Stored request fingerprint must contain exactly 32 bytes");
        }
        this.requestFingerprint = requestFingerprint.clone();
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.playerId = BinaryUuid.requireValid(playerId, "playerId");
        UUID validatedPlayerId = this.playerId;
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(related -> {
            BinaryUuid.requireValid(related, "relatedPlayerId");
            if (related.equals(validatedPlayerId)) throw new PersistenceDataException("Stored relatedPlayerId cannot equal playerId");
        });
        this.relatedPlayerId = relatedPlayerId;
        Objects.requireNonNull(gameId, "gameId");
        gameId.ifPresent(StoredOperation::requireGameId);
        if (gameId.isPresent() && type != OperationType.AWARD) {
            throw new PersistenceDataException("Stored " + type + " cannot include gameId");
        }
        this.gameId = gameId;
        if (requestedAmount < 0L) throw new PersistenceDataException("Stored requested amount cannot be negative");
        if (type == OperationType.TRANSFER) {
            if (relatedPlayerId.isEmpty()) throw new PersistenceDataException("Stored transfer is missing relatedPlayerId");
            if (requestedAmount <= 0L) throw new PersistenceDataException("Stored transfer requested amount must be positive");
        } else if (relatedPlayerId.isPresent()) {
            throw new PersistenceDataException("Stored " + type + " cannot include relatedPlayerId");
        }
        this.requestedAmount = requestedAmount;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.source = Objects.requireNonNull(source, "source");
        this.reason = Objects.requireNonNull(reason, "reason");
        if (metadataJson == null || metadataJson.isBlank()) {
            throw new PersistenceDataException("Stored metadata json cannot be blank");
        }
        this.metadataJson = metadataJson;
        this.resultPayload = Objects.requireNonNull(resultPayload, "resultPayload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (status == OperationStatus.PENDING && completedAt.isPresent()) {
            throw new PersistenceDataException("Stored pending operation cannot have completedAt");
        }
        if (status != OperationStatus.PENDING && completedAt.isEmpty()) {
            throw new PersistenceDataException("Stored completed operation must have completedAt");
        }
        completedAt.ifPresent(completed -> {
            if (completed.isBefore(createdAt)) {
                throw new PersistenceDataException("Stored operation completedAt cannot be before createdAt");
            }
        });
    }

    public OperationId operationId() { return this.operationId; }
    public int fingerprintVersion() { return this.fingerprintVersion; }
    public byte[] requestFingerprint() { return this.requestFingerprint.clone(); }
    public OperationType type() { return this.type; }
    public OperationStatus status() { return this.status; }
    public UUID playerId() { return this.playerId; }
    public Optional<UUID> relatedPlayerId() { return this.relatedPlayerId; }
    public Optional<String> gameId() { return this.gameId; }
    public long requestedAmount() { return this.requestedAmount; }
    public OperationActor actor() { return this.actor; }
    public OperationSource source() { return this.source; }
    public OperationReason reason() { return this.reason; }
    public String metadataJson() { return this.metadataJson; }
    public OperationResultPayload resultPayload() { return this.resultPayload; }
    public Instant createdAt() { return this.createdAt; }
    public Optional<Instant> completedAt() { return this.completedAt; }

    private static String requireGameId(String gameId) {
        if (!GAME_ID_PATTERN.matcher(gameId).matches()) {
            throw new PersistenceDataException("Stored gameId is invalid: " + gameId);
        }
        return gameId;
    }
}
