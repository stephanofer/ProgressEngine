package com.stephanofer.progressengine.api.operation;

/**
 * Durable economic operation kinds supported by ProgressEngine.
 */
public enum OperationType {
    /** Gameplay award that may apply NetworkBoosters. */
    AWARD,
    /** Direct credit without boosters. */
    CREDIT,
    /** Direct debit. */
    DEBIT,
    /** Atomic transfer between two accounts. */
    TRANSFER,
    /** Administrative balance assignment. */
    SET_BALANCE,
    /** Administrative reset to zero. */
    RESET_BALANCE
}
