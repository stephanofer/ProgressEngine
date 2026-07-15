package com.stephanofer.progressengine.transaction;

import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.ActorType;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OperationFingerprint {
    public static final int CURRENT_VERSION = 2;
    private static final UUID NIL = new UUID(0L, 0L);

    private OperationFingerprint() {
    }

    public static byte[] current(OperationType type, UUID playerId, Optional<UUID> relatedPlayerId, long amount,
                                  OperationReason reason, OperationActor actor, String sourcePlugin) {
        return current(type, playerId, relatedPlayerId, Optional.empty(), amount, reason, actor, sourcePlugin);
    }

    public static byte[] current(OperationType type, UUID playerId, Optional<UUID> relatedPlayerId, Optional<String> gameId, long amount,
                                  OperationReason reason, OperationActor actor, String sourcePlugin) {
        return versioned(CURRENT_VERSION, type, playerId, relatedPlayerId, gameId, amount, reason, actor, sourcePlugin);
    }

    public static byte[] versioned(int version, OperationType type, UUID playerId, Optional<UUID> relatedPlayerId,
                                   long amount, OperationReason reason, OperationActor actor, String sourcePlugin) {
        return versioned(version, type, playerId, relatedPlayerId, Optional.empty(), amount, reason, actor, sourcePlugin);
    }

    public static byte[] versioned(int version, OperationType type, UUID playerId, Optional<UUID> relatedPlayerId,
                                   Optional<String> gameId, long amount, OperationReason reason, OperationActor actor,
                                   String sourcePlugin) {
        if (version == 1) {
            return sha256(canonicalV1(type, playerId, relatedPlayerId, amount, reason, actor, sourcePlugin));
        }
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported operation fingerprint version: " + version);
        }
        return sha256(canonicalV2(type, playerId, relatedPlayerId, gameId, amount, reason, actor, sourcePlugin));
    }

    public static boolean matches(byte[] storedFingerprint, int storedVersion, OperationType type, UUID playerId,
                                  Optional<UUID> relatedPlayerId, long amount, OperationReason reason,
                                  OperationActor actor, String sourcePlugin) {
        byte[] recalculated = versioned(storedVersion, type, playerId, relatedPlayerId, amount, reason, actor, sourcePlugin);
        return MessageDigest.isEqual(Objects.requireNonNull(storedFingerprint, "storedFingerprint"), recalculated);
    }

    public static boolean matches(byte[] storedFingerprint, int storedVersion, OperationType type, UUID playerId,
                                  Optional<UUID> relatedPlayerId, Optional<String> gameId, long amount,
                                  OperationReason reason, OperationActor actor, String sourcePlugin) {
        byte[] recalculated = versioned(storedVersion, type, playerId, relatedPlayerId, gameId, amount, reason, actor, sourcePlugin);
        return MessageDigest.isEqual(Objects.requireNonNull(storedFingerprint, "storedFingerprint"), recalculated);
    }

    private static byte[] canonicalV1(OperationType type, UUID playerId, Optional<UUID> relatedPlayerId, long amount,
                                       OperationReason reason, OperationActor actor, String sourcePlugin) {
        Objects.requireNonNull(type, "type");
        requireUuid(playerId, "playerId");
        Objects.requireNonNull(relatedPlayerId, "relatedPlayerId");
        relatedPlayerId.ifPresent(related -> {
            requireUuid(related, "relatedPlayerId");
            if (related.equals(playerId)) throw new IllegalArgumentException("relatedPlayerId cannot equal playerId");
        });
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (sourcePlugin == null || sourcePlugin.isBlank()) {
            throw new IllegalArgumentException("sourcePlugin cannot be blank");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x50454F50); // PEOP
            out.writeShort(1);
            out.writeByte(typeToken(type));
            writeUuid(out, playerId);
            writeOptionalUuid(out, relatedPlayerId);
            out.writeLong(amount);
            writeString(out, reason.value());
            writeString(out, sourcePlugin);
            out.writeByte(actorToken(actor.type()));
            writeOptionalUuid(out, actor.playerId());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build operation fingerprint", exception);
        }
    }

    private static byte[] canonicalV2(OperationType type, UUID playerId, Optional<UUID> relatedPlayerId, Optional<String> gameId,
                                      long amount, OperationReason reason, OperationActor actor, String sourcePlugin) {
        Objects.requireNonNull(gameId, "gameId");
        byte[] legacy = canonicalV1(type, playerId, relatedPlayerId, amount, reason, actor, sourcePlugin);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(legacy.length + 80);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x50454F50); // PEOP
            out.writeShort(CURRENT_VERSION);
            out.write(legacy);
            writeOptionalString(out, gameId);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build operation fingerprint", exception);
        } finally {
            Arrays.fill(legacy, (byte) 0);
        }
    }

    private static byte[] sha256(byte[] canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        } finally {
            Arrays.fill(canonical, (byte) 0);
        }
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static void writeOptionalUuid(DataOutputStream out, Optional<UUID> uuid) throws IOException {
        if (uuid.isPresent()) {
            out.writeByte(1);
            writeUuid(out, uuid.get());
        } else {
            out.writeByte(0);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static void writeOptionalString(DataOutputStream out, Optional<String> value) throws IOException {
        if (value.isPresent()) {
            out.writeByte(1);
            writeString(out, value.get());
        } else {
            out.writeByte(0);
        }
    }

    private static int typeToken(OperationType type) {
        return switch (type) {
            case AWARD -> 1;
            case CREDIT -> 2;
            case DEBIT -> 3;
            case TRANSFER -> 4;
            case SET_BALANCE -> 5;
            case RESET_BALANCE -> 6;
        };
    }

    private static int actorToken(ActorType type) {
        return switch (type) {
            case SYSTEM -> 1;
            case PLUGIN -> 2;
            case PLAYER -> 3;
            case CONSOLE -> 4;
        };
    }

    private static UUID requireUuid(UUID uuid, String name) {
        Objects.requireNonNull(uuid, name);
        if (NIL.equals(uuid)) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
        return uuid;
    }
}
