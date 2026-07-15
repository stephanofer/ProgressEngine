package com.stephanofer.progressengine.account;

final class AccountBalanceMath {
    private AccountBalanceMath() {
    }

    static AccountMutationDecision credit(long balanceBefore, long revisionBefore, long amount, long maximumBalance) {
        long balanceAfter;
        try {
            balanceAfter = Math.addExact(balanceBefore, amount);
        } catch (ArithmeticException exception) {
            return AccountMutationDecision.balanceLimitExceeded();
        }
        if (balanceAfter > maximumBalance) {
            return AccountMutationDecision.balanceLimitExceeded();
        }
        return AccountMutationDecision.success(balanceAfter, nextRevision(revisionBefore));
    }

    static AccountMutationDecision debit(long balanceBefore, long revisionBefore, long amount) {
        if (balanceBefore < amount) {
            return AccountMutationDecision.insufficientFunds();
        }
        return AccountMutationDecision.success(Math.subtractExact(balanceBefore, amount), nextRevision(revisionBefore));
    }

    static AccountMutationDecision set(long revisionBefore, long targetBalance, long maximumBalance) {
        if (targetBalance > maximumBalance) {
            return AccountMutationDecision.balanceLimitExceeded();
        }
        return AccountMutationDecision.success(targetBalance, nextRevision(revisionBefore));
    }

    static AccountMutationDecision reset(long revisionBefore) {
        return AccountMutationDecision.success(0L, nextRevision(revisionBefore));
    }

    private static long nextRevision(long revisionBefore) {
        return Math.addExact(revisionBefore, 1L);
    }
}
