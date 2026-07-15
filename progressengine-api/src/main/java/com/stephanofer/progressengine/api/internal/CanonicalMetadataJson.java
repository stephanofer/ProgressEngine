package com.stephanofer.progressengine.api.internal;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON codec used to validate and later persist operation metadata.
 */
public final class CanonicalMetadataJson {
    private CanonicalMetadataJson() {
    }

    /**
     * Serializes metadata as a deterministic JSON object.
     *
     * @param values metadata values
     * @return canonical JSON
     */
    public static String encode(Map<String, String> values) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, entry.getKey());
            json.append(':');
            appendString(json, entry.getValue());
        }
        return json.append('}').toString();
    }

    /**
     * Returns the UTF-8 size of canonical metadata JSON.
     *
     * @param values metadata values
     * @return encoded UTF-8 byte size
     */
    public static int utf8Size(Map<String, String> values) {
        return encode(values).getBytes(StandardCharsets.UTF_8).length;
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u");
                        String hex = Integer.toHexString(character);
                        json.append("0000", 0, 4 - hex.length()).append(hex);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
