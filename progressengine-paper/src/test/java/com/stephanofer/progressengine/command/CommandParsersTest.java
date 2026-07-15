package com.stephanofer.progressengine.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CommandParsersTest {
    @Test
    void positiveAmountRejectsAmbiguousForms() {
        assertEquals(25L, CommandParsers.positiveAmount("25", 100L));

        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("0", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("-1", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("+1", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("1.0", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("1e3", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("101", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.positiveAmount("9223372036854775808", Long.MAX_VALUE));
    }

    @Test
    void setAmountAcceptsZeroButNotOverflow() {
        assertEquals(0L, CommandParsers.nonNegativeAmount("0", 100L));
        assertEquals(100L, CommandParsers.nonNegativeAmount("100", 100L));

        assertThrows(IllegalArgumentException.class, () -> CommandParsers.nonNegativeAmount("-0", 100L));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.nonNegativeAmount("101", 100L));
    }

    @Test
    void tokenHashIsStableAndSha256Sized() {
        CommandTokenGenerator generator = new CommandTokenGenerator();
        byte[] first = generator.hash("abc");
        byte[] second = generator.hash("abc");

        assertEquals(32, first.length);
        assertTrue(java.util.Arrays.equals(first, second));
    }
}
