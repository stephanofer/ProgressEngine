package com.stephanofer.progressengine.api.operation;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import com.stephanofer.progressengine.api.internal.CanonicalMetadataJson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bounded diagnostic key-value metadata attached to an operation.
 */
public record OperationMetadata(Map<String, String> values) {
    /** Maximum metadata entries. */
    public static final int MAX_ENTRIES = 16;
    /** Maximum metadata key length in characters. */
    public static final int MAX_KEY_LENGTH = 64;
    /** Maximum metadata value length in UTF-8 bytes. */
    public static final int MAX_VALUE_BYTES = 256;
    /** Maximum total metadata size in UTF-8 bytes. */
    public static final int MAX_TOTAL_BYTES = 2048;

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9._-]+");
    private static final OperationMetadata EMPTY = new OperationMetadata(Map.of());

    /**
     * Creates metadata from a map. The map is defensively copied.
     *
     * @param values metadata values
     */
    public OperationMetadata {
        if (values == null) {
            throw new NullPointerException("values cannot be null");
        }
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("values cannot contain more than " + MAX_ENTRIES + " entries");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) {
                throw new NullPointerException("metadata key cannot be null");
            }
            if (value == null) {
                throw new NullPointerException("metadata value cannot be null");
            }
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH || !KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("metadata key is invalid: " + key);
            }
            int valueBytes = ApiValidation.utf8Bytes(value);
            if (valueBytes > MAX_VALUE_BYTES) {
                throw new IllegalArgumentException("metadata value exceeds " + MAX_VALUE_BYTES + " UTF-8 bytes for key " + key);
            }
            copy.put(key, value);
        }
        if (CanonicalMetadataJson.utf8Size(copy) > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("metadata exceeds " + MAX_TOTAL_BYTES + " serialized UTF-8 bytes");
        }
        values = Map.copyOf(copy);
    }

    /**
     * Returns empty metadata.
     *
     * @return empty metadata
     */
    public static OperationMetadata empty() {
        return EMPTY;
    }

    /**
     * Creates metadata from a map.
     *
     * @param values metadata values
     * @return validated metadata
     */
    public static OperationMetadata of(Map<String, String> values) {
        return values.isEmpty() ? EMPTY : new OperationMetadata(values);
    }
}
