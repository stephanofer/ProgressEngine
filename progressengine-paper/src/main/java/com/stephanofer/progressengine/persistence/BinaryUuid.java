package com.stephanofer.progressengine.persistence;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public final class BinaryUuid {
    private static final UUID NIL = new UUID(0L, 0L);
    private static final int BYTE_LENGTH = 16;

    private BinaryUuid() {
    }

    public static byte[] encode(UUID uuid) {
        UUID value = requireValid(uuid, "uuid");
        ByteBuffer buffer = ByteBuffer.allocate(BYTE_LENGTH);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != BYTE_LENGTH) {
            throw new PersistenceDataException("Expected 16 UUID bytes but received " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        return requireValid(uuid, "uuid");
    }

    public static UUID requireValid(UUID uuid, String name) {
        Objects.requireNonNull(uuid, name);
        if (NIL.equals(uuid)) {
            throw new IllegalArgumentException(name + " cannot be nil");
        }
        return uuid;
    }
}
