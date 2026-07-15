package com.stephanofer.progressengine.api.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OperationMetadataTest {
    @Test
    void copiesValuesDefensively() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("product_id", "legendary_sword");

        OperationMetadata metadata = OperationMetadata.of(source);
        source.put("product_id", "changed");

        assertEquals("legendary_sword", metadata.values().get("product_id"));
        assertThrows(UnsupportedOperationException.class, () -> metadata.values().put("other", "value"));
    }

    @Test
    void rejectsExcessiveOrInvalidMetadata() {
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= OperationMetadata.MAX_ENTRIES; index++) {
            tooMany.put("key" + index, "value");
        }

        assertThrows(IllegalArgumentException.class, () -> OperationMetadata.of(tooMany));
        assertThrows(IllegalArgumentException.class, () -> OperationMetadata.of(Map.of("Invalid", "value")));
        assertThrows(IllegalArgumentException.class, () -> OperationMetadata.of(Map.of("key", "x".repeat(OperationMetadata.MAX_VALUE_BYTES + 1))));
        assertThrows(NullPointerException.class, () -> new OperationMetadata(null));
    }

    @Test
    void rejectsMetadataThatOnlyExceedsTheSerializedJsonLimit() {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < 4; index++) {
            values.put("key" + index, "\"".repeat(OperationMetadata.MAX_VALUE_BYTES));
        }

        assertThrows(IllegalArgumentException.class, () -> OperationMetadata.of(values));
    }

    @Test
    void emptyMetadataIsShared() {
        assertEquals(OperationMetadata.empty(), OperationMetadata.of(Map.of()));
    }
}
