package com.stephanofer.progressengine.transaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.persistence.OperationResultPayload;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceDataException;
import com.stephanofer.progressengine.persistence.StoredOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class OperationResultCodec {
    public static final int CURRENT_VERSION = 1;
    private static final Set<String> SUCCESS_FIELDS = Set.of("balance_before", "balance_after", "revision");

    private OperationResultCodec() {
    }

    public static OperationResultPayload successPayload(BalanceChange change) {
        Objects.requireNonNull(change, "change");
        String json = "{\"balance_before\":" + change.balanceBefore()
            + ",\"balance_after\":" + change.balanceAfter()
            + ",\"revision\":" + change.revision() + '}';
        return OperationResultPayload.of(CURRENT_VERSION, json);
    }

    public static OperationResultPayload rejectionPayload() {
        return OperationResultPayload.of(CURRENT_VERSION, "{}");
    }

    public static DecodedOperationResult decode(StoredOperation operation, ReplayStatus replayStatus) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(replayStatus, "replayStatus");
        if (operation.status() == OperationStatus.PENDING) {
            throw new PersistenceDataException("Cannot decode pending operation result " + operation.operationId());
        }
        int version = operation.resultPayload().version()
            .orElseThrow(() -> new PersistenceDataException("Completed operation is missing result version"));
        String json = operation.resultPayload().json()
            .orElseThrow(() -> new PersistenceDataException("Completed operation is missing result json"));
        if (version != CURRENT_VERSION) {
            throw new PersistenceDataException("Unsupported operation result version " + version);
        }
        if (operation.status() != OperationStatus.SUCCESS) {
            requireEmptyObject(json, "rejected operation result");
            return new DecodedOperationResult.Rejected(operation.operationId(), operation.status(), replayStatus);
        }
        JsonObject object = object(json, "success operation result");
        if (!object.keySet().equals(SUCCESS_FIELDS)) {
            throw new PersistenceDataException("Success result payload has unexpected fields: " + object.keySet());
        }
        long balanceBefore = longField(object, "balance_before");
        long balanceAfter = longField(object, "balance_after");
        long revision = longField(object, "revision");
        BalanceChange change = BalanceChange.single(
            operation.playerId(),
            Math.subtractExact(balanceAfter, balanceBefore),
            balanceBefore,
            balanceAfter,
            revision
        );
        OperationReceipt receipt = new OperationReceipt(
            operation.operationId(),
            operation.type(),
            operation.reason(),
            operation.actor(),
            operation.source(),
            metadata(operation.metadataJson()),
            List.of(change),
            operation.completedAt().orElseThrow(() -> new PersistenceDataException("Success operation is missing completedAt"))
        );
        return new DecodedOperationResult.Success(receipt, replayStatus);
    }

    private static OperationMetadata metadata(String json) {
        JsonObject object = object(json, "operation metadata");
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new PersistenceDataException("Operation metadata values must be strings");
            }
            values.put(entry.getKey(), element.getAsString());
        }
        return OperationMetadata.of(values);
    }

    private static void requireEmptyObject(String json, String label) {
        JsonObject object = object(json, label);
        if (!object.isEmpty()) {
            throw new PersistenceDataException(label + " payload must be an empty object");
        }
    }

    private static JsonObject object(String json, String label) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new PersistenceDataException(label + " payload must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new PersistenceDataException("Invalid " + label + " JSON", exception);
        }
    }

    private static long longField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new PersistenceDataException("Missing numeric field " + name);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new PersistenceDataException("Field " + name + " must be a number");
        }
        String raw = primitive.getAsString();
        if (!raw.matches("-?(0|[1-9]\\d*)")) {
            throw new PersistenceDataException("Field " + name + " must be an integer long");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new PersistenceDataException("Field " + name + " is outside the long range", exception);
        }
    }
}
