package com.stephanofer.progressengine.synchronization;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.stephanofer.progressengine.api.operation.OperationId;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RedisMessageCodec {
    private static final int VERSION = 1;
    private static final int MAX_PAYLOAD_LENGTH = 2_048;
    private static final Pattern COMPONENT = Pattern.compile("[a-zA-Z0-9._:-]+");

    public String encode(BalanceInvalidationMessage message) {
        Objects.requireNonNull(message, "message");
        return "{\"version\":1"
            + ",\"playerId\":\"" + message.playerId() + '"'
            + ",\"revision\":" + message.revision()
            + ",\"operationId\":\"" + message.operationId() + '"'
            + ",\"sourceServerId\":\"" + message.sourceServerId() + '"'
            + '}';
    }

    public String encode(TransferNoticeMessage message) {
        Objects.requireNonNull(message, "message");
        return "{\"version\":1"
            + ",\"operationId\":\"" + message.operationId() + '"'
            + ",\"senderId\":\"" + message.senderId() + '"'
            + ",\"receiverId\":\"" + message.receiverId() + '"'
            + ",\"amount\":" + message.amount()
            + ",\"receiverRevision\":" + message.receiverRevision()
            + ",\"sourceServerId\":\"" + message.sourceServerId() + '"'
            + '}';
    }

    public BalanceInvalidationMessage decodeInvalidation(String payload) {
        ParsedObject object = parse(payload);
        object.requireFields("version", "playerId", "revision", "operationId", "sourceServerId");
        requireVersion(object);
        return new BalanceInvalidationMessage(
            parseUuid(object.string("playerId"), "playerId"),
            object.positiveLong("revision"),
            OperationId.parse(object.string("operationId")),
            requireComponent(object.string("sourceServerId"), "sourceServerId")
        );
    }

    public TransferNoticeMessage decodeTransferNotice(String payload) {
        ParsedObject object = parse(payload);
        object.requireFields("version", "operationId", "senderId", "receiverId", "amount", "receiverRevision", "sourceServerId");
        requireVersion(object);
        return new TransferNoticeMessage(
            OperationId.parse(object.string("operationId")),
            parseUuid(object.string("senderId"), "senderId"),
            parseUuid(object.string("receiverId"), "receiverId"),
            object.positiveLong("amount"),
            object.positiveLong("receiverRevision"),
            requireComponent(object.string("sourceServerId"), "sourceServerId")
        );
    }

    static String requireComponent(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (trimmed.length() > 256 || !COMPONENT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(name + " must match [a-zA-Z0-9._:-]+ and be at most 256 characters");
        }
        return trimmed;
    }

    private static void requireVersion(ParsedObject object) {
        long version = object.positiveLong("version");
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported Redis message version " + version);
        }
    }

    private static UUID parseUuid(String value, String name) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException(name + " must be canonical UUID");
            }
            if (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L) {
                throw new IllegalArgumentException(name + " cannot be nil UUID");
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be canonical UUID", exception);
        }
    }

    private static ParsedObject parse(String payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("payload is too large");
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(payload));
            reader.setLenient(false);
            ParsedObject object = new ParsedObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!object.names.add(name)) {
                    throw new IllegalArgumentException("duplicate Redis message field: " + name);
                }
                JsonToken token = reader.peek();
                if (token == JsonToken.STRING) {
                    object.put(name, reader.nextString(), FieldType.STRING);
                } else if (token == JsonToken.NUMBER) {
                    object.put(name, Long.toString(reader.nextLong()), FieldType.NUMBER);
                } else {
                    throw new IllegalArgumentException("unsupported value for Redis message field: " + name);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("payload has trailing content");
            }
            return object;
        } catch (IOException exception) {
            throw new IllegalArgumentException("payload is not valid JSON", exception);
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("payload is not valid Redis message JSON", exception);
        }
    }

    private static final class ParsedObject {
        private final Set<String> names = new HashSet<>();
        private final java.util.Map<String, String> values = new java.util.HashMap<>();
        private final java.util.Map<String, FieldType> types = new java.util.HashMap<>();

        void put(String name, String value, FieldType type) {
            this.values.put(name, value);
            this.types.put(name, type);
        }

        void requireFields(String... expected) {
            Set<String> allowed = Set.of(expected);
            if (!this.names.equals(allowed)) {
                Set<String> missing = new HashSet<>(allowed);
                missing.removeAll(this.names);
                Set<String> unknown = new HashSet<>(this.names);
                unknown.removeAll(allowed);
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException("missing Redis message fields: " + missing);
                }
                throw new IllegalArgumentException("unknown Redis message fields: " + unknown);
            }
        }

        String string(String name) {
            if (this.types.get(name) != FieldType.STRING) {
                throw new IllegalArgumentException(name + " must be a string");
            }
            String value = this.values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " cannot be blank");
            }
            return value;
        }

        long positiveLong(String name) {
            if (this.types.get(name) != FieldType.NUMBER) {
                throw new IllegalArgumentException(name + " must be a number");
            }
            String value = this.values.get(name);
            try {
                long parsed = Long.parseLong(value);
                if (parsed < 1L) {
                    throw new IllegalArgumentException(name + " must be positive");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " must be a long", exception);
            }
        }
    }

    private enum FieldType {
        STRING,
        NUMBER
    }
}
