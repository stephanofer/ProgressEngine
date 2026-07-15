package com.stephanofer.progressengine.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stephanofer.progressengine.persistence.OperationStatus;
import org.junit.jupiter.api.Test;

final class AccountBalanceMathTest {
    @Test
    void creditUsesExactArithmeticAndMaximumBalance() {
        AccountMutationDecision success = AccountBalanceMath.credit(50L, 3L, 25L, 100L);

        assertEquals(OperationStatus.SUCCESS, success.status());
        assertEquals(75L, success.balanceAfter());
        assertEquals(4L, success.revisionAfter());
        assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, AccountBalanceMath.credit(90L, 1L, 11L, 100L).status());
        assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, AccountBalanceMath.credit(Long.MAX_VALUE, 1L, 1L, Long.MAX_VALUE).status());
    }

    @Test
    void debitDecidesOnlyFromLockedBalance() {
        AccountMutationDecision exact = AccountBalanceMath.debit(100L, 9L, 100L);

        assertEquals(OperationStatus.SUCCESS, exact.status());
        assertEquals(0L, exact.balanceAfter());
        assertEquals(10L, exact.revisionAfter());
        assertEquals(OperationStatus.INSUFFICIENT_FUNDS, AccountBalanceMath.debit(99L, 1L, 100L).status());
    }

    @Test
    void setAndResetAllowNoOpAuditedRevision() {
        AccountMutationDecision set = AccountBalanceMath.set(4L, 0L, 100L);
        AccountMutationDecision reset = AccountBalanceMath.reset(5L);

        assertEquals(OperationStatus.SUCCESS, set.status());
        assertEquals(0L, set.balanceAfter());
        assertEquals(5L, set.revisionAfter());
        assertEquals(OperationStatus.SUCCESS, reset.status());
        assertEquals(0L, reset.balanceAfter());
        assertEquals(6L, reset.revisionAfter());
        assertEquals(OperationStatus.BALANCE_LIMIT_EXCEEDED, AccountBalanceMath.set(1L, 101L, 100L).status());
    }

    @Test
    void revisionOverflowFailsExceptionally() {
        assertThrows(ArithmeticException.class, () -> AccountBalanceMath.reset(Long.MAX_VALUE));
    }
}
