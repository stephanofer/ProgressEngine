package com.stephanofer.progressengine.api.event;

/**
 * Origin of an observed balance change.
 */
public enum BalanceChangeOrigin {
    /** Change originated on this server after a local commit. */
    LOCAL,
    /** Change was observed after a remote invalidation or reconciliation. */
    REMOTE
}
