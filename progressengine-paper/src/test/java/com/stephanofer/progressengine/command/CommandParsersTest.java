package com.stephanofer.progressengine.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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
        byte[] different = generator.hash("abcd");

        assertEquals(32, first.length);
        assertTrue(java.util.Arrays.equals(first, second));
        assertFalse(java.util.Arrays.equals(first, different));
    }

    @Test
    void generatedTokensAreUrlSafeAndNotDeterministic() {
        CommandTokenGenerator generator = new CommandTokenGenerator();
        String first = generator.generate();
        String second = generator.generate();

        assertEquals(24, first.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(first, second);
    }

    @Test
    void pageRejectsZeroNegativeAndOverflowingValues() {
        assertEquals(1, CommandParsers.page("1"));
        assertEquals(1_000_000, CommandParsers.page("1000000"));

        assertThrows(IllegalArgumentException.class, () -> CommandParsers.page("0"));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.page("-1"));
        assertThrows(IllegalArgumentException.class, () -> CommandParsers.page("1000001"));
    }

    @Test
    void uuidParserRejectsInvalidAndNilUuid() {
        UUID playerId = UUID.fromString("11111111-1111-4111-8111-111111111111");

        assertEquals(playerId, CommandParsers.uuid(playerId.toString()).orElseThrow());
        assertTrue(CommandParsers.uuid("not-a-uuid").isEmpty());
        assertTrue(CommandParsers.uuid("00000000-0000-0000-0000-000000000000").isEmpty());
    }
}
