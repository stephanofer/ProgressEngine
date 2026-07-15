package com.stephanofer.progressengine.api.operation;

/**
 * Indicates whether a durable result was produced now or replayed from an earlier request.
 */
public enum ReplayStatus {
    /** The result belongs to the first successful resolution of the operation id. */
    ORIGINAL,
    /** The result was loaded from a prior resolution of the same operation id and fingerprint. */
    REPLAYED
}
