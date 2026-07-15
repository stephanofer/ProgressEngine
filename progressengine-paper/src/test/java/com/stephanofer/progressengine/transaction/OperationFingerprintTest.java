package com.stephanofer.progressengine.transaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.source.OperationActor;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OperationFingerprintTest {
    private final UUID playerId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final OperationReason reason = OperationReason.of("test:mutation");

    @Test
    void sameEconomicIntentionProducesSameFingerprint() {
        byte[] first = fingerprint(100L, "TestPlugin", OperationActor.plugin());
        byte[] second = fingerprint(100L, "TestPlugin", OperationActor.plugin());

        assertArrayEquals(first, second);
        assertTrue(OperationFingerprint.matches(first, OperationFingerprint.CURRENT_VERSION, OperationType.CREDIT,
            this.playerId, Optional.empty(), 100L, this.reason, OperationActor.plugin(), "TestPlugin"));
    }

    @Test
    void economicFieldsChangeFingerprint() {
        byte[] original = fingerprint(100L, "TestPlugin", OperationActor.plugin());

        assertFalse(Arrays.equals(original, fingerprint(101L, "TestPlugin", OperationActor.plugin())));
        assertFalse(Arrays.equals(original, fingerprint(100L, "OtherPlugin", OperationActor.plugin())));
        assertFalse(Arrays.equals(original, fingerprint(100L, "TestPlugin", OperationActor.player(UUID.randomUUID()))));
        assertFalse(Arrays.equals(original, OperationFingerprint.current(OperationType.DEBIT, this.playerId, Optional.empty(),
            100L, this.reason, OperationActor.plugin(), "TestPlugin")));
    }

    @Test
    void transferFingerprintIsDirectionalAndIncludesReceiver() {
        UUID receiver = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        UUID otherReceiver = UUID.fromString("323e4567-e89b-12d3-a456-426614174000");

        byte[] original = OperationFingerprint.current(OperationType.TRANSFER, this.playerId, Optional.of(receiver),
            100L, this.reason, OperationActor.plugin(), "TestPlugin");

        assertFalse(Arrays.equals(original, OperationFingerprint.current(OperationType.TRANSFER, receiver, Optional.of(this.playerId),
            100L, this.reason, OperationActor.plugin(), "TestPlugin")));
        assertFalse(Arrays.equals(original, OperationFingerprint.current(OperationType.TRANSFER, this.playerId, Optional.of(otherReceiver),
            100L, this.reason, OperationActor.plugin(), "TestPlugin")));
        assertTrue(OperationFingerprint.matches(original, OperationFingerprint.CURRENT_VERSION, OperationType.TRANSFER,
            this.playerId, Optional.of(receiver), 100L, this.reason, OperationActor.plugin(), "TestPlugin"));
    }

    @Test
    void unsupportedStoredVersionFailsClosed() {
        byte[] original = fingerprint(100L, "TestPlugin", OperationActor.plugin());

        assertThrows(IllegalArgumentException.class, () -> OperationFingerprint.matches(original, 99, OperationType.CREDIT,
            this.playerId, Optional.empty(), 100L, this.reason, OperationActor.plugin(), "TestPlugin"));
    }

    private byte[] fingerprint(long amount, String plugin, OperationActor actor) {
        return OperationFingerprint.current(OperationType.CREDIT, this.playerId, Optional.empty(), amount, this.reason, actor, plugin);
    }
}
