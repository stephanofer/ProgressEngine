package com.stephanofer.progressengine.api.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class OperationReasonTest {
    @Test
    void acceptsStableNamespacedReasons() {
        assertEquals("skywars:kill", OperationReason.of("skywars:kill").toString());
        assertEquals("progressengine:admin/reset", OperationReason.of("progressengine:admin/reset").value());
        assertEquals("hera_shop:special_item", OperationReason.of("hera_shop:special_item").value());
    }

    @Test
    void rejectsInvalidReasons() {
        assertThrows(NullPointerException.class, () -> OperationReason.of(null));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of("SkyWars:kill"));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of("skywars"));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of("skywars:"));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of(":"));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of("skywars:kill now"));
        assertThrows(IllegalArgumentException.class, () -> OperationReason.of("a:" + "b".repeat(OperationReason.MAX_LENGTH)));
    }
}
