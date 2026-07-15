package com.stephanofer.progressengine.api.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OperationIdTest {
    @Test
    void generateCreatesDistinctNonNilIds() {
        OperationId first = OperationId.generate();
        OperationId second = OperationId.generate();

        assertNotNull(first.value());
        assertNotEquals(new UUID(0L, 0L), first.value());
        assertNotEquals(first, second);
    }

    @Test
    void wrapsAndParsesCanonicalUuid() {
        UUID uuid = UUID.randomUUID();

        OperationId id = OperationId.of(uuid);

        assertEquals(uuid, id.value());
        assertEquals(id, OperationId.parse(uuid.toString()));
        assertEquals(uuid.toString(), id.toString());
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(NullPointerException.class, () -> OperationId.of(null));
        assertThrows(IllegalArgumentException.class, () -> OperationId.of(new UUID(0L, 0L)));
        assertThrows(NullPointerException.class, () -> OperationId.parse(null));
        assertThrows(IllegalArgumentException.class, () -> OperationId.parse("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> OperationId.parse("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class, () -> OperationId.parse(UUID.randomUUID().toString().toUpperCase()));
    }
}
