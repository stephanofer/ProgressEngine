package com.stephanofer.progressengine.transaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.stephanofer.progressengine.api.result.AwardCalculation;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.BalanceChange;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.persistence.OperationResultPayload;
import com.stephanofer.progressengine.persistence.OperationStatus;
import com.stephanofer.progressengine.persistence.PersistenceDataException;
import com.stephanofer.progressengine.persistence.StoredOperation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class OperationResultCodec {
    public static final int CURRENT_VERSION = 1;
    private static final Set<String> SINGLE_SUCCESS_FIELDS = Set.of("balance_before", "balance_after", "revision");
    private static final Set<String> AWARD_CALCULATION_FIELDS = Set.of(
        "requested_base_amount",
        "prepared_base_amount",
        "multiplier",
        "calculated_amount",
        "final_amount",
        "applied_booster_ids",
        "capped",
        "boosters_evaluated"
    );
    private static final Set<String> AWARD_SUCCESS_FIELDS = union(SINGLE_SUCCESS_FIELDS, AWARD_CALCULATION_FIELDS);
    private static final Set<String> TRANSFER_SUCCESS_FIELDS = Set.of(
        "sender_balance_before",
        "sender_balance_after",
        "sender_revision",
        "receiver_balance_before",
        "receiver_balance_after",
        "receiver_revision"
    );

    private OperationResultCodec() {
    }

    public static OperationResultPayload successPayload(BalanceChange change) {
        Objects.requireNonNull(change, "change");
        if (change.relatedPlayerId().isPresent()) {
            throw new IllegalArgumentException("single-account success payload cannot include relatedPlayerId");
        }
        String json = "{\"balance_before\":" + change.balanceBefore()
            + ",\"balance_after\":" + change.balanceAfter()
            + ",\"revision\":" + change.revision() + '}';
        return OperationResultPayload.of(CURRENT_VERSION, json);
    }

    public static OperationResultPayload awardSuccessPayload(BalanceChange change, AwardCalculation calculation) {
        Objects.requireNonNull(change, "change");
        if (change.relatedPlayerId().isPresent()) {
            throw new IllegalArgumentException("award success payload cannot include relatedPlayerId");
        }
        String json = "{\"balance_before\":" + change.balanceBefore()
            + ",\"balance_after\":" + change.balanceAfter()
            + ",\"revision\":" + change.revision()
            + awardCalculationJsonSuffix(calculation) + '}';
        return OperationResultPayload.of(CURRENT_VERSION, json);
    }

    public static OperationResultPayload awardCalculationPayload(AwardCalculation calculation) {
        return OperationResultPayload.of(CURRENT_VERSION, '{' + awardCalculationJson(calculation) + '}');
    }

    public static OperationResultPayload transferSuccessPayload(BalanceChange sender, BalanceChange receiver) {
        validateTransferChanges(sender, receiver);
        String json = "{\"sender_balance_before\":" + sender.balanceBefore()
            + ",\"sender_balance_after\":" + sender.balanceAfter()
            + ",\"sender_revision\":" + sender.revision()
            + ",\"receiver_balance_before\":" + receiver.balanceBefore()
            + ",\"receiver_balance_after\":" + receiver.balanceAfter()
            + ",\"receiver_revision\":" + receiver.revision() + '}';
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
            if (operation.type() != OperationType.AWARD) {
                requireEmptyObject(json, "rejected operation result");
            }
            return new DecodedOperationResult.Rejected(operation.operationId(), operation.status(), replayStatus);
        }
        return operation.type() == OperationType.TRANSFER
            ? decodeTransferSuccess(operation, replayStatus, json)
            : decodeSingleSuccess(operation, replayStatus, json);
    }

    private static DecodedOperationResult decodeSingleSuccess(StoredOperation operation, ReplayStatus replayStatus, String json) {
        JsonObject object = object(json, "success operation result");
        Set<String> expectedFields = operation.type() == OperationType.AWARD ? AWARD_SUCCESS_FIELDS : SINGLE_SUCCESS_FIELDS;
        if (!object.keySet().equals(expectedFields)) {
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

    public static AwardCalculation awardCalculation(StoredOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (operation.type() != OperationType.AWARD) {
            throw new PersistenceDataException("Operation is not an award: " + operation.type());
        }
        int version = operation.resultPayload().version()
            .orElseThrow(() -> new PersistenceDataException("Award operation is missing result version"));
        String json = operation.resultPayload().json()
            .orElseThrow(() -> new PersistenceDataException("Award operation is missing result json"));
        if (version != CURRENT_VERSION) {
            throw new PersistenceDataException("Unsupported operation result version " + version);
        }
        JsonObject object = object(json, "award operation result");
        if (operation.status() == OperationStatus.SUCCESS) {
            if (!object.keySet().equals(AWARD_SUCCESS_FIELDS)) {
                throw new PersistenceDataException("Award success payload has unexpected fields: " + object.keySet());
            }
        } else if (!object.keySet().equals(AWARD_CALCULATION_FIELDS)) {
            throw new PersistenceDataException("Award calculation payload has unexpected fields: " + object.keySet());
        }
        return readAwardCalculation(object);
    }

    private static DecodedOperationResult decodeTransferSuccess(StoredOperation operation, ReplayStatus replayStatus, String json) {
        JsonObject object = object(json, "transfer success operation result");
        if (!object.keySet().equals(TRANSFER_SUCCESS_FIELDS)) {
            throw new PersistenceDataException("Transfer success result payload has unexpected fields: " + object.keySet());
        }
        java.util.UUID receiverId = operation.relatedPlayerId()
            .orElseThrow(() -> new PersistenceDataException("Transfer success is missing relatedPlayerId"));
        long amount = operation.requestedAmount();
        BalanceChange sender = BalanceChange.related(
            operation.playerId(),
            receiverId,
            Math.subtractExact(longField(object, "sender_balance_after"), longField(object, "sender_balance_before")),
            longField(object, "sender_balance_before"),
            longField(object, "sender_balance_after"),
            longField(object, "sender_revision")
        );
        BalanceChange receiver = BalanceChange.related(
            receiverId,
            operation.playerId(),
            Math.subtractExact(longField(object, "receiver_balance_after"), longField(object, "receiver_balance_before")),
            longField(object, "receiver_balance_before"),
            longField(object, "receiver_balance_after"),
            longField(object, "receiver_revision")
        );
        if (sender.delta() != -amount) {
            throw new PersistenceDataException("Transfer sender delta does not match requested amount");
        }
        if (receiver.delta() != amount) {
            throw new PersistenceDataException("Transfer receiver delta does not match requested amount");
        }
        OperationReceipt receipt = new OperationReceipt(
            operation.operationId(),
            operation.type(),
            operation.reason(),
            operation.actor(),
            operation.source(),
            metadata(operation.metadataJson()),
            List.of(sender, receiver),
            operation.completedAt().orElseThrow(() -> new PersistenceDataException("Success operation is missing completedAt"))
        );
        return new DecodedOperationResult.Success(receipt, replayStatus);
    }

    private static void validateTransferChanges(BalanceChange sender, BalanceChange receiver) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        if (sender.delta() >= 0L || receiver.delta() <= 0L) {
            throw new IllegalArgumentException("transfer payload requires sender debit and receiver credit");
        }
        if (Math.addExact(sender.delta(), receiver.delta()) != 0L) {
            throw new IllegalArgumentException("transfer payload must conserve balance");
        }
        if (sender.relatedPlayerId().isEmpty() || !sender.relatedPlayerId().get().equals(receiver.playerId())) {
            throw new IllegalArgumentException("sender change must reference receiver");
        }
        if (receiver.relatedPlayerId().isEmpty() || !receiver.relatedPlayerId().get().equals(sender.playerId())) {
            throw new IllegalArgumentException("receiver change must reference sender");
        }
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

    private static AwardCalculation readAwardCalculation(JsonObject object) {
        return new AwardCalculation(
            longField(object, "requested_base_amount"),
            longField(object, "prepared_base_amount"),
            decimalStringField(object, "multiplier"),
            decimalStringField(object, "calculated_amount"),
            longField(object, "final_amount"),
            stringArrayField(object, "applied_booster_ids"),
            booleanField(object, "capped"),
            booleanField(object, "boosters_evaluated")
        );
    }

    private static String awardCalculationJsonSuffix(AwardCalculation calculation) {
        return ',' + awardCalculationJson(calculation);
    }

    private static String awardCalculationJson(AwardCalculation calculation) {
        Objects.requireNonNull(calculation, "calculation");
        return "\"requested_base_amount\":" + calculation.requestedBaseAmount()
            + ",\"prepared_base_amount\":" + calculation.preparedBaseAmount()
            + ",\"multiplier\":\"" + decimal(calculation.multiplier()) + "\""
            + ",\"calculated_amount\":\"" + decimal(calculation.calculatedAmount()) + "\""
            + ",\"final_amount\":" + calculation.finalAmount()
            + ",\"applied_booster_ids\":" + stringArray(calculation.appliedBoosterIds())
            + ",\"capped\":" + calculation.capped()
            + ",\"boosters_evaluated\":" + calculation.boostersEvaluated();
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

    private static BigDecimal decimalStringField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new PersistenceDataException("Field " + name + " must be a decimal string");
        }
        try {
            return new BigDecimal(element.getAsString());
        } catch (NumberFormatException exception) {
            throw new PersistenceDataException("Field " + name + " is not a valid decimal", exception);
        }
    }

    private static boolean booleanField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new PersistenceDataException("Missing boolean field " + name);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw new PersistenceDataException("Field " + name + " must be boolean");
        }
        return primitive.getAsBoolean();
    }

    private static List<String> stringArrayField(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new PersistenceDataException("Field " + name + " must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        java.util.ArrayList<String> values = new java.util.ArrayList<>(array.size());
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new PersistenceDataException("Field " + name + " must contain only strings");
            }
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }

    private static String decimal(BigDecimal value) {
        return Objects.requireNonNull(value, "decimal").stripTrailingZeros().toPlainString();
    }

    private static String stringArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(values.get(index))).append('"');
        }
        return builder.append(']').toString();
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                builder.append('\\');
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.HashSet<String> values = new java.util.HashSet<>(first);
        values.addAll(second);
        return Set.copyOf(values);
    }
}
