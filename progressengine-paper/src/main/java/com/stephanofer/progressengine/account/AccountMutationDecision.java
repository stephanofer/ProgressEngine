package com.stephanofer.progressengine.account;

import com.stephanofer.progressengine.persistence.OperationStatus;

record AccountMutationDecision(OperationStatus status, long balanceAfter, long revisionAfter) {
    AccountMutationDecision {
        if (status == null) throw new NullPointerException("status cannot be null");
        if (status == OperationStatus.PENDING || status == OperationStatus.NO_POINTS_AWARDED) {
            throw new IllegalArgumentException("status is not valid for an account mutation decision");
        }
        if (status == OperationStatus.SUCCESS) {
            if (balanceAfter < 0L) throw new IllegalArgumentException("balanceAfter cannot be negative");
            if (revisionAfter < 1L) throw new IllegalArgumentException("revisionAfter must be positive");
        } else if (balanceAfter != 0L || revisionAfter != 0L) {
            throw new IllegalArgumentException("rejected mutation decisions cannot include balance data");
        }
    }

    static AccountMutationDecision success(long balanceAfter, long revisionAfter) {
        return new AccountMutationDecision(OperationStatus.SUCCESS, balanceAfter, revisionAfter);
    }

    static AccountMutationDecision insufficientFunds() {
        return new AccountMutationDecision(OperationStatus.INSUFFICIENT_FUNDS, 0L, 0L);
    }

    static AccountMutationDecision balanceLimitExceeded() {
        return new AccountMutationDecision(OperationStatus.BALANCE_LIMIT_EXCEEDED, 0L, 0L);
    }
}
